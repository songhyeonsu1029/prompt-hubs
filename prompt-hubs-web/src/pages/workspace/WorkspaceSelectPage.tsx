import { useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "react-router-dom"
import apiClient from "@/api/client"
import {
  Building2,
  LogOut,
  PlusCircle,
  Sparkles,
  Loader2,
} from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import { Button } from "@/components/ui/button"

interface Workspace {
  id: string
  name: string
  slug: string
  plan: string
  myRole: string
}

export default function WorkspaceSelectPage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  const { data: workspaces, isLoading } = useQuery<Workspace[]>({
    queryKey: ["workspaces"],
    queryFn: async () => {
      const res = await apiClient.get("/workspaces")
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const handleLogout = async () => {
    try {
      await apiClient.post("/auth/logout")
    } catch {
      /* ignore */
    }
    logout()
    navigate("/login", { replace: true })
  }

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-100 px-4 py-12">
      <div className="absolute inset-0 -z-10 overflow-hidden">
        <div className="absolute -top-40 -right-32 h-96 w-96 rounded-full bg-blue-300/30 blur-3xl" />
        <div className="absolute -bottom-40 -left-32 h-96 w-96 rounded-full bg-indigo-300/30 blur-3xl" />
      </div>

      <div className="absolute right-6 top-6 flex items-center gap-3 text-sm">
        <span className="text-slate-600">
          {user?.name} <span className="text-slate-400">({user?.email})</span>
        </span>
        <button
          onClick={handleLogout}
          className="flex items-center rounded-md border border-slate-200 bg-white/80 px-3 py-1.5 text-slate-700 transition-colors hover:bg-white"
        >
          <LogOut className="mr-1 h-4 w-4" />
          로그아웃
        </button>
      </div>

      <div className="w-full max-w-2xl space-y-8">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 shadow-lg shadow-blue-500/30">
            <Sparkles className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            워크스페이스를 선택하세요
          </h1>
          <p className="mt-1 text-slate-600">
            계속할 워크스페이스를 고르거나 새로 만드세요.
          </p>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-12 text-slate-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            워크스페이스를 불러오는 중...
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {workspaces?.map((ws) => (
              <button
                key={ws.id}
                onClick={() => navigate(`/w/${ws.slug}`)}
                className="group flex items-center gap-3 rounded-xl border border-slate-200 bg-white/80 p-5 text-left shadow-sm backdrop-blur transition-all hover:border-primary hover:bg-white hover:shadow-md"
              >
                <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-white">
                  <Building2 className="h-6 w-6" />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-semibold text-slate-900">
                    {ws.name}
                  </h3>
                  <p className="truncate text-sm text-slate-500">/{ws.slug}</p>
                  <div className="mt-1 flex items-center gap-1.5">
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-600">
                      {ws.plan}
                    </span>
                    <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-primary">
                      {ws.myRole}
                    </span>
                  </div>
                </div>
              </button>
            ))}

            <Link
              to="/workspace/new"
              className="flex items-center gap-3 rounded-xl border-2 border-dashed border-slate-300 bg-white/40 p-5 transition-all hover:border-primary hover:bg-white"
            >
              <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                <PlusCircle className="h-6 w-6" />
              </div>
              <div>
                <h3 className="font-semibold text-slate-900">
                  새 워크스페이스
                </h3>
                <p className="text-sm text-slate-500">팀을 위한 공간 만들기</p>
              </div>
            </Link>
          </div>
        )}

        {workspaces?.length === 0 && !isLoading && (
          <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
            <p className="mb-4 text-slate-600">
              아직 워크스페이스가 없습니다. 첫 워크스페이스를 만들어보세요.
            </p>
            <Button asChild>
              <Link to="/workspace/new">
                <PlusCircle className="mr-2 h-4 w-4" />
                워크스페이스 만들기
              </Link>
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
