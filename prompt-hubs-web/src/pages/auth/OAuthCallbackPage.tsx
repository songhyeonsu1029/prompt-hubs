import { useEffect, useRef, useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import apiClient from "@/api/client"
import { useAuthStore } from "@/store/authStore"
import { AlertCircle, Loader2 } from "lucide-react"

export default function OAuthCallbackPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const setUser = useAuthStore((state) => state.setUser)
  const [error, setError] = useState<string | null>(null)
  const ran = useRef(false)

  useEffect(() => {
    if (ran.current) return
    ran.current = true

    const accessToken = params.get("accessToken")
    const refreshToken = params.get("refreshToken")

    if (!accessToken || !refreshToken) {
      setError("OAuth 응답에 토큰이 포함되지 않았습니다.")
      return
    }

    localStorage.setItem("accessToken", accessToken)
    localStorage.setItem("refreshToken", refreshToken)

    apiClient
      .get("/auth/me")
      .then((res) => {
        const account = res.data
        setUser({
          id: account.id,
          email: account.email,
          name: account.name,
          avatarUrl: account.avatarUrl,
        })
        navigate("/", { replace: true })
      })
      .catch((err) => {
        const message = err.response?.data?.message ?? "사용자 정보를 가져오지 못했습니다."
        localStorage.removeItem("accessToken")
        localStorage.removeItem("refreshToken")
        setError(message)
      })
  }, [params, navigate, setUser])

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-100 px-4">
        <div className="w-full max-w-md space-y-4 rounded-xl border border-slate-200/60 bg-white/80 p-6 shadow-xl backdrop-blur">
          <div className="flex items-start gap-2 text-destructive">
            <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0" />
            <div>
              <p className="font-medium">로그인에 실패했습니다</p>
              <p className="text-sm text-slate-600">{error}</p>
            </div>
          </div>
          <button
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
            onClick={() => navigate("/login", { replace: true })}
          >
            로그인 페이지로 돌아가기
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-100">
      <div className="flex flex-col items-center space-y-3 text-slate-700">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
        <p className="text-sm">Google 로그인 처리 중...</p>
      </div>
    </div>
  )
}
