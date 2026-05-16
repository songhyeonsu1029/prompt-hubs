import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { ChevronLeft, Loader2 } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"

interface DiffLine {
  lineNumber: number
  content: string
  changeType: "UNCHANGED" | "ADDED" | "DELETED"
}

interface DiffResponse {
  sourceVersionNumber: string
  targetVersionNumber: string
  promptTextDiff: DiffLine[]
  successCriteriaDiff: DiffLine[]
  validationMethodDiff: DiffLine[]
}

function DiffPanel({ title, lines }: { title: string; lines: DiffLine[] }) {
  return (
    <Card>
      <CardHeader className="border-b border-slate-100 py-3">
        <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {lines.length === 0 ? (
          <p className="p-6 text-center text-sm text-slate-500">변경 없음</p>
        ) : (
          <pre className="overflow-x-auto font-mono text-xs leading-relaxed">
            {lines.map((line, idx) => (
              <div
                key={idx}
                className={cn(
                  "flex gap-2 px-3 py-0.5",
                  line.changeType === "ADDED" && "bg-emerald-50 text-emerald-900",
                  line.changeType === "DELETED" && "bg-red-50 text-red-900",
                  line.changeType === "UNCHANGED" && "text-slate-700"
                )}
              >
                <span className="w-8 text-right text-slate-400 select-none">
                  {line.changeType === "ADDED"
                    ? "+"
                    : line.changeType === "DELETED"
                    ? "−"
                    : " "}
                </span>
                <span className="w-10 text-right text-slate-400 select-none">
                  {line.lineNumber}
                </span>
                <span className="whitespace-pre-wrap break-all">{line.content}</span>
              </div>
            ))}
          </pre>
        )}
      </CardContent>
    </Card>
  )
}

export default function VersionDiffPage() {
  const { slug, promptId, v1, v2 } = useParams()

  const { data, isLoading, error } = useQuery<DiffResponse>({
    queryKey: ["diff", slug, promptId, v1, v2],
    queryFn: async () => {
      const res = await apiClient.get(
        `/w/${slug}/prompts/${promptId}/versions/${v2}/diff`,
        { params: { compareWith: v1 } }
      )
      return res.data
    },
    enabled: !!v1 && !!v2,
  })

  return (
    <div className="space-y-6">
      <nav>
        <Link
          to={`/w/${slug}/prompts/${promptId}`}
          className="flex items-center text-sm text-slate-500 transition-colors hover:text-primary"
        >
          <ChevronLeft className="mr-1 h-4 w-4" />
          상세로 돌아가기
        </Link>
      </nav>

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          버전 비교
        </h1>
        {data && (
          <div className="flex items-center gap-2 text-sm">
            <span className="rounded bg-red-100 px-2 py-1 font-mono text-red-800">
              v{data.sourceVersionNumber}
            </span>
            <span className="text-slate-400">→</span>
            <span className="rounded bg-emerald-100 px-2 py-1 font-mono text-emerald-800">
              v{data.targetVersionNumber}
            </span>
          </div>
        )}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-slate-500">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          버전 비교 중...
        </div>
      ) : error ? (
        <div className="rounded-md border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          버전을 비교할 수 없습니다.
        </div>
      ) : data ? (
        <div className="space-y-6">
          <DiffPanel title="프롬프트 본문" lines={data.promptTextDiff} />
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            <DiffPanel title="성공 기준" lines={data.successCriteriaDiff} />
            <DiffPanel title="검증 방법" lines={data.validationMethodDiff} />
          </div>
        </div>
      ) : null}
    </div>
  )
}
