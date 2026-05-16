import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import apiClient from "@/api/client"
import { useAuthStore } from "@/store/authStore"
import { useEffect, useState } from "react"
import GoogleLoginButton from "@/components/GoogleLoginButton"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  LogIn,
  Mail,
  Sparkles,
} from "lucide-react"

const loginSchema = z.object({
  email: z.string().min(1, "이메일을 입력해주세요").email("올바른 이메일 형식이 아닙니다"),
  password: z.string().min(1, "비밀번호를 입력해주세요"),
})

type LoginFormValues = z.infer<typeof loginSchema>

export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const setUser = useAuthStore((state) => state.setUser)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  useEffect(() => {
    const oauthError = searchParams.get("oauthError")
    if (oauthError) {
      setError(`소셜 로그인 실패: ${decodeURIComponent(oauthError)}`)
      searchParams.delete("oauthError")
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: "onTouched",
  })

  const onSubmit = async (data: LoginFormValues) => {
    setIsLoading(true)
    setError(null)
    try {
      const tokenRes = await apiClient.post("/auth/login", data)
      const { accessToken, refreshToken } = tokenRes.data

      localStorage.setItem("accessToken", accessToken)
      localStorage.setItem("refreshToken", refreshToken)

      const meRes = await apiClient.get("/auth/me")
      const account = meRes.data

      setUser({
        id: account.id,
        email: account.email,
        name: account.name,
        avatarUrl: account.avatarUrl,
      })

      navigate("/")
    } catch (err: any) {
      const status = err.response?.status
      const message = err.response?.data?.message

      if (status === 401 || status === 400) {
        setError(message || "이메일 또는 비밀번호가 올바르지 않습니다")
      } else if (err.code === "ERR_NETWORK") {
        setError("서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요")
      } else {
        setError(message || "로그인 중 오류가 발생했습니다")
      }

      localStorage.removeItem("accessToken")
      localStorage.removeItem("refreshToken")
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
        <div className="flex flex-col items-center space-y-2 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 shadow-lg shadow-blue-500/30">
            <Sparkles className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Prompt Hubs
          </h1>
          <p className="text-sm text-slate-600">
            팀의 프롬프트를 한 곳에서 관리하세요
          </p>
        </div>

        <Card className="border-slate-200/60 bg-white/80 shadow-xl shadow-slate-200/50 backdrop-blur">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">로그인</CardTitle>
            <CardDescription>
              계정 정보를 입력하고 워크스페이스로 이동하세요
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              {error && (
                <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="email">이메일</Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
                    autoFocus
                    placeholder="name@example.com"
                    className="pl-9"
                    aria-invalid={!!errors.email}
                    {...register("email")}
                  />
                </div>
                {errors.email && (
                  <p className="text-xs text-destructive">{errors.email.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">비밀번호</Label>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="••••••••"
                    className="pl-9 pr-10"
                    aria-invalid={!!errors.password}
                    {...register("password")}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 transition-colors hover:text-slate-600"
                    tabIndex={-1}
                    aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-xs text-destructive">{errors.password.message}</p>
                )}
              </div>

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    로그인 중...
                  </>
                ) : (
                  <>
                    <LogIn className="mr-2 h-4 w-4" />
                    로그인
                  </>
                )}
              </Button>
            </form>

            <div className="relative my-4">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t border-slate-200" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-white/80 px-2 text-slate-500">또는</span>
              </div>
            </div>

            <GoogleLoginButton disabled={isLoading} label="Google로 로그인" />
          </CardContent>
          <CardFooter className="flex justify-center border-t border-slate-100 pt-4">
            <p className="text-sm text-slate-600">
              아직 계정이 없으신가요?{" "}
              <Link
                to="/signup"
                className="font-medium text-primary hover:underline"
              >
                회원가입
              </Link>
            </p>
          </CardFooter>
        </Card>

        <p className="text-center text-xs text-slate-500">
          © {new Date().getFullYear()} Prompt Hubs. All rights reserved.
        </p>
      </div>
    </div>
  )
}
