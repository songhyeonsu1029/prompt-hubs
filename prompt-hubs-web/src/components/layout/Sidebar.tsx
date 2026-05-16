import { Link, NavLink, useNavigate, useParams } from "react-router-dom"
import {
  LayoutDashboard,
  ClipboardList,
  FileText,
  BookOpen,
  CheckSquare,
  Settings,
  LogOut,
  Building2,
  ChevronsUpDown,
  Sparkles,
} from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import apiClient from "@/api/client"
import { cn } from "@/lib/utils"

interface SidebarProps {
  workspace?: { name?: string; slug?: string; plan?: string }
}

const navItems = [
  { name: "Dashboard", href: "", icon: LayoutDashboard, end: true },
  { name: "Prompts", href: "/prompts", icon: ClipboardList },
  { name: "Reviews", href: "/reviews", icon: CheckSquare },
  { name: "Logs", href: "/logs", icon: FileText },
  { name: "Docs", href: "/docs", icon: BookOpen },
  { name: "Settings", href: "/settings", icon: Settings },
]

export default function Sidebar({ workspace }: SidebarProps) {
  const { slug } = useParams()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

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
    <aside className="flex h-full w-64 flex-col border-r border-slate-200 bg-white">
      <div className="flex h-16 items-center border-b border-slate-100 px-5">
        <Link to="/" className="flex items-center gap-2 font-bold">
          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-gradient-to-br from-blue-500 to-indigo-600">
            <Sparkles className="h-4 w-4 text-white" />
          </div>
          <span className="text-slate-900">Prompt Hubs</span>
        </Link>
      </div>

      <button
        onClick={() => navigate("/workspace/select")}
        className="mx-3 mt-3 flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-left text-sm hover:bg-slate-50"
        title="워크스페이스 전환"
      >
        <div className="flex min-w-0 items-center gap-2">
          <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
            <Building2 className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <div className="truncate font-semibold text-slate-900">
              {workspace?.name || slug}
            </div>
            <div className="truncate text-xs text-slate-500">/{slug}</div>
          </div>
        </div>
        <ChevronsUpDown className="h-4 w-4 flex-shrink-0 text-slate-400" />
      </button>

      <nav className="flex-1 space-y-0.5 px-3 py-4">
        {navItems.map((item) => (
          <NavLink
            key={item.name}
            to={`/w/${slug}${item.href}`}
            end={item.end}
            className={({ isActive }) =>
              cn(
                "group flex items-center rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-slate-700 hover:bg-slate-100"
              )
            }
          >
            <item.icon className="mr-3 h-4 w-4" />
            {item.name}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-slate-100 p-3">
        <div className="flex items-center gap-2 rounded-md px-2 py-2">
          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-slate-600">
            {user?.name?.[0]?.toUpperCase() || "?"}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-medium text-slate-900">
              {user?.name}
            </div>
            <div className="truncate text-xs text-slate-500">{user?.email}</div>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="mt-1 flex w-full items-center rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-red-50 hover:text-red-600"
        >
          <LogOut className="mr-3 h-4 w-4" />
          로그아웃
        </button>
      </div>
    </aside>
  )
}
