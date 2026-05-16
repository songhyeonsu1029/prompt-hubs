package com.mediaproject.prompthubs.integration.notion;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Helpers to construct Notion children blocks (paragraph / heading / code).
 *
 * Used to push long-form content — prompts, assistant turn rendering — into a Notion
 * page's body instead of cramming it into properties (where Notion would render it as
 * a single wrinkled cell). The Markdown converter handles the most common shapes the
 * Claude Code Stop hook produces: heading, paragraph, fenced code block.
 */
@Component
public class NotionBlockBuilder {

    /** Notion's per-text-segment limit. Long content is chunked at this boundary. */
    private static final int RICH_TEXT_CHAR_LIMIT = 1900;

    /** Notion accepts up to 100 children per page-create call. */
    private static final int MAX_BLOCKS_PER_PAGE = 100;

    private static final Pattern FENCE = Pattern.compile("^```(\\w*)\\s*$");
    private static final Pattern NUMBERED_LIST = Pattern.compile("^(\\d+)\\.\\s+(.+)$");
    private static final Pattern INLINE = Pattern.compile("(\\*\\*[^*\\n]+\\*\\*|`[^`\\n]+`)");

    private final ObjectMapper mapper;

    public NotionBlockBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ArrayNode emptyBlockList() {
        return mapper.createArrayNode();
    }

    /**
     * Converts a Markdown-ish string into Notion blocks. Recognises:
     *   - fenced code blocks (```lang ... ```)
     *   - heading_2 / heading_3 (## , ###)
     *   - paragraph (everything else, blank-line-separated)
     */
    public ArrayNode markdownToBlocks(String markdown) {
        ArrayNode blocks = mapper.createArrayNode();
        if (markdown == null || markdown.isBlank()) return blocks;

        String[] lines = markdown.split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = null;
        String codeLang = null;

        for (String line : lines) {
            if (code != null) {
                if (FENCE.matcher(line.trim()).matches()) {
                    appendCodeBlock(blocks, code.toString(), codeLang);
                    code = null;
                    codeLang = null;
                } else {
                    if (code.length() > 0) code.append('\n');
                    code.append(line);
                }
                continue;
            }

            var fence = FENCE.matcher(line.trim());
            if (fence.matches()) {
                flushParagraph(blocks, paragraph);
                code = new StringBuilder();
                codeLang = fence.group(1);
                if (codeLang == null || codeLang.isEmpty()) codeLang = "plain text";
                continue;
            }

            if (line.startsWith("## ")) {
                flushParagraph(blocks, paragraph);
                appendHeading(blocks, "heading_2", line.substring(3));
            } else if (line.startsWith("### ")) {
                flushParagraph(blocks, paragraph);
                appendHeading(blocks, "heading_3", line.substring(4));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flushParagraph(blocks, paragraph);
                appendListItem(blocks, "bulleted_list_item", line.substring(2));
            } else {
                var num = NUMBERED_LIST.matcher(line);
                if (num.matches()) {
                    flushParagraph(blocks, paragraph);
                    appendListItem(blocks, "numbered_list_item", num.group(2));
                } else if (line.isBlank()) {
                    flushParagraph(blocks, paragraph);
                } else {
                    if (paragraph.length() > 0) paragraph.append('\n');
                    paragraph.append(line);
                }
            }
        }
        flushParagraph(blocks, paragraph);
        if (code != null) {
            // Unterminated fence — preserve content as a code block anyway
            appendCodeBlock(blocks, code.toString(), codeLang == null ? "plain text" : codeLang);
        }

        return capBlockCount(blocks);
    }

    /**
     * Builds a heading_2 block as a standalone helper for callers that compose blocks
     * manually.
     */
    public ObjectNode heading2(String text) {
        ObjectNode block = baseBlock("heading_2");
        block.putObject("heading_2").set("rich_text", richTextArray(text == null ? "" : text));
        return block;
    }

    public ObjectNode paragraph(String text) {
        ObjectNode block = baseBlock("paragraph");
        block.putObject("paragraph").set("rich_text", richTextArray(text == null ? "" : text));
        return block;
    }

    /**
     * Concatenates two block lists. Use to assemble per-section block streams (prompt
     * heading + body, then result heading + body).
     */
    public ArrayNode concat(ArrayNode... lists) {
        ArrayNode out = mapper.createArrayNode();
        for (ArrayNode list : lists) {
            if (list == null) continue;
            for (var node : list) out.add(node);
        }
        return capBlockCount(out);
    }

    // ---------- internals ----------

    private void flushParagraph(ArrayNode blocks, StringBuilder buf) {
        if (buf.length() == 0) return;
        for (String chunk : chunk(buf.toString(), RICH_TEXT_CHAR_LIMIT)) {
            blocks.add(paragraph(chunk));
        }
        buf.setLength(0);
    }

    private void appendHeading(ArrayNode blocks, String type, String text) {
        // Notion heading rich_text segment also limited to 2000 chars; truncate aggressively.
        ObjectNode block = baseBlock(type);
        block.putObject(type).set("rich_text", richTextArray(truncate(text, RICH_TEXT_CHAR_LIMIT)));
        blocks.add(block);
    }

    private void appendListItem(ArrayNode blocks, String type, String text) {
        ObjectNode block = baseBlock(type);
        block.putObject(type).set("rich_text", richTextArray(truncate(text, RICH_TEXT_CHAR_LIMIT)));
        blocks.add(block);
    }

    private void appendCodeBlock(ArrayNode blocks, String content, String language) {
        for (String chunk : chunk(content, RICH_TEXT_CHAR_LIMIT)) {
            ObjectNode block = baseBlock("code");
            ObjectNode code = block.putObject("code");
            code.set("rich_text", richTextArray(chunk));
            code.put("language", normaliseLanguage(language));
            blocks.add(block);
        }
    }

    private ArrayNode richTextArray(String text) {
        ArrayNode arr = mapper.createArrayNode();
        if (text == null || text.isEmpty()) {
            ObjectNode el = arr.addObject();
            el.put("type", "text");
            el.putObject("text").put("content", "");
            return arr;
        }
        var m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                addSegment(arr, text.substring(last, m.start()), false, false);
            }
            String tok = m.group();
            if (tok.startsWith("**")) {
                addSegment(arr, tok.substring(2, tok.length() - 2), true, false);
            } else {
                addSegment(arr, tok.substring(1, tok.length() - 1), false, true);
            }
            last = m.end();
        }
        if (last < text.length()) {
            addSegment(arr, text.substring(last), false, false);
        }
        if (arr.size() == 0) {
            // All matches consumed the entire string with empty trailing — fall back.
            addSegment(arr, "", false, false);
        }
        return arr;
    }

    private void addSegment(ArrayNode arr, String content, boolean bold, boolean code) {
        if (content == null || content.isEmpty()) return;
        ObjectNode el = arr.addObject();
        el.put("type", "text");
        el.putObject("text").put("content", content);
        if (bold || code) {
            ObjectNode ann = el.putObject("annotations");
            if (bold) ann.put("bold", true);
            if (code) ann.put("code", true);
        }
    }

    private ObjectNode baseBlock(String type) {
        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", type);
        return block;
    }

    private ArrayNode capBlockCount(ArrayNode blocks) {
        if (blocks.size() <= MAX_BLOCKS_PER_PAGE) return blocks;
        ArrayNode capped = mapper.createArrayNode();
        for (int i = 0; i < MAX_BLOCKS_PER_PAGE - 1; i++) capped.add(blocks.get(i));
        ObjectNode marker = paragraph("…[truncated " + (blocks.size() - (MAX_BLOCKS_PER_PAGE - 1)) + " more blocks]");
        capped.add(marker);
        return capped;
    }

    private static List<String> chunk(String text, int max) {
        if (text.length() <= max) return List.of(text);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += max) {
            out.add(text.substring(i, Math.min(i + max, text.length())));
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Notion accepts a fixed enum for code block language; map common aliases. */
    private static String normaliseLanguage(String s) {
        if (s == null || s.isBlank()) return "plain text";
        return switch (s.toLowerCase()) {
            case "sh", "shell", "zsh" -> "bash";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "yml" -> "yaml";
            case "md" -> "markdown";
            case "tf", "hcl" -> "hcl";
            case "json", "bash", "javascript", "typescript", "python", "java", "go",
                 "rust", "ruby", "kotlin", "swift", "html", "css", "sql", "yaml",
                 "markdown", "plain text" -> s.toLowerCase();
            default -> "plain text";
        };
    }
}
