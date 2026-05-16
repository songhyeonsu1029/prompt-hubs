import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import {
  ClipboardList,
  FileText,
  MessageSquare,
  Plus,
  Activity,
  Users,
  Loader2,
  ArrowRight,
  BookOpen,
} from "lucide-react"
import { useAuthStore } from "@/store/authStore"

interface ActivityItem {
  type: string
  title: string
  actor: string
  createdAt: string
}

interface DashboardResponse {
  totalPrompts: number
  totalLogs: number
  pendingReviews: number
  recentActivity: ActivityItem[]
}

interface UsageResponse {
  plan: string
  memberCount: number
  memberLimit: number
  promptCount: number
  promptLimit: number
  logRetentionDays: number
  versionLimit: number
}

function StatCard({
  label,
  value,
  icon: Icon,
  color,
  to,
}: {
  label: string
  value: number | string
  icon: React.ComponentType<{ className?: string }>
  color: string
  to: string
}) {
  return (
    <Link to={to}>
      <Card className="transition-all hover:border-primary hover:shadow-md">
        <CardContent className="flex items-center justify-between p-5">
          <div>
            <p className="text-sm font-medium text-slate-600">{label}</p>
            <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
          </div>
          <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${color}`}>
            <Icon className="h-6 w-6" />
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}

function relativeTime(iso: string): string {
  const date = new Date(iso)
  const now = Date.now()
  const diff = Math.floor((now - date.getTime()) / 1000)
  if (diff < 60) return "방금 전"
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)}일 전`
  return date.toLocaleDateString()
}

function activityIcon(type: string) {
  const t = type.toUpperCase()
  if (t.includes("PROMPT")) return ClipboardList
  if (t.includes("LOG")) return FileText
  if (t.includes("REVIEW")) return MessageSquare
  if (t.includes("DOC")) return BookOpen
  return Activity
}

export default function DashboardPage() {
  const { slug } = useParams()
  const user = useAuthStore((s) => s.user)

  const dashboard = useQuery<DashboardResponse>({
    queryKey: ["dashboard", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/dashboard`)
      return res.data
    },
  })

  const usage = useQuery<UsageResponse>({
    queryKey: ["usage", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/workspaces/${slug}/usage`)
      return res.data
    },
  })

  if (dashboard.isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-slate-500">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        대시보드 로딩 중...
      </div>
    )
  }

  const data = dashboard.data
  const activity = data?.recentActivity ?? []

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            안녕하세요, {user?.name}님 👋
          </h1>
          <p className="mt-1 text-slate-600">
            워크스페이스 한 눈에 보기와 최근 활동입니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link to={`/w/${slug}/logs/new`}>
              <Plus className="mr-1 h-4 w-4" />
              로그 추가
            </Link>
          </Button>
          <Button asChild>
            <Link to={`/w/${slug}/prompts/new`}>
              <Plus className="mr-1 h-4 w-4" />
              프롬프트 생성
            </Link>
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="총 프롬프트"
          value={data?.totalPrompts ?? 0}
          icon={ClipboardList}
          color="bg-blue-100 text-blue-600"
          to={`/w/${slug}/prompts`}
        />
        <StatCard
          label="총 로그"
          value={data?.totalLogs ?? 0}
          icon={FileText}
          color="bg-emerald-100 text-emerald-600"
          to={`/w/${slug}/logs`}
        />
        <StatCard
          label="대기 중인 리뷰"
          value={data?.pendingReviews ?? 0}
          icon={MessageSquare}
          color="bg-amber-100 text-amber-600"
          to={`/w/${slug}/prompts?status=IN_REVIEW`}
        />
        <StatCard
          label="팀 멤버"
          value={usage.data?.memberCount ?? "—"}
          icon={Users}
          color="bg-violet-100 text-violet-600"
          to={`/w/${slug}/settings`}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="border-b border-slate-100">
            <CardTitle className="text-base">최근 활동</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {activity.length === 0 ? (
              <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
                <Activity className="mb-2 h-8 w-8 text-slate-300" />
                <p className="text-sm text-slate-500">
                  아직 활동이 없습니다. 프롬프트나 로그를 만들어보세요.
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {activity.map((item, idx) => {
                  const Icon = activityIcon(item.type)
                  return (
                    <li key={idx} className="flex items-start gap-3 px-5 py-4">
                      <div className="mt-0.5 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">
                          {item.title}
                        </p>
                        <p className="text-xs text-slate-500">
                          {item.actor} · {relativeTime(item.createdAt)}
                        </p>
                      </div>
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                        {item.type}
                      </span>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b border-slate-100">
            <CardTitle className="text-base">플랜 사용량</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            {usage.isLoading ? (
              <div className="flex items-center justify-center py-8 text-slate-400">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            ) : usage.data ? (
              <>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-600">현재 플랜</span>
                  <span className="rounded-md bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
                    {usage.data.plan}
                  </span>
                </div>
                <UsageBar
                  label="멤버"
                  current={usage.data.memberCount}
                  limit={usage.data.memberLimit}
                />
                <UsageBar
                  label="프롬프트"
                  current={usage.data.promptCount}
                  limit={usage.data.promptLimit}
                />
                <div className="border-t border-slate-100 pt-3 text-xs text-slate-500">
                  <p>로그 보관: {usage.data.logRetentionDays}일</p>
                  <p>버전 보관: {usage.data.versionLimit}개</p>
                </div>
              </>
            ) : (
              <p className="text-sm text-slate-500">사용량 정보를 가져오지 못했습니다.</p>
            )}
            <Button asChild variant="outline" size="sm" className="w-full">
              <Link to={`/w/${slug}/settings`}>
                설정 이동
                <ArrowRight className="ml-2 h-3 w-3" />
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function UsageBar({
  label,
  current,
  limit,
}: {
  label: string
  current: number
  limit: number
}) {
  const pct = limit > 0 ? Math.min(100, (current / limit) * 100) : 0
  const color = pct >= 90 ? "bg-red-500" : pct >= 70 ? "bg-amber-500" : "bg-primary"
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="text-slate-600">{label}</span>
        <span className="font-medium text-slate-700">
          {current} / {limit > 0 ? limit : "∞"}
        </span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full ${color} transition-all`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
