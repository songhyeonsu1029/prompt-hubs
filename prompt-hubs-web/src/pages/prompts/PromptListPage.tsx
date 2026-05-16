import { useQuery } from "@tanstack/react-query"
import { Link, useParams, useSearchParams } from "react-router-dom"
import apiClient from "@/api/client"
import { Plus, Search, ClipboardList, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"
import { useState } from "react"

type Status = "DRAFT" | "IN_REVIEW" | "APPROVED" | "ARCHIVED"

interface Prompt {
  id: string
  title: string
  category: string | null
  tags: string[] | null
  status: Status
  authorName: string
  updatedAt: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
}

const STATUSES: { value: Status | ""; label: string }[] = [
  { value: "", label: "전체" },
  { value: "DRAFT", label: "초안" },
  { value: "IN_REVIEW", label: "리뷰 중" },
  { value: "APPROVED", label: "승인됨" },
  { value: "ARCHIVED", label: "보관" },
]

function statusStyle(status: Status) {
  switch (status) {
    case "APPROVED":
      return "bg-emerald-50 text-emerald-700 border-emerald-200"
    case "IN_REVIEW":
      return "bg-blue-50 text-blue-700 border-blue-200"
    case "ARCHIVED":
      return "bg-slate-100 text-slate-500 border-slate-200"
    default:
      return "bg-amber-50 text-amber-700 border-amber-200"
  }
}

function statusLabel(status: Status): string {
  return STATUSES.find((s) => s.value === status)?.label || status
}

export default function PromptListPage() {
  const { slug } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get("status") as Status | "") || ""
  const [keyword, setKeyword] = useState(searchParams.get("keyword") || "")

  const { data, isLoading, error } = useQuery<PageResponse<Prompt>>({
    queryKey: ["prompts", slug, status, searchParams.get("keyword")],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (status) params.status = status
      const kw = searchParams.get("keyword")
      if (kw) params.keyword = kw
      const res = await apiClient.get(`/w/${slug}/prompts`, { params })
      const body = res.data
      if (Array.isArray(body)) {
        return { content: body, totalElements: body.length }
      }
      return body
    },
  })

  const prompts = data?.content ?? []

  const applyKeyword = () => {
    const next = new URLSearchParams(searchParams)
    if (keyword) next.set("keyword", keyword)
    else next.delete("keyword")
    setSearchParams(next)
  }

  const setStatus = (s: Status | "") => {
    const next = new URLSearchParams(searchParams)
    if (s) next.set("status", s)
    else next.delete("status")
    setSearchParams(next)
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            프롬프트 라이브러리
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            팀에서 검증된 프롬프트와 버전을 관리합니다.
          </p>
        </div>
        <Button asChild>
          <Link to={`/w/${slug}/prompts/new`}>
            <Plus className="mr-2 h-4 w-4" />
            프롬프트 생성
          </Link>
        </Button>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && applyKeyword()}
            placeholder="제목·태그·카테고리로 검색"
            className="pl-9"
          />
        </div>
        <Button variant="outline" onClick={applyKeyword}>
          검색
        </Button>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUSES.map((s) => (
          <button
            key={s.value}
            onClick={() => setStatus(s.value)}
            className={cn(
              "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
              status === s.value
                ? "border-primary bg-primary text-white"
                : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
            )}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-slate-500">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          프롬프트 불러오는 중...
        </div>
      ) : error ? (
        <div className="rounded-md border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          프롬프트를 불러오지 못했습니다.
        </div>
      ) : prompts.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 bg-white p-12 text-center">
          <ClipboardList className="mb-3 h-10 w-10 text-slate-300" />
          <h3 className="mb-1 font-semibold text-slate-900">
            검색 결과가 없습니다
          </h3>
          <p className="mb-4 text-sm text-slate-500">
            새 프롬프트를 만들거나 다른 필터를 시도해보세요.
          </p>
          <Button asChild>
            <Link to={`/w/${slug}/prompts/new`}>
              <Plus className="mr-2 h-4 w-4" />첫 프롬프트 만들기
            </Link>
          </Button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                {["제목", "상태", "카테고리", "태그", "작성자", "수정일"].map(
                  (h) => (
                    <th
                      key={h}
                      className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500"
                    >
                      {h}
                    </th>
                  )
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {prompts.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-6 py-4">
                    <Link
                      to={`/w/${slug}/prompts/${p.id}`}
                      className="text-sm font-medium text-slate-900 hover:text-primary"
                    >
                      {p.title}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-6 py-4">
                    <span
                      className={cn(
                        "inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium",
                        statusStyle(p.status)
                      )}
                    >
                      {statusLabel(p.status)}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-500">
                    {p.category || "—"}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex flex-wrap gap-1">
                      {(p.tags ?? []).slice(0, 3).map((t) => (
                        <span
                          key={t}
                          className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600"
                        >
                          {t}
                        </span>
                      ))}
                      {(p.tags?.length ?? 0) > 3 && (
                        <span className="text-xs text-slate-400">
                          +{(p.tags?.length ?? 0) - 3}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-500">
                    {p.authorName}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-500">
                    {new Date(p.updatedAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
