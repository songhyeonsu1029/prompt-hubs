import { useQuery } from "@tanstack/react-query"
import { useParams, Link } from "react-router-dom"
import apiClient from "@/api/client"
import { Plus, FileText, Calendar, User, BookOpen, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useMemo, useState } from "react"

interface Doc {
  id: string
  title: string
  category: string
  authorName: string
  updatedAt: string
}

interface PageResponse<T> {
  content: T[]
  page: number
  totalElements: number
  totalPages: number
}

export default function DocListPage() {
  const { slug } = useParams()
  const [category, setCategory] = useState<string>("")

  const { data, isLoading, error } = useQuery<PageResponse<Doc>>({
    queryKey: ["docs", slug, category],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/docs`, {
        params: category ? { category } : undefined,
      })
      const body = res.data
      if (Array.isArray(body)) {
        return { content: body, page: 0, totalElements: body.length, totalPages: 1 }
      }
      return body
    },
  })

  const docs = data?.content ?? []
  const categories = useMemo(() => {
    const set = new Set<string>()
    docs.forEach((d) => d.category && set.add(d.category))
    return Array.from(set)
  }, [docs])

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">문서</h1>
          <p className="mt-1 text-sm text-slate-600">
            팀 컨벤션, 가이드라인, 지식 베이스를 관리합니다.
          </p>
        </div>
        <Button asChild>
          <Link to={`/w/${slug}/docs/new`}>
            <Plus className="mr-2 h-4 w-4" />새 문서
          </Link>
        </Button>
      </div>

      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setCategory("")}
            className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
              category === ""
                ? "border-primary bg-primary text-white"
                : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
            }`}
          >
            전체
          </button>
          {categories.map((c) => (
            <button
              key={c}
              onClick={() => setCategory(c)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                category === c
                  ? "border-primary bg-primary text-white"
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
              }`}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-slate-500">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          문서 불러오는 중...
        </div>
      ) : error ? (
        <div className="rounded-md border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          문서를 불러오지 못했습니다.
        </div>
      ) : docs.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 bg-white p-12 text-center">
          <BookOpen className="mb-3 h-10 w-10 text-slate-300" />
          <h3 className="mb-1 font-semibold text-slate-900">
            아직 문서가 없습니다
          </h3>
          <p className="mb-4 text-sm text-slate-500">
            팀의 가이드라인이나 컨벤션을 첫 문서로 작성해보세요.
          </p>
          <Button asChild>
            <Link to={`/w/${slug}/docs/new`}>
              <Plus className="mr-2 h-4 w-4" />첫 문서 작성하기
            </Link>
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {docs.map((doc) => (
            <Link
              key={doc.id}
              to={`/w/${slug}/docs/${doc.id}`}
              className="group flex flex-col justify-between rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition-all hover:border-primary hover:shadow-md"
            >
              <div>
                <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-white">
                  <FileText className="h-5 w-5" />
                </div>
                <h3 className="mb-1 line-clamp-1 font-semibold text-slate-900 transition-colors group-hover:text-primary">
                  {doc.title}
                </h3>
                {doc.category && (
                  <span className="inline-flex items-center rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                    {doc.category}
                  </span>
                )}
              </div>

              <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4 text-xs text-slate-500">
                <div className="flex items-center">
                  <User className="mr-1 h-3 w-3" />
                  {doc.authorName}
                </div>
                <div className="flex items-center">
                  <Calendar className="mr-1 h-3 w-3" />
                  {new Date(doc.updatedAt).toLocaleDateString()}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
