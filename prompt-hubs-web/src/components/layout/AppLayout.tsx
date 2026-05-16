import { Outlet, Navigate, useParams } from "react-router-dom"
import Sidebar from "./Sidebar"
import ErrorBoundary from "@/components/ErrorBoundary"
import { useWorkspaceStore } from "@/store/workspaceStore"
import { useQuery } from "@tanstack/react-query"
import apiClient from "@/api/client"
import { useEffect } from "react"
import { Loader2 } from "lucide-react"

export default function AppLayout() {
  const { slug } = useParams()
  const { setCurrentWorkspace } = useWorkspaceStore()

  const { data: workspace, isLoading, error } = useQuery({
    queryKey: ["workspace", slug],
    queryFn: async () => {
      const response = await apiClient.get(`/workspaces/${slug}`)
      return response.data
    },
    enabled: !!slug,
  })

  useEffect(() => {
    if (workspace) setCurrentWorkspace(workspace)
  }, [workspace, setCurrentWorkspace])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <div className="flex items-center gap-2 text-slate-600">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>워크스페이스를 불러오는 중...</span>
        </div>
      </div>
    )
  }
  if (error) return <Navigate to="/workspace/select" replace />

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar workspace={workspace} />
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-6xl px-8 py-8">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </div>
      </main>
    </div>
  )
}
