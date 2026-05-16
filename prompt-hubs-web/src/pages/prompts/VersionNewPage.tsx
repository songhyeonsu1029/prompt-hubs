import { useQuery } from "@tanstack/react-query"
import { useNavigate, useParams } from "react-router-dom"
import { ChevronLeft, Loader2 } from "lucide-react"
import apiClient from "@/api/client"
import PrePromptingWizardForm from "@/components/PrePromptingWizardForm"

interface PromptDetail {
  id: string
  title: string
  category: string | null
  tags: string[] | null
  currentVersion: {
    id: string
    versionNumber: string
    promptText: string
    successCriteria: string
    validationMethod: string
  } | null
}

export default function VersionNewPage() {
  const { slug, promptId } = useParams()
  const navigate = useNavigate()

  const { data: prompt, isLoading } = useQuery<PromptDetail>({
    queryKey: ["prompt", slug, promptId],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/prompts/${promptId}`)
      return res.data
    },
  })

  const onSubmit = async (data: {
    title: string
    category?: string
    tags: string[]
    successCriteria: string
    validationMethod: string
    promptText: string
    changeNote?: string
    reviewerId: string
  }) => {
    // Versions don't carry title/category/tags — only body fields go up.
    await apiClient.post(`/w/${slug}/prompts/${promptId}/versions`, {
      promptText: data.promptText,
      successCriteria: data.successCriteria,
      validationMethod: data.validationMethod,
      changeNote: data.changeNote,
      reviewerId: data.reviewerId,
    })
    navigate(`/w/${slug}/reviews`)
  }

  if (isLoading || !prompt) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-slate-500">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        프롬프트 불러오는 중...
      </div>
    )
  }

  const v = prompt.currentVersion
  const defaults = {
    title: prompt.title,
    category: prompt.category ?? "",
    tags: (prompt.tags ?? []).join(", "),
    promptText: v?.promptText ?? "",
    successCriteria: v?.successCriteria ?? "",
    validationMethod: v?.validationMethod ?? "",
  }

  return (
    <div className="space-y-6">
      <nav>
        <button
          onClick={() => navigate(`/w/${slug}/prompts/${promptId}`)}
          className="flex items-center text-sm text-slate-500 transition-colors hover:text-primary"
        >
          <ChevronLeft className="mr-1 h-4 w-4" />
          프롬프트로 돌아가기
        </button>
      </nav>

      <PrePromptingWizardForm
        slug={slug!}
        defaults={defaults}
        titleReadOnly
        onSubmit={onSubmit}
        onCancel={() => navigate(`/w/${slug}/prompts/${promptId}`)}
        heading={`새 버전 — ${prompt.title}`}
        subheading={`현재 v${v?.versionNumber ?? "—"} 기반으로 본문/성공 기준/검증 방법을 수정합니다. 저장 즉시 새 버전이 만들어지고 리뷰 대기열에 올라갑니다.`}
        submitButtonText="새 버전 + 리뷰 요청 보내기"
      />
    </div>
  )
}
