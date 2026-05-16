package com.mediaproject.prompthubs.integration.mcp;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Centralised MCP tool metadata. Descriptions are tuned so the model knows
 * <em>when</em> to call each one without being told explicitly by the user.
 */
@Component
public class McpToolCatalog {

    private final ObjectMapper mapper;
    private final ArrayNode tools;

    public McpToolCatalog(ObjectMapper mapper) {
        this.mapper = mapper;
        this.tools = build(mapper);
    }

    public ArrayNode toolsList() {
        return tools.deepCopy();
    }

    private static ArrayNode build(ObjectMapper m) {
        ArrayNode arr = m.createArrayNode();
        arr.add(saveLog(m));
        arr.add(searchPrompts(m));
        arr.add(getPrompt(m));
        arr.add(listDocs(m));
        arr.add(getDoc(m));
        return arr;
    }

    private static ObjectNode saveLog(ObjectMapper m) {
        ObjectNode t = m.createObjectNode();
        t.put("name", "save_log");
        t.put("description",
                "Archive the current prompt + assistant result as a PromptLog in the connected PromptHubs workspace. " +
                "Call this when a meaningful exchange just finished — e.g. a code task completed, a question answered, " +
                "a multi-step tool sequence wrapped up. Pass `idempotencyKey` (or use auto-save hooks that supply one) " +
                "so retries and double-firings collapse into a single row.");

        ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        prop(props, "promptText", "string", "The full user prompt text that started this turn.");
        prop(props, "modelUsed", "string", "Model identifier, e.g. 'claude-opus-4-7' or 'claude-sonnet-4-6'.");
        enumProp(props, "status",
                "Outcome of the exchange. SUCCESS = clean finish. PARTIAL = some tool calls errored but overall result is usable. " +
                "FAILURE = the assistant ended in an error. NEEDS_REVIEW = ambiguous / user should triage later.",
                "SUCCESS", "PARTIAL", "FAILURE", "NEEDS_REVIEW");
        prop(props, "resultSummary", "string", "One-line summary of the assistant output (optional).");
        prop(props, "resultBody", "string",
                "Full assistant turn rendered as Markdown — text, code blocks, tool calls and their outputs (optional).");
        ObjectNode tags = props.putObject("tags");
        tags.put("type", "array");
        tags.putObject("items").put("type", "string");
        tags.put("description", "Free-form tags such as 'bugfix', 'migration' (optional).");
        prop(props, "sessionId", "string", "UUID grouping turns of one conversation (optional).");
        prop(props, "idempotencyKey", "string",
                "Caller-supplied dedup key (max 64 chars). Same key → single row even on retries (optional).");
        ObjectNode metadata = props.putObject("metadata");
        metadata.put("type", "object");
        metadata.put("description", "Optional structured details (tool calls, model parameters, cwd, etc.).");
        ArrayNode required = schema.putArray("required");
        required.add("promptText");
        required.add("modelUsed");
        required.add("status");
        t.set("inputSchema", schema);
        return t;
    }

    private static ObjectNode searchPrompts(ObjectMapper m) {
        ObjectNode t = m.createObjectNode();
        t.put("name", "search_prompts");
        t.put("description",
                "Search this workspace's APPROVED prompts (vetted by the team) by keyword and/or category. " +
                "Use this BEFORE drafting a new prompt or designing a workflow — chances are someone already " +
                "wrote a reusable prompt for it. Returns up to 10 most recently updated entries (title, " +
                "category, status). For full body + linked docs, follow up with `get_prompt`.");
        ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        prop(props, "keyword", "string", "Free-text term matched against title, body, and tags.");
        prop(props, "category", "string", "Restrict to one category (e.g. 'migration', 'code-gen').");
        t.set("inputSchema", schema);
        return t;
    }

    private static ObjectNode getPrompt(ObjectMapper m) {
        ObjectNode t = m.createObjectNode();
        t.put("name", "get_prompt");
        t.put("description",
                "Fetch one prompt's full body and current version metadata by UUID. " +
                "Returns title, prompt text, success criteria, validation method, and most importantly " +
                "`linkedDocs` — IDs and titles of workspace docs (coding conventions, API specs, etc.) the " +
                "team has attached to this prompt. When the user asks you to actually USE the prompt " +
                "(e.g. \"implement login based on the auth prompt\"), follow up with `get_doc` on each " +
                "linkedDocs entry so your output respects team conventions.");
        ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        prop(props, "id", "string", "Prompt UUID returned from search_prompts.");
        schema.putArray("required").add("id");
        t.set("inputSchema", schema);
        return t;
    }

    private static ObjectNode listDocs(ObjectMapper m) {
        ObjectNode t = m.createObjectNode();
        t.put("name", "list_docs");
        t.put("description",
                "List up to 50 most recently updated workspace docs (coding conventions, API specs, runbooks, " +
                "domain glossaries). Returns id + title + category only — use `get_doc` for full body. " +
                "Useful when the user hasn't named a specific prompt and you want to scan what context exists.");
        ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        prop(props, "category", "string", "Restrict to one category (optional).");
        t.set("inputSchema", schema);
        return t;
    }

    private static ObjectNode getDoc(ObjectMapper m) {
        ObjectNode t = m.createObjectNode();
        t.put("name", "get_doc");
        t.put("description",
                "Fetch one workspace doc's full Markdown body by UUID. Use this whenever a prompt's " +
                "`linkedDocs` points at a coding convention / API spec / style guide and the user has asked " +
                "you to write or modify code that should follow it. Treat the returned content as binding " +
                "team-wide context for this turn.");
        ObjectNode schema = m.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        prop(props, "id", "string", "Doc UUID (from get_prompt.linkedDocs[*].id or list_docs).");
        schema.putArray("required").add("id");
        t.set("inputSchema", schema);
        return t;
    }

    private static void prop(ObjectNode props, String name, String type, String description) {
        ObjectNode p = props.putObject(name);
        p.put("type", type);
        p.put("description", description);
    }

    private static void enumProp(ObjectNode props, String name, String description, String... values) {
        ObjectNode p = props.putObject(name);
        p.put("type", "string");
        p.put("description", description);
        ArrayNode arr = p.putArray("enum");
        for (String v : values) arr.add(v);
    }
}
