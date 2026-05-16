#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ErrorCode,
  McpError,
} from "@modelcontextprotocol/sdk/types.js";

const BASE_URL = (process.env.PROMPTHUBS_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const API_KEY = process.env.PROMPTHUBS_API_KEY;

if (!API_KEY) {
  process.stderr.write(
    "[prompthubs-mcp] PROMPTHUBS_API_KEY env var is required (prefix: ph_).\n"
  );
  process.exit(1);
}

async function callApi(method, path, { body, query } = {}) {
  const url = new URL(`${BASE_URL}${path}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, v);
    }
  }

  const res = await fetch(url, {
    method,
    headers: {
      "X-API-Key": API_KEY,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: body == null ? undefined : JSON.stringify(body),
  });

  const text = await res.text();
  if (!res.ok) {
    throw new McpError(
      ErrorCode.InternalError,
      `PromptHubs API ${method} ${path} failed: ${res.status} ${res.statusText} — ${text}`
    );
  }
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

const TOOLS = [
  {
    name: "save_log",
    description:
      "Save the current prompt + result as a PromptLog in PromptHubs. Call this after a meaningful prompt-response exchange to build a searchable history. Pass sessionId/idempotencyKey when called from automated hooks so duplicate firings collapse into a single row.",
    inputSchema: {
      type: "object",
      properties: {
        promptText: {
          type: "string",
          description: "The full prompt text the user sent.",
        },
        modelUsed: {
          type: "string",
          description: "Model identifier, e.g. claude-opus-4-7, claude-sonnet-4-6.",
        },
        status: {
          type: "string",
          enum: ["SUCCESS", "FAILURE"],
          description: "Whether the prompt produced a useful result.",
        },
        resultSummary: {
          type: "string",
          description: "Short summary of the model output (optional).",
        },
        resultBody: {
          type: "string",
          description: "Full assistant turn rendered as Markdown — text, code blocks, terminal commands and outputs (optional).",
        },
        tags: {
          type: "array",
          items: { type: "string" },
          description: "Free-form tags (optional).",
        },
        sessionId: {
          type: "string",
          description: "Session UUID grouping turns of one conversation (optional).",
        },
        idempotencyKey: {
          type: "string",
          description: "Caller-supplied dedup key. Same key → single row even on retries (optional, max 64 chars).",
        },
        metadata: {
          type: "object",
          description: "Optional structured details (tool calls list, model parameters, etc.).",
        },
      },
      required: ["promptText", "modelUsed", "status"],
    },
  },
  {
    name: "search_prompts",
    description:
      "Search APPROVED prompts in the connected PromptHubs workspace by keyword or category. Returns up to 10 most-recently-updated prompts.",
    inputSchema: {
      type: "object",
      properties: {
        keyword: { type: "string", description: "Free-text keyword to match." },
        category: { type: "string", description: "Filter by category." },
      },
    },
  },
  {
    name: "get_prompt",
    description: "Fetch a single prompt (with its latest version) by UUID.",
    inputSchema: {
      type: "object",
      properties: {
        id: {
          type: "string",
          description: "Prompt UUID returned from search_prompts.",
        },
      },
      required: ["id"],
    },
  },
  {
    name: "list_docs",
    description:
      "List up to 50 most-recently-updated workspace docs (coding conventions, API specs, runbooks). " +
      "Returns id + title + category only — use `get_doc` for full body.",
    inputSchema: {
      type: "object",
      properties: {
        category: { type: "string", description: "Filter by category (optional)." },
      },
    },
  },
  {
    name: "get_doc",
    description:
      "Fetch one workspace doc's full Markdown body by UUID. Call this when a prompt's `linkedDocs` " +
      "points at a coding convention / API spec the user expects you to follow.",
    inputSchema: {
      type: "object",
      properties: {
        id: {
          type: "string",
          description: "Doc UUID (from get_prompt.linkedDocs[*].id or list_docs).",
        },
      },
      required: ["id"],
    },
  },
];

const server = new Server(
  { name: "prompthubs", version: "0.1.0" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params;

  try {
    let result;
    switch (name) {
      case "save_log":
        result = await callApi("POST", "/api/v1/external/mcp/logs", {
          body: {
            promptText: args.promptText,
            modelUsed: args.modelUsed,
            status: args.status,
            resultSummary: args.resultSummary,
            resultBody: args.resultBody,
            tags: args.tags,
            sessionId: args.sessionId,
            idempotencyKey: args.idempotencyKey,
            metadata: args.metadata,
          },
        });
        break;

      case "search_prompts":
        result = await callApi("POST", "/api/v1/external/mcp/search", {
          body: { keyword: args.keyword, category: args.category },
        });
        break;

      case "get_prompt":
        if (!args.id) throw new McpError(ErrorCode.InvalidParams, "id is required");
        result = await callApi("GET", `/api/v1/external/mcp/prompts/${encodeURIComponent(args.id)}`);
        break;

      case "list_docs":
        result = await callApi("GET", "/api/v1/external/mcp/docs", {
          query: { category: args.category },
        });
        break;

      case "get_doc":
        if (!args.id) throw new McpError(ErrorCode.InvalidParams, "id is required");
        result = await callApi("GET", `/api/v1/external/mcp/docs/${encodeURIComponent(args.id)}`);
        break;

      default:
        throw new McpError(ErrorCode.MethodNotFound, `Unknown tool: ${name}`);
    }

    return {
      content: [
        { type: "text", text: typeof result === "string" ? result : JSON.stringify(result, null, 2) },
      ],
    };
  } catch (err) {
    if (err instanceof McpError) throw err;
    throw new McpError(ErrorCode.InternalError, err?.message ?? String(err));
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
process.stderr.write(`[prompthubs-mcp] connected — base=${BASE_URL}\n`);
