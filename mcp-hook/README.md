# prompthubs-mcp-hook

Claude Code **Stop hook** that automatically archives every conversation turn
(user prompt + assistant response + tool calls) into a
[PromptHubs](https://github.com/songhyeonsu1029/prompt-hubs) workspace.

Once installed, every time Claude finishes a turn the hook reads the
session transcript, extracts the last user-prompt → assistant-reply pair
(rendered as Markdown with tool calls inlined), and posts it to your
PromptHubs backend so your team's prompt/result history is never lost.

The hook is silent on success and exits `0` even on failure — a backend
outage never blocks Claude Code.

## Requirements

- Node.js **>= 18** (uses the built-in `fetch`)
- A PromptHubs workspace and an API key (`ph_…`)

## Install

You don't need to install anything globally. Claude Code will fetch the
package on demand via `npx`:

```bash
# Optional: warm the npx cache
npx -y prompthubs-mcp-hook --help
```

## Configure Claude Code

Add the hook to your project (`<repo>/.claude/settings.local.json`) or
user (`~/.claude/settings.json`) Claude Code settings:

```json
{
  "hooks": {
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "PROMPTHUBS_API_KEY=ph_xxx PROMPTHUBS_BASE_URL=https://your-prompthubs-backend.example.com npx -y prompthubs-mcp-hook",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

Replace `ph_xxx` and the base URL with your own workspace credentials.

## Environment variables

| Name                  | Required | Default                  | Description                                  |
| --------------------- | -------- | ------------------------ | -------------------------------------------- |
| `PROMPTHUBS_API_KEY`  | yes      | —                        | Workspace API key (`ph_…`)                   |
| `PROMPTHUBS_BASE_URL` | no       | `http://localhost:8080`  | Base URL of your PromptHubs backend          |

## What gets saved

For each completed turn the hook posts the following to
`POST {BASE_URL}/api/v1/external/mcp/logs` with header `X-API-Key`:

- `promptText` — the user's prompt for the turn
- `modelUsed` — the model that produced the response
- `status` — `SUCCESS` / `PARTIAL` (tool error) / `FAILURE` (assistant error)
- `resultSummary` — first ~240 chars of the assistant text
- `resultBody` — full Markdown rendering of the assistant reply, including
  `Bash`, `Edit`, `Write`, `Read`, `Grep`, `WebFetch` etc. tool calls and
  their results
- `tags` — `["claude-code", "auto"]`
- `sessionId`, `idempotencyKey`, `metadata.cwd`, `metadata.toolCalls`

`thinking` blocks are intentionally **not** archived.

## How it works

1. Claude Code fires the `Stop` hook when a turn finishes and pipes a JSON
   payload (`{ session_id, transcript_path, … }`) to the hook's stdin.
2. The hook reads the JSONL transcript at `transcript_path`, locates the
   most recent real user prompt (string-content, not a tool_result echo),
   and walks forward collecting every assistant message and tool block of
   that turn.
3. Each block is rendered as Markdown so it stays readable in PromptHubs
   (and downstream Notion/Slack mirrors).
4. The rendered turn is posted with an idempotency key derived from the
   closing assistant message's UUID, so retries collapse.

## License

[MIT](./LICENSE) © songhyeonsu1029
