#!/usr/bin/env node
/**
 * Claude Code Stop hook: archive each turn into PromptHubs.
 *
 *   ~/.claude/settings.json
 *   {
 *     "hooks": {
 *       "Stop": [{
 *         "type": "command",
 *         "command": "node /absolute/path/to/auto-save-hook.js",
 *         "timeout": 30
 *       }]
 *     }
 *   }
 *
 * Required env vars (set in the same settings.json or shell):
 *   PROMPTHUBS_API_KEY    — workspace API key (ph_…)
 *   PROMPTHUBS_BASE_URL   — http(s)://…   default http://localhost:8080
 *
 * The hook is intentionally silent on success and only writes warnings to stderr.
 * A non-zero exit would surface to the user but not block Claude; we exit 0 even
 * on failure so a backend outage never breaks Claude Code.
 */

import fs from "node:fs";
import crypto from "node:crypto";

const BASE_URL = (process.env.PROMPTHUBS_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const API_KEY = process.env.PROMPTHUBS_API_KEY;

// File-extension → Notion/Markdown language hint. Declared at the top of the module
// so the synchronous IIFE below can reach it without hitting TDZ
// (`renderToolUse` → `guessLang` runs synchronously before the rest of the module
// top-level has been evaluated).
const LANG_BY_EXT = {
  js: "javascript", mjs: "javascript", cjs: "javascript", jsx: "javascript",
  ts: "typescript", tsx: "typescript",
  py: "python", rb: "ruby", go: "go", rs: "rust",
  java: "java", kt: "kotlin", swift: "swift",
  c: "c", h: "c", cpp: "cpp", hpp: "cpp", cc: "cpp",
  cs: "csharp",
  sh: "bash", bash: "bash", zsh: "bash", fish: "bash",
  sql: "sql", html: "html", css: "css", scss: "scss",
  json: "json", yaml: "yaml", yml: "yaml", toml: "toml",
  xml: "xml", md: "markdown", markdown: "markdown",
  tf: "hcl", hcl: "hcl",
  dockerfile: "dockerfile",
};

function guessLang(path) {
  if (!path) return "";
  const fname = path.split("/").pop() || "";
  if (/^dockerfile/i.test(fname)) return "dockerfile";
  const ext = fname.includes(".") ? fname.split(".").pop().toLowerCase() : "";
  return LANG_BY_EXT[ext] || "";
}

(async () => {
  try {
    if (!API_KEY) bail("PROMPTHUBS_API_KEY env var not set");

    const payload = JSON.parse(fs.readFileSync(0, "utf-8"));
    if (payload.hook_event_name && payload.hook_event_name !== "Stop") return;

    const transcriptPath = payload.transcript_path;
    if (!transcriptPath || !fs.existsSync(transcriptPath)) {
      bail(`transcript not found: ${transcriptPath}`);
    }

    const turn = extractLastTurn(transcriptPath);
    if (!turn) return; // nothing to save (empty turn / synthetic only)

    const status = turn.assistantHadError
      ? "FAILURE"
      : turn.hadToolError
      ? "PARTIAL"
      : "SUCCESS";

    const body = {
      promptText: truncate(turn.userPrompt, 100_000),
      modelUsed: turn.model || "claude-code",
      status,
      resultSummary: deriveSummary(turn.assistantText),
      resultBody: turn.resultBody,
      tags: ["claude-code", "auto"],
      sessionId: payload.session_id || turn.sessionId,
      idempotencyKey: turn.idempotencyKey,
      metadata: {
        cwd: payload.cwd,
        toolCalls: turn.toolCalls,
        turnUuid: turn.lastAssistantUuid,
      },
    };

    const res = await fetch(`${BASE_URL}/api/v1/external/mcp/logs`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": API_KEY,
      },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const txt = await res.text();
      warn(`PromptHubs save_log failed: ${res.status} ${res.statusText} — ${txt.slice(0, 300)}`);
    }
  } catch (err) {
    warn(`hook error: ${err?.stack || err}`);
  }
})();

function extractLastTurn(path) {
  // Read JSONL into structured records, ignoring lines that fail to parse.
  const records = [];
  for (const line of fs.readFileSync(path, "utf-8").split("\n")) {
    if (!line.trim()) continue;
    try {
      records.push(JSON.parse(line));
    } catch {
      /* skip malformed line */
    }
  }
  if (records.length === 0) return null;

  // The "last user prompt" is the most recent record where type=user AND content is a
  // plain string. (Records of type=user with array content are tool_result echoes
  // injected by Claude Code — not user input.)
  let lastUserIdx = -1;
  for (let i = records.length - 1; i >= 0; i--) {
    const r = records[i];
    if (r.type !== "user") continue;
    const c = r.message?.content;
    if (typeof c === "string" && c.trim().length > 0) {
      lastUserIdx = i;
      break;
    }
  }
  if (lastUserIdx === -1) return null;

  const userRecord = records[lastUserIdx];
  const tail = records.slice(lastUserIdx + 1);

  const renderedParts = [];
  const toolCalls = [];
  let lastAssistantUuid = null;
  let model = null;
  let assistantHadError = false;
  let hadToolError = false;
  let assistantText = "";

  // Gather all assistant text + tool_use + tool_result blocks for this turn,
  // rendered as a Markdown document.
  for (const r of tail) {
    if (r.type === "assistant") {
      lastAssistantUuid = r.uuid || lastAssistantUuid;
      model = r.message?.model || model;
      if (r.error || r.isApiErrorMessage) assistantHadError = true;
      for (const block of r.message?.content ?? []) {
        if (block.type === "text") {
          renderedParts.push(block.text);
          assistantText += (assistantText ? "\n\n" : "") + block.text;
        } else if (block.type === "tool_use") {
          toolCalls.push({ id: block.id, name: block.name });
          renderedParts.push(renderToolUse(block));
        }
        // thinking blocks are intentionally not archived
      }
    } else if (r.type === "user") {
      const c = r.message?.content;
      if (Array.isArray(c)) {
        for (const block of c) {
          if (block?.type === "tool_result") {
            if (block.is_error) hadToolError = true;
            renderedParts.push(renderToolResult(block));
          }
        }
      }
    }
  }

  // Idempotency key: the assistant uuid that closed the turn is unique per turn.
  // Falling back to a hash of the rendered body so reruns of the same content collapse.
  const idempotencyKey = lastAssistantUuid
    ? `t-${lastAssistantUuid}`.slice(0, 64)
    : "h-" + crypto.createHash("sha256").update(renderedParts.join("\n")).digest("hex").slice(0, 60);

  return {
    userPrompt: userRecord.message.content,
    sessionId: userRecord.sessionId,
    model,
    assistantText: assistantText || "(no text response)",
    assistantHadError,
    hadToolError,
    resultBody: renderedParts.length ? renderedParts.join("\n\n") : null,
    toolCalls,
    lastAssistantUuid,
    idempotencyKey,
  };
}

function renderToolUse(block) {
  const name = block.name || "tool";
  const input = block.input ?? {};
  // heading_3 (`### `) so NotionBlockBuilder turns it into an actual heading,
  // not a paragraph with literal asterisks.
  const head = `### 🔧 ${name}`;

  if (name === "Bash" && typeof input.command === "string") {
    return head + "\n```bash\n" + truncate(input.command, 8_000) + "\n```";
  }

  if (name === "Write" && typeof input.file_path === "string") {
    const lang = guessLang(input.file_path);
    const content = typeof input.content === "string" ? input.content : "";
    return `${head} \`${input.file_path}\`\n\`\`\`${lang}\n${truncate(content, 12_000)}\n\`\`\``;
  }

  if (name === "Edit" && typeof input.file_path === "string") {
    const lang = guessLang(input.file_path);
    let body = `${head} \`${input.file_path}\``;
    if (input.old_string)
      body += `\n\n*before:*\n\`\`\`${lang}\n${truncate(input.old_string, 6_000)}\n\`\`\``;
    if (input.new_string)
      body += `\n\n*after:*\n\`\`\`${lang}\n${truncate(input.new_string, 6_000)}\n\`\`\``;
    return body;
  }

  if (name === "MultiEdit" && typeof input.file_path === "string") {
    const lang = guessLang(input.file_path);
    let body = `${head} \`${input.file_path}\``;
    const edits = Array.isArray(input.edits) ? input.edits : [];
    edits.forEach((e, i) => {
      body += `\n\n*edit #${i + 1} before:*\n\`\`\`${lang}\n${truncate(e.old_string || "", 4_000)}\n\`\`\``;
      body += `\n*edit #${i + 1} after:*\n\`\`\`${lang}\n${truncate(e.new_string || "", 4_000)}\n\`\`\``;
    });
    return body;
  }

  if (name === "Read" && typeof input.file_path === "string") {
    const range =
      input.offset || input.limit
        ? ` (line ${input.offset ?? 1}${input.limit ? `, ×${input.limit}` : ""})`
        : "";
    return `${head} \`${input.file_path}\`${range}`;
  }

  if ((name === "Grep" || name === "grep") && typeof input.pattern === "string") {
    const where = input.path ? ` in \`${input.path}\`` : "";
    const glob = input.glob ? ` matching \`${input.glob}\`` : "";
    return `${head} pattern \`${input.pattern}\`${where}${glob}`;
  }

  if ((name === "Glob" || name === "glob") && typeof input.pattern === "string") {
    return `${head} \`${input.pattern}\``;
  }

  if (name === "WebFetch" && typeof input.url === "string") {
    const prompt = input.prompt ? `\n\n*prompt:* ${truncate(input.prompt, 500)}` : "";
    return `${head} \`${input.url}\`${prompt}`;
  }

  // Fallback: pretty-printed JSON, but skip empty/uninteresting inputs.
  const json = JSON.stringify(input, null, 2);
  if (!json || json === "{}") return head;
  return head + "\n```json\n" + truncate(json, 8_000) + "\n```";
}

function renderToolResult(block) {
  const content = typeof block.content === "string"
    ? block.content
    : JSON.stringify(block.content, null, 2);
  const errMark = block.is_error ? " (error)" : "";
  return `### ↳ tool_result${errMark}\n\`\`\`\n${truncate(content, 8_000)}\n\`\`\``;
}

function deriveSummary(text) {
  if (!text) return null;
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > 240 ? flat.slice(0, 237) + "…" : flat;
}

function truncate(s, max) {
  if (s == null) return "";
  return s.length > max ? s.slice(0, max) + `\n…[truncated ${s.length - max} chars]` : s;
}

function warn(msg) {
  process.stderr.write(`[prompthubs-auto-save] ${msg}\n`);
}

function bail(msg) {
  warn(msg);
  process.exit(0); // never block Claude
}
