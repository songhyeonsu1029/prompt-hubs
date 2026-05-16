import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { useAuthStore } from "@/store/authStore"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import Markdown from "@/components/Markdown"
import {
  Check,
  CheckCircle2,
  ChevronLeft,
  Loader2,
  MessageSquare,
  Save,
  Send,
  X,
} from "lucide-react"
import { useEffect, useMemo, useState } from "react"

type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED"

interface Review {
  id: string
  status: ReviewStatus
  versionNumber: string
  promptId: string
  promptTitle: string
  requesterId: string
  requesterName: string
  reviewerName: string
  reviewerId: string
  unresolvedComments: number
  createdAt: string
}

interface Comment {
  id: string
  authorName: string
  content: string
  resolved: boolean
  createdAt: string
}

interface Member {
  id: string
  accountId: string
  name: string
  email: string
}

interface PageResponse<T> {
  content: T[]
}

interface PromptDetail {
  id: string
  title: string
  category: string | null
  tags: string[] | null
  status: string
  currentVersion: {
    id: string
    versionNumber: string
    promptText: string
    successCriteria: string
    validationMethod: string
  } | null
}

export default function PromptReviewPage() {
  const { slug, promptId } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const me = useAuthStore((s) => s.user)
  const [comment, setComment] = useState("")
  const [reviewerId, setReviewerId] = useState("")

  // Draft edit form state
  const [draftTitle, setDraftTitle] = useState("")
  const [draftCategory, setDraftCategory] = useState("")
  const [draftPromptText, setDraftPromptText] = useState("")
  const [draftSuccess, setDraftSuccess] = useState("")
  const [draftValidation, setDraftValidation] = useState("")
  const [draftDirty, setDraftDirty] = useState(false)

  const prompt = useQuery<PromptDetail>({
    queryKey: ["prompt", slug, promptId],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/prompts/${promptId}`)
      return res.data
    },
  })

  const reviewsQuery = useQuery<PageResponse<Review>>({
    queryKey: ["reviews", slug, promptId],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/reviews`, {
        params: { promptId },
      })
      const body = res.data
      if (Array.isArray(body)) return { content: body }
      return body
    },
  })

  const reviews = reviewsQuery.data?.content ?? []
  const currentReview = useMemo(
    () => reviews.find((r) => r.status === "PENDING") || reviews[0],
    [reviews]
  )

  const members = useQuery<Member[]>({
    queryKey: ["members", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/members`)
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: !currentReview,
  })

  useEffect(() => {
    if (members.data && members.data[0]) {
      setReviewerId(members.data[0].accountId)
    }
  }, [members.data])

  // Seed draft form whenever the prompt loads (only if user hasn't started editing).
  useEffect(() => {
    if (!prompt.data || draftDirty) return
    const v = prompt.data.currentVersion
    setDraftTitle(prompt.data.title || "")
    setDraftCategory(prompt.data.category || "")
    setDraftPromptText(v?.promptText || "")
    setDraftSuccess(v?.successCriteria || "")
    setDraftValidation(v?.validationMethod || "")
  }, [prompt.data, draftDirty])

  const comments = useQuery<Comment[]>({
    queryKey: ["review", currentReview?.id, "comments"],
    queryFn: async () => {
      const res = await apiClient.get(
        `/w/${slug}/reviews/${currentReview!.id}/comments`
      )
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: !!currentReview,
  })

  const createReview = useMutation({
    mutationFn: async () => {
      const versionId = prompt.data?.currentVersion?.id
      if (!versionId) throw new Error("버전 정보가 없습니다")
      const res = await apiClient.post(`/w/${slug}/reviews`, {
        promptVersionId: versionId,
        reviewerId,
      })
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["reviews", slug, promptId] })
    },
  })

  const saveDraft = useMutation({
    mutationFn: async () => {
      await apiClient.patch(`/w/${slug}/reviews/${currentReview!.id}/draft`, {
        title: draftTitle,
        category: draftCategory || null,
        promptText: draftPromptText,
        successCriteria: draftSuccess,
        validationMethod: draftValidation,
      })
    },
    onSuccess: () => {
      setDraftDirty(false)
      qc.invalidateQueries({ queryKey: ["prompt", slug, promptId] })
      qc.invalidateQueries({ queryKey: ["reviews", slug, promptId] })
    },
  })

  const addComment = useMutation({
    mutationFn: async () => {
      await apiClient.post(
        `/w/${slug}/reviews/${currentReview!.id}/comments`,
        { content: comment }
      )
    },
    onSuccess: () => {
      setComment("")
      qc.invalidateQueries({
        queryKey: ["review", currentReview?.id, "comments"],
      })
    },
  })

  const resolveComment = useMutation({
    mutationFn: async (commentId: string) => {
      await apiClient.patch(
        `/w/${slug}/reviews/${currentReview!.id}/comments/${commentId}`
      )
    },
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: ["review", currentReview?.id, "comments"],
      })
    },
  })

  const updateStatus = useMutation({
    mutationFn: async (status: "APPROVED" | "REJECTED") => {
      await apiClient.patch(`/w/${slug}/reviews/${currentReview!.id}`, {
        status,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["prompt", slug, promptId] })
      navigate(`/w/${slug}/prompts/${promptId}`)
    },
  })

  if (prompt.isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-slate-500">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        리뷰 정보 불러오는 중...
      </div>
    )
  }
  if (!prompt.data) return <div className="text-slate-500">프롬프트를 찾을 수 없습니다.</div>

  const v = prompt.data.currentVersion
  const isPending = currentReview?.status === "PENDING"
  const isRequester = !!me && !!currentReview && me.id === currentReview.requesterId
  const isReviewer = !!me && !!currentReview && me.id === currentReview.reviewerId
  const canEditDraft = isPending && isRequester
  const canDecide = isPending && isReviewer
  const markDirty = () => setDraftDirty(true)

  const submitComment = () => {
    if (!comment.trim() || addComment.isPending) return
    addComment.mutate()
  }

  const onCommentKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter alone submits; Shift+Enter keeps the newline.
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submitComment()
    }
  }

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

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            리뷰 — {prompt.data.title}
          </h1>
          {v && (
            <p className="text-sm text-slate-500">버전 v{v.versionNumber}</p>
          )}
        </div>
        {canDecide && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => updateStatus.mutate("REJECTED")}
              disabled={updateStatus.isPending}
              className="border-red-200 text-red-700 hover:bg-red-50"
            >
              <X className="mr-2 h-4 w-4" />
              변경 요청
            </Button>
            <Button
              onClick={() => updateStatus.mutate("APPROVED")}
              disabled={updateStatus.isPending}
              className="bg-emerald-600 hover:bg-emerald-700"
            >
              <Check className="mr-2 h-4 w-4" />
              승인
            </Button>
          </div>
        )}
      </div>

      {!currentReview && (
        <Card>
          <CardHeader className="border-b border-slate-100">
            <CardTitle className="text-base">리뷰 요청</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            <p className="text-sm text-slate-600">
              아직 리뷰가 없습니다. 팀 멤버를 선택해 리뷰를 요청하세요.
            </p>
            <div>
              <Label htmlFor="reviewer">리뷰어</Label>
              <select
                id="reviewer"
                value={reviewerId}
                onChange={(e) => setReviewerId(e.target.value)}
                className="mt-1 block w-full rounded-md border border-slate-200 px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              >
                {(members.data ?? []).map((m) => (
                  <option key={m.accountId} value={m.accountId}>
                    {m.name} ({m.email})
                  </option>
                ))}
              </select>
            </div>
            <Button
              onClick={() => createReview.mutate()}
              disabled={!reviewerId || createReview.isPending}
            >
              {createReview.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  생성 중...
                </>
              ) : (
                <>
                  <MessageSquare className="mr-2 h-4 w-4" />
                  리뷰 요청
                </>
              )}
            </Button>
            {createReview.isError && (
              <p className="text-xs text-destructive">
                리뷰 생성 실패:{" "}
                {(createReview.error as any)?.response?.data?.message}
              </p>
            )}
          </CardContent>
        </Card>
      )}

      {currentReview && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            {canEditDraft ? (
              <Card>
                <CardHeader className="flex flex-row items-center justify-between border-b border-slate-100 py-3">
                  <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    초안 편집 (요청자만 · PENDING 동안에만)
                  </CardTitle>
                  <Button
                    size="sm"
                    onClick={() => saveDraft.mutate()}
                    disabled={!draftDirty || saveDraft.isPending}
                  >
                    {saveDraft.isPending ? (
                      <Loader2 className="mr-2 h-3 w-3 animate-spin" />
                    ) : (
                      <Save className="mr-2 h-3 w-3" />
                    )}
                    {draftDirty ? "수정 저장" : "저장됨"}
                  </Button>
                </CardHeader>
                <CardContent className="space-y-4 p-6">
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <Label htmlFor="draft-title">제목</Label>
                      <Input
                        id="draft-title"
                        value={draftTitle}
                        onChange={(e) => {
                          setDraftTitle(e.target.value)
                          markDirty()
                        }}
                      />
                    </div>
                    <div>
                      <Label htmlFor="draft-category">카테고리</Label>
                      <Input
                        id="draft-category"
                        value={draftCategory}
                        onChange={(e) => {
                          setDraftCategory(e.target.value)
                          markDirty()
                        }}
                      />
                    </div>
                  </div>
                  <div>
                    <Label htmlFor="draft-prompt">프롬프트 본문</Label>
                    <textarea
                      id="draft-prompt"
                      value={draftPromptText}
                      onChange={(e) => {
                        setDraftPromptText(e.target.value)
                        markDirty()
                      }}
                      rows={14}
                      className="block w-full rounded-md border border-slate-200 px-3 py-2 font-mono text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                    />
                  </div>
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <Label htmlFor="draft-success">성공 기준</Label>
                      <textarea
                        id="draft-success"
                        value={draftSuccess}
                        onChange={(e) => {
                          setDraftSuccess(e.target.value)
                          markDirty()
                        }}
                        rows={5}
                        className="block w-full rounded-md border border-slate-200 px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                      />
                    </div>
                    <div>
                      <Label htmlFor="draft-validation">검증 방법</Label>
                      <textarea
                        id="draft-validation"
                        value={draftValidation}
                        onChange={(e) => {
                          setDraftValidation(e.target.value)
                          markDirty()
                        }}
                        rows={5}
                        className="block w-full rounded-md border border-slate-200 px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                      />
                    </div>
                  </div>
                  {saveDraft.isError && (
                    <p className="text-xs text-destructive">
                      저장 실패: {(saveDraft.error as any)?.response?.data?.message}
                    </p>
                  )}
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardHeader className="border-b border-slate-100 py-3">
                  <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    프롬프트 본문 (읽기 전용
                    {isPending && !isRequester
                      ? " — 수정은 요청자만, 리뷰어는 코멘트로 의견을 남기세요"
                      : " — 리뷰 완료됨"}
                    )
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                  {v ? (
                    <Markdown content={v.promptText} />
                  ) : (
                    <p className="text-sm text-slate-500">버전 없음</p>
                  )}
                </CardContent>
              </Card>
            )}
          </div>

          <div>
            <Card className="flex h-[600px] flex-col">
              <CardHeader className="border-b border-slate-100 py-3">
                <CardTitle className="flex items-center text-sm">
                  <MessageSquare className="mr-2 h-4 w-4" />
                  코멘트
                </CardTitle>
              </CardHeader>
              <CardContent className="flex-1 space-y-3 overflow-y-auto p-4">
                {!comments.data || comments.data.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-500">
                    아직 코멘트가 없습니다.
                  </p>
                ) : (
                  comments.data.map((c) => (
                    <div key={c.id} className="space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-slate-700">
                          {c.authorName}
                        </span>
                        <div className="flex items-center gap-2">
                          {c.resolved ? (
                            <span className="flex items-center text-[10px] text-emerald-600">
                              <CheckCircle2 className="mr-0.5 h-3 w-3" />
                              해결됨
                            </span>
                          ) : (
                            <button
                              onClick={() => resolveComment.mutate(c.id)}
                              className="text-[10px] text-slate-400 hover:text-emerald-600"
                            >
                              해결 표시
                            </button>
                          )}
                          <span className="text-[10px] text-slate-400">
                            {new Date(c.createdAt).toLocaleTimeString()}
                          </span>
                        </div>
                      </div>
                      <div
                        className={`rounded-lg p-3 text-sm ${
                          c.resolved
                            ? "bg-emerald-50 text-emerald-900"
                            : "bg-slate-100 text-slate-800"
                        }`}
                      >
                        {c.content}
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
              <div className="border-t border-slate-100 p-3">
                <div className="relative">
                  <textarea
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    onKeyDown={onCommentKeyDown}
                    placeholder="코멘트를 작성하세요 (Enter 로 등록 · Shift+Enter 로 줄바꿈)"
                    className="min-h-[80px] w-full rounded-md border border-slate-200 p-2 pr-10 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  />
                  <button
                    onClick={submitComment}
                    disabled={!comment.trim() || addComment.isPending}
                    className="absolute bottom-2 right-2 rounded-md bg-primary p-1.5 text-white hover:bg-primary/90 disabled:opacity-50"
                  >
                    <Send className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
