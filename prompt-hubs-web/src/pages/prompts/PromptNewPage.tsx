import { useNavigate, useParams } from "react-router-dom"
import { ChevronLeft } from "lucide-react"
import apiClient from "@/api/client"
import PrePromptingWizardForm from "@/components/PrePromptingWizardForm"

export default function PromptNewPage() {
  const { slug } = useParams()
  const navigate = useNavigate()

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
    await apiClient.post(`/w/${slug}/prompts`, {
      title: data.title,
      category: data.category,
      tags: data.tags,
      successCriteria: data.successCriteria,
      validationMethod: data.validationMethod,
      promptText: data.promptText,
      changeNote: data.changeNote,
      reviewerId: data.reviewerId,
    })
    navigate(`/w/${slug}/reviews`)
  }

  return (
    <div className="space-y-6">
      <nav>
        <button
          onClick={() => navigate(-1)}
          className="flex items-center text-sm text-slate-500 transition-colors hover:text-primary"
        >
          <ChevronLeft className="mr-1 h-4 w-4" />
          뒤로
        </button>
      </nav>

      <PrePromptingWizardForm
        slug={slug!}
        onSubmit={onSubmit}
        onCancel={() => navigate(-1)}
        heading="새 프롬프트 — Pre-prompting 위저드"
        subheading="작성 직후 리뷰 대기열에 올라가고, 승인된 후에만 라이브러리에 합쳐집니다."
        submitButtonText="리뷰 요청 보내기"
      />
    </div>
  )
}
