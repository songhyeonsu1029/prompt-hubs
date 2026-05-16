import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { Card, CardContent } from "@/components/ui/card"
import { CheckCircle2, ChevronRight, Loader2, MessageSquare, XCircle, Clock } from "lucide-react"
import { useState } from "react"

type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED"

interface Review {
  id: string
  status: ReviewStatus
  versionId: string
  versionNumber: string
  promptId: string
  promptTitle: string
  requesterId: string
  requesterName: string
  reviewerId: string
  reviewerName: string
  unresolvedComments: number
  createdAt: string
  reviewedAt: string | null
}

interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  page: number
  size: number
}

const STATUS_LABEL: Record<ReviewStatus, string> = {
  PENDING: "대기중",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
}

const STATUS_STYLE: Record<ReviewStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-rose-100 text-rose-800",
}

const STATUS_ICON: Record<ReviewStatus, React.ComponentType<{ className?: string }>> = {
  PENDING: Clock,
  APPROVED: CheckCircle2,
  REJECTED: XCircle,
}

export default function ReviewListPage() {
  const { slug } = useParams()
  const [status, setStatus] = useState<ReviewStatus | "">("")

  const reviews = useQuery<PageResponse<Review>>({
    queryKey: ["reviews", slug, status],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/reviews`, {
        params: status ? { status } : undefined,
      })
      return res.data
    },
  })

  const items = reviews.data?.content ?? []
  const counts = {
    PENDING: items.filter((r) => r.status === "PENDING").length,
    APPROVED: items.filter((r) => r.status === "APPROVED").length,
    REJECTED: items.filter((r) => r.status === "REJECTED").length,
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">리뷰</h1>
        <p className="mt-1 text-sm text-slate-600">
          프롬프트 버전이 라이브러리에 합쳐지기 전의 검토 단계입니다. 승인된 리뷰만
          Prompts 가 APPROVED 로 전이됩니다.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <FilterPill
          active={status === ""}
          onClick={() => setStatus("")}
          label={`전체 (${items.length})`}
        />
        <FilterPill
          active={status === "PENDING"}
          onClick={() => setStatus("PENDING")}
          label={`대기중 (${counts.PENDING})`}
        />
        <FilterPill
          active={status === "APPROVED"}
          onClick={() => setStatus("APPROVED")}
          label={`승인됨 (${counts.APPROVED})`}
        />
        <FilterPill
          active={status === "REJECTED"}
          onClick={() => setStatus("REJECTED")}
          label={`반려됨 (${counts.REJECTED})`}
        />
      </div>

      <Card>
        <CardContent className="p-0">
          {reviews.isLoading ? (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-5 w-5 animate-spin" />
            </div>
          ) : items.length === 0 ? (
            <div className="py-16 text-center text-sm text-slate-500">
              조건에 맞는 리뷰가 없습니다.
            </div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {items.map((r) => {
                const Icon = STATUS_ICON[r.status]
                return (
                  <li key={r.id}>
                    <Link
                      to={`/w/${slug}/prompts/${r.promptId}/review`}
                      className="flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-slate-50"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span
                            className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${STATUS_STYLE[r.status]}`}
                          >
                            <Icon className="h-3 w-3" />
                            {STATUS_LABEL[r.status]}
                          </span>
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-mono text-slate-700">
                            v{r.versionNumber}
                          </span>
                          {r.unresolvedComments > 0 && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2 py-0.5 text-[11px] font-medium text-blue-800">
                              <MessageSquare className="h-3 w-3" />
                              {r.unresolvedComments} 미해결
                            </span>
                          )}
                        </div>
                        <p className="mt-1 truncate text-sm font-semibold text-slate-900">
                          {r.promptTitle}
                        </p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          요청 <strong className="text-slate-700">{r.requesterName}</strong> →
                          리뷰어 <strong className="text-slate-700">{r.reviewerName}</strong>
                          {" · "}
                          {new Date(r.createdAt).toLocaleString()}
                          {r.reviewedAt && (
                            <>
                              {" · "}완료 {new Date(r.reviewedAt).toLocaleString()}
                            </>
                          )}
                        </p>
                      </div>
                      <ChevronRight className="h-4 w-4 flex-shrink-0 text-slate-400" />
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function FilterPill({
  active,
  onClick,
  label,
}: {
  active: boolean
  onClick: () => void
  label: string
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
        active
          ? "border-primary bg-primary/10 text-primary"
          : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
      }`}
    >
      {label}
    </button>
  )
}
