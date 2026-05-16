import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { cn } from "@/lib/utils"

/**
 * Renders Markdown text with Tailwind-tuned typography. Used wherever stored
 * Markdown (log result bodies, prompt bodies, doc content, success criteria,
 * validation method, etc.) needs to surface as actual formatting on the web UI
 * — Notion already renders these correctly, but the React side was showing the
 * raw `**bold**` / backtick syntax.
 *
 * `tone="dark"` flips to a near-black code block surface, used for prompt-body
 * cards that intentionally look like a terminal.
 */
export default function Markdown({
  content,
  tone = "light",
  className,
}: {
  content: string | null | undefined
  tone?: "light" | "dark"
  className?: string
}) {
  if (!content || !content.trim()) {
    return <p className="text-sm italic text-slate-400">—</p>
  }

  const isDark = tone === "dark"

  return (
    <div
      className={cn(
        "prose-sm max-w-none break-words",
        isDark ? "text-slate-50" : "text-slate-800",
        className
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: (props) => (
            <h1
              {...props}
              className="mt-4 mb-2 text-xl font-bold tracking-tight"
            />
          ),
          h2: (props) => (
            <h2
              {...props}
              className="mt-4 mb-2 text-lg font-bold tracking-tight"
            />
          ),
          h3: (props) => (
            <h3
              {...props}
              className="mt-3 mb-1.5 text-base font-semibold tracking-tight"
            />
          ),
          h4: (props) => (
            <h4 {...props} className="mt-3 mb-1 text-sm font-semibold" />
          ),
          p: (props) => (
            <p {...props} className="my-2 text-sm leading-relaxed" />
          ),
          ul: (props) => (
            <ul {...props} className="my-2 ml-5 list-disc space-y-1 text-sm" />
          ),
          ol: (props) => (
            <ol
              {...props}
              className="my-2 ml-5 list-decimal space-y-1 text-sm"
            />
          ),
          li: (props) => <li {...props} className="leading-relaxed" />,
          strong: (props) => (
            <strong
              {...props}
              className={cn(
                "font-semibold",
                isDark ? "text-white" : "text-slate-900"
              )}
            />
          ),
          em: (props) => <em {...props} className="italic" />,
          a: (props) => (
            <a
              {...props}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline-offset-2 hover:underline"
            />
          ),
          hr: () => <hr className="my-4 border-slate-200" />,
          blockquote: (props) => (
            <blockquote
              {...props}
              className="my-3 border-l-2 border-slate-300 pl-3 italic text-slate-600"
            />
          ),
          table: (props) => (
            <div className="my-3 overflow-x-auto">
              <table
                {...props}
                className="w-full border-collapse text-sm"
              />
            </div>
          ),
          th: (props) => (
            <th
              {...props}
              className="border border-slate-200 bg-slate-50 px-3 py-1.5 text-left font-semibold"
            />
          ),
          td: (props) => (
            <td {...props} className="border border-slate-200 px-3 py-1.5" />
          ),
          code: ({ inline, className: cls, children, ...rest }: any) => {
            if (inline) {
              return (
                <code
                  {...rest}
                  className={cn(
                    "rounded px-1 py-0.5 font-mono text-[0.85em]",
                    isDark
                      ? "bg-slate-800 text-amber-200"
                      : "bg-slate-100 text-rose-700"
                  )}
                >
                  {children}
                </code>
              )
            }
            return (
              <code {...rest} className={cn("font-mono text-sm", cls)}>
                {children}
              </code>
            )
          },
          pre: (props) => (
            <pre
              {...props}
              className={cn(
                "my-3 overflow-x-auto rounded-md p-3 text-xs leading-relaxed",
                isDark
                  ? "bg-slate-900 text-slate-100"
                  : "bg-slate-900 text-slate-100"
              )}
            />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
