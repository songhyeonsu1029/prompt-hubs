import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import { Link, useNavigate } from "react-router-dom"
import apiClient from "@/api/client"
import { useAuthStore } from "@/store/authStore"
import { useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import GoogleLoginButton from "@/components/GoogleLoginButton"
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
  Check,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  Mail,
  Sparkles,
  User,
  UserPlus,
} from "lucide-react"

const signupSchema = z
  .object({
    name: z
      .string()
      .min(2, "이름은 2자 이상이어야 합니다")
      .max(50, "이름은 50자 이하여야 합니다"),
    email: z
      .string()
      .min(1, "이메일을 입력해주세요")
      .email("올바른 이메일 형식이 아닙니다"),
    password: z
      .string()
      .min(8, "비밀번호는 8자 이상이어야 합니다")
      .max(100, "비밀번호가 너무 깁니다"),
    confirmPassword: z.string().min(1, "비밀번호를 다시 입력해주세요"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["confirmPassword"],
  })

type SignupFormValues = z.infer<typeof signupSchema>

function getPasswordStrength(password: string): {
  score: number
  label: string
  color: string
} {
  if (!password) return { score: 0, label: "", color: "" }

  let score = 0
  if (password.length >= 8) score++
  if (password.length >= 12) score++
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++

  if (score <= 1) return { score: 1, label: "약함", color: "bg-red-500" }
  if (score <= 3) return { score: 2, label: "보통", color: "bg-amber-500" }
  return { score: 3, label: "강함", color: "bg-emerald-500" }
}

export default function SignupPage() {
  const navigate = useNavigate()
  const setUser = useAuthStore((state) => state.setUser)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    mode: "onTouched",
  })

  const password = watch("password") || ""
  const strength = useMemo(() => getPasswordStrength(password), [password])

  const onSubmit = async (data: SignupFormValues) => {
    setIsLoading(true)
    setError(null)
    try {
      const tokenRes = await apiClient.post("/auth/signup", {
        name: data.name,
        email: data.email,
        password: data.password,
      })
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
      const data = err.response?.data
      const message = data?.message

      if (status === 409 || message?.toLowerCase().includes("already")) {
        setError("이미 사용 중인 이메일입니다")
      } else if (status === 400) {
        setError(message || "입력 정보를 확인해주세요")
      } else if (err.code === "ERR_NETWORK") {
        setError("서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요")
      } else {
        setError(message || "회원가입 중 오류가 발생했습니다")
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
            지금 가입하고 팀의 프롬프트를 한 곳에서 관리하세요
          </p>
        </div>

        <Card className="border-slate-200/60 bg-white/80 shadow-xl shadow-slate-200/50 backdrop-blur">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">회원가입</CardTitle>
            <CardDescription>
              새 계정을 만들고 워크스페이스를 시작해보세요
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
                <Label htmlFor="name">이름</Label>
                <div className="relative">
                  <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="name"
                    type="text"
                    autoComplete="name"
                    autoFocus
                    placeholder="홍길동"
                    className="pl-9"
                    aria-invalid={!!errors.name}
                    {...register("name")}
                  />
                </div>
                {errors.name && (
                  <p className="text-xs text-destructive">{errors.name.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="email">이메일</Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
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
                    autoComplete="new-password"
                    placeholder="8자 이상 입력"
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
                {password && !errors.password && (
                  <div className="space-y-1">
                    <div className="flex h-1 gap-1">
                      {[1, 2, 3].map((i) => (
                        <div
                          key={i}
                          className={`flex-1 rounded-full transition-colors ${
                            i <= strength.score ? strength.color : "bg-slate-200"
                          }`}
                        />
                      ))}
                    </div>
                    {strength.label && (
                      <p className="text-xs text-slate-500">
                        비밀번호 강도:{" "}
                        <span className="font-medium text-slate-700">
                          {strength.label}
                        </span>
                      </p>
                    )}
                  </div>
                )}
                {errors.password && (
                  <p className="text-xs text-destructive">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="confirmPassword">비밀번호 확인</Label>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="confirmPassword"
                    type={showConfirmPassword ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="비밀번호 다시 입력"
                    className="pl-9 pr-10"
                    aria-invalid={!!errors.confirmPassword}
                    {...register("confirmPassword")}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 transition-colors hover:text-slate-600"
                    tabIndex={-1}
                    aria-label={
                      showConfirmPassword ? "비밀번호 숨기기" : "비밀번호 표시"
                    }
                  >
                    {showConfirmPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
                {errors.confirmPassword && (
                  <p className="text-xs text-destructive">
                    {errors.confirmPassword.message}
                  </p>
                )}
              </div>

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    가입 처리 중...
                  </>
                ) : (
                  <>
                    <UserPlus className="mr-2 h-4 w-4" />
                    회원가입
                  </>
                )}
              </Button>

              <p className="flex items-start gap-1.5 pt-1 text-xs text-slate-500">
                <Check className="mt-0.5 h-3 w-3 flex-shrink-0 text-emerald-500" />
                <span>
                  가입을 진행하면 Prompt Hubs의 서비스 이용을 시작합니다.
                </span>
              </p>
            </form>

            <div className="relative my-4">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t border-slate-200" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-white/80 px-2 text-slate-500">또는</span>
              </div>
            </div>

            <GoogleLoginButton disabled={isLoading} label="Google 계정으로 가입" />
          </CardContent>
          <CardFooter className="flex justify-center border-t border-slate-100 pt-4">
            <p className="text-sm text-slate-600">
              이미 계정이 있으신가요?{" "}
              <Link
                to="/login"
                className="font-medium text-primary hover:underline"
              >
                로그인
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
