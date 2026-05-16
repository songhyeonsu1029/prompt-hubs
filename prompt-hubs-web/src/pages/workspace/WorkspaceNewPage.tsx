import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import { Link, useNavigate } from "react-router-dom"
import apiClient from "@/api/client"
import { useEffect, useState } from "react"
import {
  AlertCircle,
  Building2,
  ChevronLeft,
  Loader2,
  Sparkles,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

const workspaceSchema = z.object({
  name: z
    .string()
    .min(2, "이름은 2자 이상이어야 합니다")
    .max(60, "이름이 너무 깁니다"),
  slug: z
    .string()
    .min(2, "slug는 2자 이상이어야 합니다")
    .max(40, "slug가 너무 깁니다")
    .regex(
      /^[a-z0-9-]+$/,
      "소문자, 숫자, 하이픈(-)만 사용할 수 있습니다"
    ),
})

type WorkspaceFormValues = z.infer<typeof workspaceSchema>

function slugify(input: string): string {
  return input
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .slice(0, 40)
}

export default function WorkspaceNewPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, dirtyFields },
  } = useForm<WorkspaceFormValues>({
    resolver: zodResolver(workspaceSchema),
    mode: "onTouched",
    defaultValues: { name: "", slug: "" },
  })

  const name = watch("name")
  useEffect(() => {
    if (!dirtyFields.slug) {
      setValue("slug", slugify(name || ""), { shouldValidate: false })
    }
  }, [name, dirtyFields.slug, setValue])

  const onSubmit = async (data: WorkspaceFormValues) => {
    setIsLoading(true)
    setError(null)
    try {
      const res = await apiClient.post("/workspaces", data)
      navigate(`/w/${res.data.slug}`)
    } catch (err: any) {
      const status = err.response?.status
      const message = err.response?.data?.message
      if (status === 409) {
        setError("이미 사용 중인 slug 입니다")
      } else {
        setError(message || "워크스페이스 생성에 실패했습니다")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-100 px-4 py-12">
      <div className="absolute inset-0 -z-10 overflow-hidden">
        <div className="absolute -top-40 -right-32 h-96 w-96 rounded-full bg-blue-300/30 blur-3xl" />
        <div className="absolute -bottom-40 -left-32 h-96 w-96 rounded-full bg-indigo-300/30 blur-3xl" />
      </div>

      <div className="w-full max-w-md space-y-6">
        <Link
          to="/workspace/select"
          className="flex items-center text-sm text-slate-600 transition-colors hover:text-primary"
        >
          <ChevronLeft className="mr-1 h-4 w-4" />
          돌아가기
        </Link>

        <div className="text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 shadow-lg shadow-blue-500/30">
            <Sparkles className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            새 워크스페이스
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            팀이 함께 프롬프트를 관리할 공간을 만듭니다.
          </p>
        </div>

        <Card className="border-slate-200/60 bg-white/80 shadow-xl backdrop-blur">
          <CardHeader>
            <CardTitle className="flex items-center text-base">
              <Building2 className="mr-2 h-4 w-4" />
              워크스페이스 정보
            </CardTitle>
            <CardDescription>이름과 URL을 입력하세요.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              {error && (
                <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="name">이름</Label>
                <Input
                  id="name"
                  {...register("name")}
                  placeholder="Acme Corp"
                  autoFocus
                />
                {errors.name && (
                  <p className="text-xs text-destructive">
                    {errors.name.message}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="slug">URL Slug</Label>
                <div className="flex items-center">
                  <span className="rounded-l-md border border-r-0 border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">
                    /w/
                  </span>
                  <Input
                    id="slug"
                    {...register("slug")}
                    placeholder="acme-corp"
                    className="rounded-l-none"
                  />
                </div>
                {errors.slug && (
                  <p className="text-xs text-destructive">
                    {errors.slug.message}
                  </p>
                )}
                <p className="text-xs text-slate-500">
                  소문자·숫자·하이픈만 사용할 수 있습니다.
                </p>
              </div>

              <div className="flex gap-2 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1"
                  onClick={() => navigate(-1)}
                >
                  취소
                </Button>
                <Button type="submit" className="flex-1" disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      생성 중...
                    </>
                  ) : (
                    "생성"
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
