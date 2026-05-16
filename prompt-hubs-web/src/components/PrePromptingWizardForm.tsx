import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useQuery } from "@tanstack/react-query"
import * as z from "zod"
import apiClient from "@/api/client"
import { CheckCircle2, Circle, Loader2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent } from "@/components/ui/card"

export const wizardSchema = z.object({
  title: z.string().min(1, "제목을 입력해주세요"),
  category: z.string().optional(),
  tags: z.string().optional(),
  successCriteria: z.string().min(10, "성공 기준은 10자 이상이어야 합니다"),
  validationMethod: z.string().min(10, "검증 방법은 10자 이상이어야 합니다"),
  promptText: z.string().min(1, "프롬프트 본문을 입력해주세요"),
  changeNote: z.string().optional(),
  reviewerId: z.string().min(1, "리뷰어를 선택해주세요"),
})

export type WizardValues = z.infer<typeof wizardSchema>

export interface PrePromptingSubmitPayload {
  title: string
  category?: string
  tags: string[]
  successCriteria: string
  validationMethod: string
  promptText: string
  changeNote?: string
  reviewerId: string
}

interface Member {
  id: string
  accountId: string
  name: string
  email: string
}

interface Props {
  slug: string
  defaults?: Partial<WizardValues>
  onSubmit: (data: PrePromptingSubmitPayload) => Promise<void>
  onCancel: () => void
  submitButtonText?: string
  heading?: string
  subheading?: string
  /** When true, title/category are shown but locked (used for new-version wizard). */
  titleReadOnly?: boolean
}

const steps = [
  { id: 1, name: "정의", description: "성공이란 무엇인가?" },
  { id: 2, name: "검증", description: "어떻게 테스트할까?" },
  { id: 3, name: "프롬프트 + 리뷰어", description: "초안 + 리뷰 대상" },
]

export default function PrePromptingWizardForm({
  slug,
  defaults,
  onSubmit,
  onCancel,
  submitButtonText = "리뷰 요청 생성",
  heading = "Pre-prompting 위저드",
  subheading = "리뷰를 거친 프롬프트만 라이브러리에 합쳐집니다.",
  titleReadOnly = false,
}: Props) {
  const [currentStep, setCurrentStep] = useState(1)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const members = useQuery<Member[]>({
    queryKey: ["members", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/members`)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const {
    register,
    handleSubmit,
    trigger,
    formState: { errors },
  } = useForm<WizardValues>({
    resolver: zodResolver(wizardSchema),
    mode: "onTouched",
    defaultValues: {
      title: defaults?.title ?? "",
      category: defaults?.category ?? "",
      tags: defaults?.tags ?? "",
      successCriteria: defaults?.successCriteria ?? "",
      validationMethod: defaults?.validationMethod ?? "",
      promptText: defaults?.promptText ?? "",
      changeNote: defaults?.changeNote ?? "",
      reviewerId: defaults?.reviewerId ?? "",
    },
  })

  const nextStep = async () => {
    let fields: (keyof WizardValues)[] = []
    if (currentStep === 1) fields = ["title", "successCriteria"]
    if (currentStep === 2) fields = ["validationMethod"]
    const ok = await trigger(fields)
    if (ok) setCurrentStep((s) => s + 1)
  }

  const onFormSubmit = async (data: WizardValues) => {
    setIsLoading(true)
    setError(null)
    try {
      await onSubmit({
        title: data.title,
        category: data.category || undefined,
        tags: data.tags
          ? data.tags
              .split(",")
              .map((t) => t.trim())
              .filter(Boolean)
          : [],
        successCriteria: data.successCriteria,
        validationMethod: data.validationMethod,
        promptText: data.promptText,
        changeNote: data.changeNote || undefined,
        reviewerId: data.reviewerId,
      })
    } catch (err: any) {
      setError(err.response?.data?.message || "요청에 실패했습니다.")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">{heading}</h1>
        <p className="mt-2 text-slate-600">{subheading}</p>
      </div>

      <nav aria-label="Progress">
        <ol className="flex items-center justify-center gap-6">
          {steps.map((step, idx) => (
            <li key={step.id} className="flex items-center gap-3">
              {currentStep > step.id ? (
                <CheckCircle2 className="h-6 w-6 text-primary" />
              ) : currentStep === step.id ? (
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">
                  {step.id}
                </div>
              ) : (
                <Circle className="h-6 w-6 text-slate-300" />
              )}
              <div className="hidden sm:block">
                <div
                  className={cn(
                    "text-sm font-medium",
                    currentStep === step.id ? "text-primary" : "text-slate-500"
                  )}
                >
                  {step.name}
                </div>
                <div className="text-xs text-slate-400">{step.description}</div>
              </div>
              {idx < steps.length - 1 && (
                <div className="hidden h-px w-8 bg-slate-200 sm:block" />
              )}
            </li>
          ))}
        </ol>
      </nav>

      <Card>
        <CardContent className="p-8">
          <form onSubmit={handleSubmit(onFormSubmit)} className="space-y-6">
            {error && (
              <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            {currentStep === 1 && (
              <div className="space-y-5">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <Label htmlFor="title">제목</Label>
                    <Input
                      id="title"
                      {...register("title")}
                      placeholder="예: Code Reviewer"
                      readOnly={titleReadOnly}
                      className={titleReadOnly ? "bg-slate-50" : undefined}
                    />
                    {titleReadOnly && (
                      <p className="mt-1 text-[11px] text-slate-500">
                        프롬프트 자체의 이름은 새 버전에서 변경되지 않습니다.
                      </p>
                    )}
                    {errors.title && (
                      <p className="mt-1 text-xs text-destructive">{errors.title.message}</p>
                    )}
                  </div>
                  <div>
                    <Label htmlFor="category">카테고리 (선택)</Label>
                    <Input
                      id="category"
                      {...register("category")}
                      placeholder="예: Engineering"
                      readOnly={titleReadOnly}
                      className={titleReadOnly ? "bg-slate-50" : undefined}
                    />
                  </div>
                </div>

                <div>
                  <Label htmlFor="tags">태그 (콤마 구분, 선택)</Label>
                  <Input id="tags" {...register("tags")} placeholder="review, security, coding" />
                </div>

                <div>
                  <Label htmlFor="successCriteria">성공 기준</Label>
                  <p className="mb-2 text-xs text-slate-500">
                    이 프롬프트가 달성해야 할 구체적인 결과는 무엇인가요?
                  </p>
                  <textarea
                    id="successCriteria"
                    {...register("successCriteria")}
                    rows={5}
                    className="block w-full rounded-md border border-slate-200 px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                    placeholder="예: 코드의 보안 취약점을 모두 찾아내고 JSON 형식으로 수정안을 제시한다..."
                  />
                  {errors.successCriteria && (
                    <p className="mt-1 text-xs text-destructive">
                      {errors.successCriteria.message}
                    </p>
                  )}
                </div>
              </div>
            )}

            {currentStep === 2 && (
              <div>
                <Label htmlFor="validationMethod">검증 방법</Label>
                <p className="mb-2 text-xs text-slate-500">
                  결과가 성공 기준을 만족하는지 어떻게 확인할 건가요?
                </p>
                <textarea
                  id="validationMethod"
                  {...register("validationMethod")}
                  rows={6}
                  className="block w-full rounded-md border border-slate-200 px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  placeholder="예: 알려진 SQLi 취약점이 있는 코드 스니펫을 입력하고 AI가 잡아내는지 검사한다..."
                />
                {errors.validationMethod && (
                  <p className="mt-1 text-xs text-destructive">
                    {errors.validationMethod.message}
                  </p>
                )}
              </div>
            )}

            {currentStep === 3 && (
              <div className="space-y-5">
                <div>
                  <Label htmlFor="promptText">프롬프트 본문</Label>
                  <p className="mb-2 text-xs text-slate-500">AI에 실제 전달될 지시문입니다.</p>
                  <textarea
                    id="promptText"
                    {...register("promptText")}
                    rows={12}
                    className="block w-full rounded-md border border-slate-200 px-3 py-2 font-mono text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                    placeholder="당신은 시니어 소프트웨어 엔지니어입니다. 다음 코드의 보안 취약점을 검토하세요..."
                  />
                  {errors.promptText && (
                    <p className="mt-1 text-xs text-destructive">{errors.promptText.message}</p>
                  )}
                </div>

                <div>
                  <Label htmlFor="changeNote">변경 노트 (선택)</Label>
                  <Input
                    id="changeNote"
                    {...register("changeNote")}
                    placeholder="이 버전의 변경 사항을 간단히 설명"
                  />
                </div>

                <div>
                  <Label htmlFor="reviewerId">리뷰어</Label>
                  <p className="mb-2 text-xs text-slate-500">
                    이 프롬프트의 리뷰를 맡을 워크스페이스 멤버를 선택하세요.
                  </p>
                  <select
                    id="reviewerId"
                    {...register("reviewerId")}
                    className="block h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  >
                    <option value="">— 선택 —</option>
                    {(members.data ?? []).map((m) => (
                      <option key={m.accountId} value={m.accountId}>
                        {m.name} ({m.email})
                      </option>
                    ))}
                  </select>
                  {errors.reviewerId && (
                    <p className="mt-1 text-xs text-destructive">{errors.reviewerId.message}</p>
                  )}
                </div>
              </div>
            )}

            <div className="mt-8 flex justify-between border-t border-slate-100 pt-6">
              <Button
                type="button"
                variant="outline"
                onClick={currentStep === 1 ? onCancel : () => setCurrentStep((s) => s - 1)}
              >
                {currentStep === 1 ? "취소" : "이전"}
              </Button>
              {currentStep < 3 ? (
                <Button type="button" onClick={nextStep}>
                  계속
                </Button>
              ) : (
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      처리 중...
                    </>
                  ) : (
                    submitButtonText
                  )}
                </Button>
              )}
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
