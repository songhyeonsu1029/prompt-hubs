import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import {
  ChevronRight,
  Copy,
  ExternalLink,
  FileText,
  Loader2,
  MessageSquare,
  Plus,
  X,
} from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import Markdown from "@/components/Markdown"
import { useEffect, useMemo, useState } from "react"

type Status = "DRAFT" | "IN_REVIEW" | "APPROVED" | "ARCHIVED"

interface VersionInfo {
  id: string
  versionNumber: string
  promptText: string
  successCriteria: string
  validationMethod: string
  changeNote: string | null
  createdByName: string
  createdAt: string
}

interface PromptDetail {
  id: string
  title: string
  category: string | null
  tags: string[] | null
  status: Status
  currentVersion: VersionInfo | null
  linkedDocs: { id: string; title: string; category?: string }[] | null
  createdAt: string
  updatedAt: string
  authorName: string
}

interface VersionItem {
  id: string
  versionNumber: string
  createdByName: string
  createdAt: string
  changeNote: string | null
  promptText?: string
  successCriteria?: string
  validationMethod?: string
}

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

interface WorkspaceDoc {
  id: string
  title: string
  category: string | null
}

interface PageEnvelope<T> {
  content: T[]
}

export default function PromptDetailPage() {
  const { slug, promptId } = useParams()
  const qc = useQueryClient()
  const [copied, setCopied] = useState(false)
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null)
  const [addDocId, setAddDocId] = useState("")

  const { data: prompt, isLoading } = useQuery<PromptDetail>({
    queryKey: ["prompt", slug, promptId],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/prompts/${promptId}`)
      return res.data
    },
  })

  const { data: versions } = useQuery<VersionItem[]>({
    queryKey: ["prompt", slug, promptId, "versions"],
    queryFn: async () => {
      const res = await apiClient.get(
        `/w/${slug}/prompts/${promptId}/versions`
      )
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const { data: allDocs } = useQuery<WorkspaceDoc[]>({
    queryKey: ["docs", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/docs`)
      const body = res.data as WorkspaceDoc[] | PageEnvelope<WorkspaceDoc>
      if (Array.isArray(body)) return body
      return body.content ?? []
    },
  })

  const updateLinks = useMutation({
    mutationFn: async (docIds: string[]) => {
      await apiClient.put(`/w/${slug}/prompts/${promptId}/docs`, { docIds })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["prompt", slug, promptId] })
    },
  })

  useEffect(() => {
    if (!selectedVersionId && prompt?.currentVersion?.id) {
      setSelectedVersionId(prompt.currentVersion.id)
    }
  }, [prompt?.currentVersion?.id, selectedVersionId])

  // Pick the version to display: explicit selection beats currentVersion. When the
  // selected one is the current version we already have full body fields from `prompt`.
  const displayedVersion: VersionInfo | undefined = useMemo(() => {
    if (!prompt) return undefined
    if (!selectedVersionId || selectedVersionId === prompt.currentVersion?.id) {
      return prompt.currentVersion ?? undefined
    }
    const v = (versions ?? []).find((x) => x.id === selectedVersionId)
    if (!v) return prompt.currentVersion ?? undefined
    // Older versions in the list don't include full body fields; coerce best-effort.
    return {
      id: v.id,
      versionNumber: v.versionNumber,
      promptText: v.promptText ?? "(이 버전의 본문은 목록 응답에 포함되어 있지 않습니다. Diff 페이지에서 확인하세요.)",
      successCriteria: v.successCriteria ?? "",
      validationMethod: v.validationMethod ?? "",
      changeNote: v.changeNote,
      createdByName: v.createdByName,
      createdAt: v.createdAt,
    }
  }, [prompt, versions, selectedVersionId])

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-slate-500">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        프롬프트 불러오는 중...
      </div>
    )
  }
  if (!prompt) return <div className="text-slate-500">프롬프트를 찾을 수 없습니다.</div>

  const v = displayedVersion
  const linkedDocs = prompt.linkedDocs ?? []
  const tags = prompt.tags ?? []
  const currentVersionId = prompt.currentVersion?.id
  const isViewingHistorical =
    !!v && !!currentVersionId && v.id !== currentVersionId

  const copyPrompt = async () => {
    if (!v) return
    try {
      await navigator.clipboard.writeText(v.promptText)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* ignore */
    }
  }

  return (
    <div className="space-y-6">
      <nav className="flex items-center text-sm text-slate-500">
        <Link
          to={`/w/${slug}/prompts`}
          className="transition-colors hover:text-primary"
        >
          라이브러리
        </Link>
        <ChevronRight className="mx-2 h-4 w-4" />
        <span className="truncate font-medium text-slate-900">
          {prompt.title}
        </span>
      </nav>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-3xl font-bold tracking-tight text-slate-900">
              {prompt.title}
            </h1>
            <span
              className={cn(
                "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
                statusStyle(prompt.status)
              )}
            >
              {prompt.status}
            </span>
            {/* Version picker — replaces the old separate history card. */}
            {(versions?.length ?? 0) > 0 && (
              <select
                value={selectedVersionId ?? ""}
                onChange={(e) => setSelectedVersionId(e.target.value)}
                className="rounded-md border border-slate-200 bg-white px-2 py-1 font-mono text-xs text-slate-700 shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                title="버전 선택"
              >
                {(versions ?? []).map((ver) => (
                  <option key={ver.id} value={ver.id}>
                    v{ver.versionNumber}
                    {ver.id === currentVersionId ? " (현재)" : ""}
                  </option>
                ))}
              </select>
            )}
            {isViewingHistorical && currentVersionId && v && (
              <Button asChild size="sm" variant="ghost" className="h-7 px-2 text-xs">
                <Link
                  to={`/w/${slug}/prompts/${promptId}/diff/${v.id}/${currentVersionId}`}
                >
                  현재와 비교
                </Link>
              </Button>
            )}
          </div>
          <p className="text-sm text-slate-600">
            {prompt.category || "분류 없음"} · 작성자 {prompt.authorName}
          </p>
          {tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {tags.map((t) => (
                <span
                  key={t}
                  className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600"
                >
                  #{t}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button asChild size="sm">
            <Link to={`/w/${slug}/prompts/${promptId}/version/new`}>
              <Plus className="mr-2 h-4 w-4" />
              새 버전 만들기
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm">
            <Link to={`/w/${slug}/prompts/${promptId}/review`}>
              <MessageSquare className="mr-2 h-4 w-4" />
              리뷰
            </Link>
          </Button>
        </div>
      </div>

      {isViewingHistorical && (
        <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
          이전 버전(v{v?.versionNumber})을 보고 있습니다. 수정은 새 버전을 만들거나
          현재 버전에 대한 리뷰를 통해 진행하세요.
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between border-b border-slate-100 py-3">
              <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                프롬프트 본문 (v{v?.versionNumber ?? "—"})
              </CardTitle>
              <Button variant="ghost" size="sm" onClick={copyPrompt}>
                <Copy className="mr-1 h-3 w-3" />
                {copied ? "복사됨" : "복사"}
              </Button>
            </CardHeader>
            <CardContent className="p-6">
              {v ? (
                <Markdown content={v.promptText} />
              ) : (
                <p className="text-sm text-slate-500">버전 정보가 없습니다.</p>
              )}
            </CardContent>
          </Card>

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            <Card>
              <CardHeader className="border-b border-slate-100 py-3">
                <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  성공 기준
                </CardTitle>
              </CardHeader>
              <CardContent className="p-6">
                <Markdown content={v?.successCriteria} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="border-b border-slate-100 py-3">
                <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  검증 방법
                </CardTitle>
              </CardHeader>
              <CardContent className="p-6">
                <Markdown content={v?.validationMethod} />
              </CardContent>
            </Card>
          </div>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader className="border-b border-slate-100 py-3">
              <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                연결된 문서
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {linkedDocs.length === 0 ? (
                <p className="p-6 text-center text-sm italic text-slate-500">
                  연결된 문서가 없습니다
                </p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {linkedDocs.map((doc) => (
                    <li
                      key={doc.id}
                      className="flex items-center justify-between p-3 transition-colors hover:bg-slate-50"
                    >
                      <Link
                        to={`/w/${slug}/docs/${doc.id}`}
                        className="flex min-w-0 flex-1 items-center gap-3"
                      >
                        <FileText className="h-4 w-4 flex-shrink-0 text-slate-400" />
                        <span className="truncate text-sm font-medium">
                          {doc.title}
                        </span>
                        <ExternalLink className="h-3 w-3 flex-shrink-0 text-slate-400" />
                      </Link>
                      <button
                        onClick={() => {
                          const next = linkedDocs
                            .filter((d) => d.id !== doc.id)
                            .map((d) => d.id)
                          updateLinks.mutate(next)
                        }}
                        disabled={updateLinks.isPending}
                        title="연결 해제"
                        className="ml-2 rounded p-1 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              {/* Add — picks any workspace doc not already linked. */}
              {(() => {
                const linkedIds = new Set(linkedDocs.map((d) => d.id))
                const available = (allDocs ?? []).filter((d) => !linkedIds.has(d.id))
                return (
                  <div className="space-y-2 border-t border-slate-100 p-3">
                    <p className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
                      문서 추가
                    </p>
                    {available.length === 0 ? (
                      <p className="text-xs italic text-slate-500">
                        워크스페이스에 더 연결할 수 있는 문서가 없습니다.
                      </p>
                    ) : (
                      <div className="flex gap-2">
                        <select
                          value={addDocId}
                          onChange={(e) => setAddDocId(e.target.value)}
                          className="block h-9 flex-1 rounded-md border border-slate-200 bg-white px-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                        >
                          <option value="">— 문서 선택 —</option>
                          {available.map((d) => (
                            <option key={d.id} value={d.id}>
                              {d.title}
                              {d.category ? ` (${d.category})` : ""}
                            </option>
                          ))}
                        </select>
                        <Button
                          size="sm"
                          onClick={() => {
                            if (!addDocId) return
                            updateLinks.mutate([
                              ...linkedDocs.map((d) => d.id),
                              addDocId,
                            ])
                            setAddDocId("")
                          }}
                          disabled={!addDocId || updateLinks.isPending}
                        >
                          {updateLinks.isPending ? (
                            <Loader2 className="h-3 w-3 animate-spin" />
                          ) : (
                            "연결"
                          )}
                        </Button>
                      </div>
                    )}
                    {updateLinks.isError && (
                      <p className="text-xs text-destructive">
                        업데이트 실패:{" "}
                        {(updateLinks.error as any)?.response?.data?.message}
                      </p>
                    )}
                  </div>
                )
              })()}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="border-b border-slate-100 py-3">
              <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                메타데이터
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 p-5 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500">생성일</span>
                <span className="font-medium text-slate-700">
                  {new Date(prompt.createdAt).toLocaleDateString()}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">마지막 수정</span>
                <span className="font-medium text-slate-700">
                  {new Date(prompt.updatedAt).toLocaleDateString()}
                </span>
              </div>
              {v && (
                <div className="flex justify-between">
                  <span className="text-slate-500">버전 작성자</span>
                  <span className="font-medium text-slate-700">
                    {v.createdByName}
                  </span>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
