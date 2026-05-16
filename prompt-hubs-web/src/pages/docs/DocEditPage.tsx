import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import { Link, useNavigate, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { AlertCircle, ChevronLeft, Loader2, Save, Trash2 } from "lucide-react"

const docSchema = z.object({
  title: z.string().min(1, "제목을 입력해주세요"),
  content: z.string().min(1, "내용을 입력해주세요"),
  category: z.string().optional(),
})

type DocFormValues = z.infer<typeof docSchema>

export default function DocEditPage() {
  const { slug, docId } = useParams()
  const isEdit = !!docId
  const navigate = useNavigate()
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: existingDoc, isLoading } = useQuery({
    queryKey: ["doc", slug, docId],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/docs/${docId}`)
      return res.data
    },
    enabled: isEdit,
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<DocFormValues>({
    resolver: zodResolver(docSchema),
    defaultValues: { title: "", content: "", category: "" },
  })

  useEffect(() => {
    if (existingDoc) {
      reset({
        title: existingDoc.title ?? "",
        content: existingDoc.content ?? "",
        category: existingDoc.category ?? "",
      })
    }
  }, [existingDoc, reset])

  const onSubmit = async (data: DocFormValues) => {
    setIsSaving(true)
    setError(null)
    try {
      if (isEdit) {
        await apiClient.put(`/w/${slug}/docs/${docId}`, data)
      } else {
        await apiClient.post(`/w/${slug}/docs`, data)
      }
      navigate(`/w/${slug}/docs`)
    } catch (err: any) {
      setError(err.response?.data?.message || "문서 저장에 실패했습니다")
    } finally {
      setIsSaving(false)
    }
  }

  const onDelete = async () => {
    if (!isEdit) return
    if (!confirm("이 문서를 삭제하시겠습니까? 되돌릴 수 없습니다.")) return
    setIsDeleting(true)
    try {
      await apiClient.delete(`/w/${slug}/docs/${docId}`)
      navigate(`/w/${slug}/docs`)
    } catch (err: any) {
      setError(err.response?.data?.message || "문서 삭제에 실패했습니다")
      setIsDeleting(false)
    }
  }

  if (isEdit && isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-slate-500">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        문서를 불러오는 중...
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <nav className="flex items-center text-sm">
        <Link
          to={`/w/${slug}/docs`}
          className="flex items-center text-slate-500 transition-colors hover:text-primary"
        >
          <ChevronLeft className="mr-1 h-4 w-4" />
          문서 목록
        </Link>
      </nav>

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          {isEdit ? "문서 수정" : "새 문서"}
        </h1>
        {isEdit && (
          <Button
            type="button"
            variant="outline"
            onClick={onDelete}
            disabled={isDeleting}
            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
          >
            <Trash2 className="mr-2 h-4 w-4" />
            삭제
          </Button>
        )}
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {error && (
          <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-4 lg:col-span-2">
            <Card>
              <CardContent className="space-y-4 p-6">
                <div>
                  <Label htmlFor="title">제목</Label>
                  <input
                    id="title"
                    {...register("title")}
                    className="mt-1 block w-full border-b border-slate-200 py-2 text-2xl font-bold focus:border-primary focus:outline-none"
                    placeholder="문서 제목"
                  />
                  {errors.title && (
                    <p className="mt-1 text-xs text-destructive">
                      {errors.title.message}
                    </p>
                  )}
                </div>

                <div>
                  <Label htmlFor="content">본문 (Markdown)</Label>
                  <textarea
                    id="content"
                    {...register("content")}
                    rows={22}
                    className="mt-1 block w-full rounded-md border border-slate-200 px-3 py-2 font-mono text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                    placeholder="# 제목&#10;&#10;본문을 Markdown으로 작성하세요."
                  />
                  {errors.content && (
                    <p className="mt-1 text-xs text-destructive">
                      {errors.content.message}
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader className="border-b border-slate-100">
                <CardTitle className="text-sm">메타데이터</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4 p-5">
                <div>
                  <Label htmlFor="category">카테고리</Label>
                  <Input
                    id="category"
                    {...register("category")}
                    placeholder="예: Coding Convention"
                  />
                </div>

                <div className="space-y-2 pt-2">
                  <Button
                    type="submit"
                    className="w-full"
                    disabled={isSaving || (isEdit && !isDirty)}
                  >
                    {isSaving ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        저장 중...
                      </>
                    ) : (
                      <>
                        <Save className="mr-2 h-4 w-4" />
                        {isEdit ? "변경 사항 저장" : "문서 생성"}
                      </>
                    )}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full"
                    onClick={() => navigate(-1)}
                  >
                    취소
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card className="border-blue-200 bg-blue-50/60">
              <CardContent className="p-5 text-sm text-blue-900">
                <h4 className="mb-2 font-bold">Markdown 단축 표기</h4>
                <ul className="list-inside list-disc space-y-1 opacity-80">
                  <li># 큰 제목, ## 중간 제목</li>
                  <li>* 또는 - 글머리표</li>
                  <li>``` 코드 블록</li>
                  <li>[텍스트](URL) 링크</li>
                </ul>
              </CardContent>
            </Card>
          </div>
        </div>
      </form>
    </div>
  )
}
