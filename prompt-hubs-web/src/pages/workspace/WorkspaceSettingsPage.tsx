import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate, useParams } from "react-router-dom"
import apiClient from "@/api/client"
import { useState } from "react"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  AlertTriangle,
  Building2,
  Check,
  Copy,
  ExternalLink,
  Hash,
  Key,
  Loader2,
  MessageSquare,
  Plug,
  Plus,
  Settings as SettingsIcon,
  Trash2,
  UserPlus,
  Users,
  X,
} from "lucide-react"

type Tab = "general" | "members" | "api-keys" | "integrations"

interface Workspace {
  id: string
  name: string
  slug: string
  plan: string
  myRole: string
}

interface Member {
  id: string
  accountId: string
  email: string
  name: string
  role: "OWNER" | "ADMIN" | "EDITOR" | "VIEWER"
  joinedAt: string
}

interface ApiKey {
  id: string
  label: string
  keyPrefix: string
  active: boolean
  lastUsedAt: string | null
  createdAt: string
  expiresAt: string | null
}

interface IssuedKey {
  id: string
  label: string
  keyPlaintext: string
  keyPrefix: string
  createdAt: string
}

interface IntegrationStatus {
  type: "NOTION" | "SLACK"
  active: boolean
  configured: boolean
  connectedAt: string | null
  lastSyncAt: string | null
  notionLogsDbId: string | null
  notionDocsDbId: string | null
  notionPromptsDbId: string | null
  notionParentPageId: string | null
  slackTeamId?: string | null
  slackChannelId?: string | null
  slackChannelName?: string | null
  slackNotifyReviewRequested?: boolean
  slackNotifyReviewCompleted?: boolean
  slackNotifyVersionCreated?: boolean
  slackNotifyPlanWarning?: boolean
  slackNotifyPrePrompting?: boolean
}

interface SlackChannelOption {
  id: string
  name: string
  privateChannel: boolean
}

interface NotionPageOption {
  id: string
  title: string
  icon: string | null
  url: string | null
  archived: boolean
}

export default function WorkspaceSettingsPage() {
  const { slug } = useParams()
  const [tab, setTab] = useState<Tab>("general")

  const workspace = useQuery<Workspace>({
    queryKey: ["workspace", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/workspaces/${slug}`)
      return res.data
    },
  })

  const tabs: { id: Tab; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: "general", label: "일반", icon: SettingsIcon },
    { id: "members", label: "멤버", icon: Users },
    { id: "api-keys", label: "MCP API 키", icon: Key },
    { id: "integrations", label: "통합", icon: Plug },
  ]

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">설정</h1>
        <p className="mt-1 text-sm text-slate-600">
          워크스페이스, 멤버, 통합을 관리합니다.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-8 md:grid-cols-4">
        <nav className="space-y-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex w-full items-center rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                tab === t.id
                  ? "bg-primary/10 text-primary"
                  : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              <t.icon className="mr-2 h-4 w-4" />
              {t.label}
            </button>
          ))}
        </nav>

        <div className="space-y-6 md:col-span-3">
          {tab === "general" && <GeneralTab workspace={workspace.data} />}
          {tab === "members" && <MembersTab slug={slug!} myRole={workspace.data?.myRole} />}
          {tab === "api-keys" && <ApiKeysTab slug={slug!} />}
          {tab === "integrations" && <IntegrationsTab slug={slug!} />}
        </div>
      </div>
    </div>
  )
}

function GeneralTab({ workspace }: { workspace?: Workspace }) {
  const { slug } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [name, setName] = useState("")
  const [error, setError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch(`/workspaces/${slug}`, { name })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workspace", slug] })
      setError(null)
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "저장 실패")
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/workspaces/${slug}`)
    },
    onSuccess: () => {
      navigate("/workspace/select", { replace: true })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "삭제 실패")
    },
  })

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">워크스페이스 프로필</CardTitle>
          <CardDescription>이름과 식별자를 관리합니다.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
              {error}
            </div>
          )}
          <div className="grid gap-2">
            <Label htmlFor="ws-name">이름</Label>
            <Input
              id="ws-name"
              defaultValue={workspace?.name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Workspace name"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="ws-slug">URL Slug</Label>
            <div className="flex items-center gap-1">
              <span className="rounded-l-md border border-r-0 border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">
                /w/
              </span>
              <Input
                id="ws-slug"
                value={workspace?.slug || ""}
                readOnly
                className="rounded-l-none bg-slate-50"
              />
            </div>
          </div>
          <div className="flex items-center justify-between border-t border-slate-100 pt-4">
            <span className="rounded-md bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
              현재 플랜: {workspace?.plan}
            </span>
            <Button
              onClick={() => saveMutation.mutate()}
              disabled={!name || saveMutation.isPending}
            >
              {saveMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  저장 중...
                </>
              ) : (
                "변경 저장"
              )}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="border-destructive/20">
        <CardHeader>
          <CardTitle className="flex items-center text-base text-destructive">
            <AlertTriangle className="mr-2 h-4 w-4" />
            위험 구역
          </CardTitle>
          <CardDescription>이 작업은 되돌릴 수 없습니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            className="border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
            onClick={() => {
              if (confirm("워크스페이스를 삭제하시겠습니까? 모든 데이터가 영구히 사라집니다.")) {
                deleteMutation.mutate()
              }
            }}
            disabled={deleteMutation.isPending}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            워크스페이스 삭제
          </Button>
        </CardContent>
      </Card>
    </>
  )
}

function MembersTab({ slug, myRole }: { slug: string; myRole?: string }) {
  const qc = useQueryClient()
  const [email, setEmail] = useState("")
  const [role, setRole] = useState<"ADMIN" | "EDITOR" | "VIEWER">("EDITOR")
  const [inviteLink, setInviteLink] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const members = useQuery<Member[]>({
    queryKey: ["members", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/members`)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const inviteMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post(`/workspaces/${slug}/invite`, {
        email,
        role,
      })
      return res.data
    },
    onSuccess: (data: any) => {
      const token = data?.token
      if (token) {
        const url = `${window.location.origin}/api/v1/workspaces/${slug}/join?token=${token}`
        setInviteLink(url)
      }
      setEmail("")
      setError(null)
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "초대 생성 실패")
    },
  })

  const removeMutation = useMutation({
    mutationFn: async (memberId: string) => {
      await apiClient.delete(`/w/${slug}/members/${memberId}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["members", slug] })
    },
  })

  const canManage = myRole === "OWNER" || myRole === "ADMIN"

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">멤버 ({members.data?.length ?? 0})</CardTitle>
          <CardDescription>워크스페이스에 속한 사용자입니다.</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {members.isLoading ? (
            <div className="flex items-center justify-center py-8 text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {(members.data ?? []).map((m) => (
                <li key={m.id} className="flex items-center justify-between px-5 py-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-200 text-sm font-bold text-slate-600">
                      {m.name[0]?.toUpperCase()}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-slate-900">{m.name}</p>
                      <p className="text-xs text-slate-500">{m.email}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        m.role === "OWNER"
                          ? "bg-amber-100 text-amber-800"
                          : m.role === "ADMIN"
                          ? "bg-blue-100 text-blue-800"
                          : "bg-slate-100 text-slate-700"
                      }`}
                    >
                      {m.role}
                    </span>
                    {canManage && m.role !== "OWNER" && (
                      <button
                        onClick={() => {
                          if (confirm(`${m.name}을(를) 제거하시겠습니까?`)) {
                            removeMutation.mutate(m.id)
                          }
                        }}
                        className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
                        title="멤버 제거"
                      >
                        <X className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {canManage && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center text-base">
              <UserPlus className="mr-2 h-4 w-4" />
              멤버 초대
            </CardTitle>
            <CardDescription>이메일과 역할로 초대 링크를 만듭니다.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {error && (
              <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="invitee@example.com"
                type="email"
                className="flex-1"
              />
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as any)}
                className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm shadow-sm"
              >
                <option value="ADMIN">ADMIN</option>
                <option value="EDITOR">EDITOR</option>
                <option value="VIEWER">VIEWER</option>
              </select>
              <Button
                onClick={() => inviteMutation.mutate()}
                disabled={!email || inviteMutation.isPending}
              >
                {inviteMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  "초대 생성"
                )}
              </Button>
            </div>
            {inviteLink && (
              <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3">
                <p className="mb-2 text-xs font-semibold text-emerald-800">
                  초대 링크가 생성되었습니다. 초대 대상자에게 전달하세요.
                </p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 overflow-x-auto rounded bg-white px-2 py-1 text-xs">
                    {inviteLink}
                  </code>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => navigator.clipboard.writeText(inviteLink)}
                  >
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </>
  )
}

function ApiKeysTab({ slug }: { slug: string }) {
  const qc = useQueryClient()
  const [label, setLabel] = useState("")
  const [issued, setIssued] = useState<IssuedKey | null>(null)
  const [error, setError] = useState<string | null>(null)

  const keys = useQuery<ApiKey[]>({
    queryKey: ["api-keys", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/api-keys`)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const issueMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post(`/w/${slug}/api-keys`, { label })
      return res.data as IssuedKey
    },
    onSuccess: (data) => {
      setIssued(data)
      setLabel("")
      setError(null)
      qc.invalidateQueries({ queryKey: ["api-keys", slug] })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "API 키 생성 실패")
    },
  })

  const revokeMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/w/${slug}/api-keys/${id}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["api-keys", slug] })
    },
  })

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center text-base">
            <Key className="mr-2 h-4 w-4" />
            MCP API 키
          </CardTitle>
          <CardDescription>
            Claude Code MCP 서버에서 이 워크스페이스에 접근할 때 사용하는 키입니다.
            발급 직후 한 번만 노출됩니다 — 안전한 곳에 보관하세요.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
              {error}
            </div>
          )}

          {issued ? (
            <IssuedKeyPanel issued={issued} onClose={() => setIssued(null)} />
          ) : (
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder='예: "claude-code-local"'
                className="flex-1"
              />
              <Button
                onClick={() => issueMutation.mutate()}
                disabled={!label || issueMutation.isPending}
              >
                {issueMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <>
                    <Plus className="mr-2 h-4 w-4" />
                    키 발급
                  </>
                )}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">활성 키</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {keys.isLoading ? (
            <div className="flex items-center justify-center py-8 text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : (keys.data?.length ?? 0) === 0 ? (
            <p className="p-6 text-center text-sm text-slate-500">
              발급된 키가 없습니다.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {keys.data!.map((k) => (
                <li
                  key={k.id}
                  className="flex items-center justify-between px-5 py-3"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-slate-900">
                        {k.label}
                      </p>
                      {!k.active && (
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
                          비활성
                        </span>
                      )}
                    </div>
                    <p className="font-mono text-xs text-slate-500">
                      {k.keyPrefix}…
                    </p>
                    <p className="text-xs text-slate-400">
                      생성 {new Date(k.createdAt).toLocaleDateString()}
                      {k.lastUsedAt && (
                        <>
                          {" · "}
                          마지막 사용 {new Date(k.lastUsedAt).toLocaleDateString()}
                        </>
                      )}
                    </p>
                  </div>
                  <button
                    onClick={() => {
                      if (
                        confirm(
                          `"${k.label}" 키를 삭제하시겠습니까? 이 키를 쓰던 Claude Code 클라이언트는 즉시 인증에 실패합니다.`
                        )
                      ) {
                        revokeMutation.mutate(k.id)
                      }
                    }}
                    className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
                    title="삭제"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card className="border-blue-200 bg-blue-50/40">
        <CardHeader>
          <CardTitle className="text-base text-blue-900">설치 가이드</CardTitle>
          <CardDescription className="text-blue-700">
            발급한 키로 Claude Code에 PromptHubs MCP 서버를 등록하세요.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p className="text-blue-900">
            <span className="rounded bg-blue-100 px-1.5 py-0.5 font-mono text-xs">
              claude mcp add
            </span>{" "}
            의 HTTP transport 한 줄로 등록됩니다. 로컬에 Node.js / 서버 파일을
            받을 필요가 없습니다.
          </p>
          <pre className="overflow-x-auto rounded-md bg-slate-950 p-3 font-mono text-xs text-slate-100">
{`claude mcp add prompthubs --transport http \\
  ${mcpBaseUrl()}/mcp \\
  --header "Authorization: Bearer ph_발급받은_키"`}
          </pre>
          <p className="text-xs text-blue-800">
            등록 후 Claude Code 를 재시작하면{" "}
            <code className="rounded bg-blue-100 px-1">/mcp</code> 명령에서
            <code className="mx-1 rounded bg-blue-100 px-1">prompthubs · connected</code>{" "}
            로 표시됩니다.
          </p>
          <div className="rounded-md border border-blue-200 bg-white p-3 text-xs text-blue-900">
            <p className="mb-1 font-semibold">제공되는 도구 4개</p>
            <ul className="ml-4 list-disc space-y-0.5 text-blue-800">
              <li>
                <code className="rounded bg-blue-100 px-1 font-mono">search_prompts</code>
                {" — 사내 검증된(APPROVED) 프롬프트 검색 (작업 전 호출 권장)"}
              </li>
              <li>
                <code className="rounded bg-blue-100 px-1 font-mono">get_prompt</code>
                {" — 단일 프롬프트 본문/버전 조회"}
              </li>
              <li>
                <code className="rounded bg-blue-100 px-1 font-mono">list_docs</code>
                {" — 워크스페이스 문서 목록 (컨벤션/스펙/런북)"}
              </li>
              <li>
                <code className="rounded bg-blue-100 px-1 font-mono">save_log</code>
                {" — 의미 있는 대화 끝에 PromptLog 자동 저장"}
              </li>
            </ul>
          </div>
        </CardContent>
      </Card>
    </>
  )
}

function IntegrationsTab({ slug }: { slug: string }) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const integrations = useQuery<IntegrationStatus[]>({
    queryKey: ["integrations", slug],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/integrations`)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const notion = integrations.data?.find((i) => i.type === "NOTION")
  const slack = integrations.data?.find((i) => i.type === "SLACK")

  const setupMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post(`/w/${slug}/integrations/notion/setup`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "DB 부트스트랩 실패")
    },
  })

  const disconnectMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/w/${slug}/integrations/notion`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
  })

  const selectParentMutation = useMutation({
    mutationFn: async (pageId: string) => {
      await apiClient.put(`/w/${slug}/integrations/notion/parent-page`, {
        pageId,
      })
    },
    onSuccess: () => {
      setError(null)
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "부모 페이지 설정 실패")
    },
  })

  const backfillMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{ pushed: number }>(
        `/w/${slug}/integrations/notion/backfill`
      )
      return res.data
    },
    onSuccess: (data) => {
      setError(null)
      alert(`Notion으로 ${data?.pushed ?? 0}건을 다시 보냈습니다.`)
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "동기화 실패")
    },
  })

  const startNotionAuth = async () => {
    setError(null)
    const popup = window.open("about:blank", "_blank", "noopener,noreferrer")
    if (!popup) {
      setError("팝업이 차단되었습니다. 브라우저에서 이 사이트의 팝업을 허용해주세요.")
      return
    }
    try {
      const res = await apiClient.get<{ authorizationUrl: string }>(
        `/w/${slug}/integrations/notion/connect`
      )
      const url = res.data?.authorizationUrl
      if (!url) {
        popup.close()
        setError("Notion 인증 URL을 받지 못했습니다.")
        return
      }
      popup.location.href = url
    } catch (err: any) {
      popup.close()
      setError(err.response?.data?.message || "Notion 연결 시작 실패")
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center text-base">
            <Building2 className="mr-2 h-4 w-4 text-slate-700" />
            Notion
          </CardTitle>
          <CardDescription>
            Notion 워크스페이스와 양방향으로 프롬프트·문서·로그를 동기화합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
              {error}
            </div>
          )}

          {integrations.isLoading ? (
            <div className="flex items-center justify-center py-4 text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : notion?.active && !notion?.notionParentPageId ? (
            <NotionParentPagePicker
              slug={slug}
              onPick={(pageId) => selectParentMutation.mutate(pageId)}
              picking={selectParentMutation.isPending}
              onDisconnect={() => {
                if (confirm("Notion 연결을 해제하시겠습니까?")) {
                  disconnectMutation.mutate()
                }
              }}
              disconnecting={disconnectMutation.isPending}
            />
          ) : notion?.active ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm font-medium text-emerald-700">
                <Check className="h-4 w-4" />
                연결됨
              </div>
              <dl className="grid grid-cols-2 gap-2 rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                <dt className="text-slate-500">연결 시각</dt>
                <dd className="text-slate-700">
                  {notion.connectedAt
                    ? new Date(notion.connectedAt).toLocaleString()
                    : "—"}
                </dd>
                <dt className="text-slate-500">마지막 동기화</dt>
                <dd className="text-slate-700">
                  {notion.lastSyncAt
                    ? new Date(notion.lastSyncAt).toLocaleString()
                    : "—"}
                </dd>
                <dt className="text-slate-500">부모 페이지</dt>
                <dd>
                  <NotionIdLink id={notion.notionParentPageId} />
                </dd>
                <dt className="text-slate-500">Prompts DB</dt>
                <dd>
                  <NotionIdLink id={notion.notionPromptsDbId} />
                </dd>
                <dt className="text-slate-500">Logs DB</dt>
                <dd>
                  <NotionIdLink id={notion.notionLogsDbId} />
                </dd>
                <dt className="text-slate-500">Docs DB</dt>
                <dd>
                  <NotionIdLink id={notion.notionDocsDbId} />
                </dd>
              </dl>
              <div className="flex flex-wrap items-center gap-2">
                {!notion.notionPromptsDbId && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setupMutation.mutate()}
                    disabled={setupMutation.isPending}
                  >
                    {setupMutation.isPending ? (
                      <Loader2 className="mr-2 h-3 w-3 animate-spin" />
                    ) : null}
                    DB 부트스트랩
                  </Button>
                )}
                {notion.notionPromptsDbId && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => backfillMutation.mutate()}
                    disabled={backfillMutation.isPending}
                  >
                    {backfillMutation.isPending ? (
                      <Loader2 className="mr-2 h-3 w-3 animate-spin" />
                    ) : null}
                    기존 데이터 다시 보내기
                  </Button>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    if (confirm("Notion 연결을 해제하시겠습니까?")) {
                      disconnectMutation.mutate()
                    }
                  }}
                  disabled={disconnectMutation.isPending}
                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                >
                  <X className="mr-2 h-3 w-3" />
                  연결 해제
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-slate-600">
                Notion 워크스페이스에 OAuth로 연결한 후, 사용할 부모 페이지를
                선택하면 Prompts / Logs / Docs 데이터베이스가 자동 생성됩니다.
              </p>

              <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                <p className="font-semibold text-slate-700">
                  시작 전 한 가지만 준비해 주세요
                </p>
                <ol className="mt-2 ml-4 list-decimal space-y-1 text-slate-600">
                  <li>
                    Notion에서 PromptHubs DB를 둘 <strong>빈 페이지</strong>를
                    하나 만드세요 (기존 페이지를 써도 됩니다).
                  </li>
                  <li>
                    "연결 시작" 클릭 후 동의 화면에서 <strong>"Select pages"</strong>
                    로 그 페이지를 선택하거나, 더 간편하게는{" "}
                    <strong>"All pages"</strong>로 워크스페이스 전체 권한을 주세요.
                  </li>
                  <li>
                    돌아오면 부모 페이지 선택 화면에서 그 페이지를 고르면
                    끝입니다.
                  </li>
                </ol>
                <a
                  href="https://www.notion.so/new"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                >
                  Notion에서 새 페이지 만들기
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>

              <Button onClick={startNotionAuth}>
                <Plug className="mr-2 h-4 w-4" />
                Notion 연결 시작
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <SlackIntegrationCard slug={slug} slack={slack} loading={integrations.isLoading} />
    </>
  )
}

type SlackFlagKey =
  | "notifyReviewRequested"
  | "notifyReviewCompleted"
  | "notifyVersionCreated"
  | "notifyPlanWarning"
  | "notifyPrePrompting"

const SLACK_FLAG_LABELS: Record<SlackFlagKey, string> = {
  notifyReviewRequested: "리뷰 요청",
  notifyReviewCompleted: "리뷰 완료",
  notifyVersionCreated: "새 버전 생성",
  notifyPlanWarning: "플랜 한도 경고",
  notifyPrePrompting: "사전 프롬프팅 위반",
}

function SlackIntegrationCard({
  slug,
  slack,
  loading,
}: {
  slug: string
  slack: IntegrationStatus | undefined
  loading: boolean
}) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [pendingChannelId, setPendingChannelId] = useState<string>("")
  const [pendingFlags, setPendingFlags] = useState<Record<SlackFlagKey, boolean>>({
    notifyReviewRequested: true,
    notifyReviewCompleted: true,
    notifyVersionCreated: true,
    notifyPlanWarning: true,
    notifyPrePrompting: true,
  })
  const [initialized, setInitialized] = useState(false)

  const isConnected = !!slack?.active && !!slack?.configured

  // 서버에서 받은 값으로 폼 1회 초기화
  if (!initialized && slack && isConnected) {
    setPendingChannelId(slack.slackChannelId ?? "")
    setPendingFlags({
      notifyReviewRequested: slack.slackNotifyReviewRequested ?? true,
      notifyReviewCompleted: slack.slackNotifyReviewCompleted ?? true,
      notifyVersionCreated: slack.slackNotifyVersionCreated ?? true,
      notifyPlanWarning: slack.slackNotifyPlanWarning ?? true,
      notifyPrePrompting: slack.slackNotifyPrePrompting ?? true,
    })
    setInitialized(true)
  }

  const channelsQuery = useQuery<SlackChannelOption[]>({
    queryKey: ["slack-channels", slug],
    enabled: isConnected,
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/integrations/slack/channels`)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const channel = channelsQuery.data?.find((c) => c.id === pendingChannelId)
      await apiClient.put(`/w/${slug}/integrations/slack/settings`, {
        channelId: pendingChannelId,
        channelName: channel?.name ?? null,
        ...pendingFlags,
      })
    },
    onSuccess: () => {
      setError(null)
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || "설정 저장 실패")
    },
  })

  const disconnectMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/w/${slug}/integrations/slack`)
    },
    onSuccess: () => {
      setInitialized(false)
      qc.invalidateQueries({ queryKey: ["integrations", slug] })
    },
  })

  const startSlackAuth = async () => {
    setError(null)
    const popup = window.open("about:blank", "_blank", "noopener,noreferrer")
    if (!popup) {
      setError("팝업이 차단되었습니다. 브라우저에서 이 사이트의 팝업을 허용해주세요.")
      return
    }
    try {
      const res = await apiClient.get<{ authorizationUrl: string }>(
        `/w/${slug}/integrations/slack/connect`
      )
      const url = res.data?.authorizationUrl
      if (!url) {
        popup.close()
        setError("Slack 인증 URL을 받지 못했습니다.")
        return
      }
      popup.location.href = url
    } catch (err: any) {
      popup.close()
      setError(err.response?.data?.message || "Slack 연결 시작 실패")
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center text-base">
          <MessageSquare className="mr-2 h-4 w-4 text-slate-700" />
          Slack
        </CardTitle>
        <CardDescription>
          리뷰 알림을 슬랙 채널로 보내고, 버튼으로 바로 승인/반려할 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-4 text-slate-400">
            <Loader2 className="h-4 w-4 animate-spin" />
          </div>
        ) : isConnected ? (
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-sm font-medium text-emerald-700">
              <Check className="h-4 w-4" />
              연결됨 {slack?.slackTeamId ? `(team ${slack.slackTeamId})` : ""}
            </div>

            <div className="space-y-1">
              <Label htmlFor="slack-channel" className="text-xs font-medium text-slate-700">
                알림 채널
              </Label>
              <div className="flex items-center gap-2">
                {channelsQuery.isLoading ? (
                  <div className="flex h-9 flex-1 items-center justify-center rounded-md border border-slate-200 bg-slate-50 text-xs text-slate-400">
                    <Loader2 className="mr-2 h-3 w-3 animate-spin" /> 채널 불러오는 중
                  </div>
                ) : (
                  <select
                    id="slack-channel"
                    value={pendingChannelId}
                    onChange={(e) => setPendingChannelId(e.target.value)}
                    className="h-9 flex-1 rounded-md border border-slate-200 bg-white px-2 text-sm"
                  >
                    <option value="">채널을 선택하세요</option>
                    {channelsQuery.data?.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.privateChannel ? "🔒" : "#"} {c.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>
              <p className="text-[11px] text-slate-500">
                원하는 채널이 안 보이면 슬랙에서{" "}
                <code className="rounded bg-slate-100 px-1">/invite @PromptHubs</code>
                {" "}로 봇을 초대한 뒤 새로고침하세요.
              </p>
            </div>

            <div className="space-y-2">
              <p className="text-xs font-medium text-slate-700">알림 종류</p>
              <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
                {(Object.keys(SLACK_FLAG_LABELS) as SlackFlagKey[]).map((key) => (
                  <label
                    key={key}
                    className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700"
                  >
                    <input
                      type="checkbox"
                      checked={pendingFlags[key]}
                      onChange={(e) =>
                        setPendingFlags((prev) => ({
                          ...prev,
                          [key]: e.target.checked,
                        }))
                      }
                      className="h-3.5 w-3.5"
                    />
                    {SLACK_FLAG_LABELS[key]}
                  </label>
                ))}
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                size="sm"
                onClick={() => saveMutation.mutate()}
                disabled={saveMutation.isPending || !pendingChannelId}
              >
                {saveMutation.isPending ? (
                  <Loader2 className="mr-2 h-3 w-3 animate-spin" />
                ) : (
                  <Check className="mr-2 h-3 w-3" />
                )}
                설정 저장
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  if (confirm("Slack 연결을 해제하시겠습니까?")) {
                    disconnectMutation.mutate()
                  }
                }}
                disabled={disconnectMutation.isPending}
                className="text-destructive hover:bg-destructive/10 hover:text-destructive"
              >
                <X className="mr-2 h-3 w-3" />
                연결 해제
              </Button>
            </div>

            {slack?.slackChannelName && pendingChannelId === slack.slackChannelId && (
              <p className="text-[11px] text-slate-500">
                <Hash className="mr-0.5 inline h-3 w-3" />
                현재 채널: {slack.slackChannelName}
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              슬랙 워크스페이스에 PromptHubs 봇을 설치하면 채널에서 리뷰 알림을 받고
              버튼으로 바로 승인할 수 있습니다.
            </p>
            <Button onClick={startSlackAuth}>
              <Plug className="mr-2 h-4 w-4" />
              Slack 연결 시작
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function NotionParentPagePicker({
  slug,
  onPick,
  picking,
  onDisconnect,
  disconnecting,
}: {
  slug: string
  onPick: (pageId: string) => void
  picking: boolean
  onDisconnect: () => void
  disconnecting: boolean
}) {
  const [query, setQuery] = useState("")
  const [manualMode, setManualMode] = useState(false)
  const [manualId, setManualId] = useState("")

  const pages = useQuery<NotionPageOption[]>({
    queryKey: ["notion-pages", slug, query],
    queryFn: async () => {
      const res = await apiClient.get(`/w/${slug}/integrations/notion/pages`, {
        params: query ? { query } : undefined,
      })
      return Array.isArray(res.data) ? res.data : []
    },
  })

  const visible = (pages.data ?? []).filter((p) => !p.archived)

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-emerald-700">
        <Check className="h-4 w-4" />
        Notion 연결 완료 — 부모 페이지를 선택하세요
      </div>
      <p className="text-xs text-slate-500">
        선택한 페이지 아래에 Prompts / Logs / Docs 3개 데이터베이스가 자동
        생성됩니다. Notion에서 PromptHubs integration을 추가한(공유한) 페이지만
        보입니다.
      </p>

      {!manualMode ? (
        <>
          <div className="flex gap-2">
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="페이지 제목으로 검색"
              className="flex-1"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => pages.refetch()}
              disabled={pages.isFetching}
            >
              {pages.isFetching ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "다시 검색"
              )}
            </Button>
          </div>

          {pages.isLoading ? (
            <div className="flex items-center justify-center py-6 text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : visible.length === 0 ? (
            <div className="space-y-3 rounded-md border border-dashed border-slate-200 p-4 text-xs text-slate-600">
              <p className="text-center font-medium text-slate-700">
                접근 가능한 페이지가 없습니다
              </p>
              <p>
                Notion에서 부모로 쓸 페이지를 열고{" "}
                <strong>Share → Connections</strong> 메뉴에서{" "}
                <strong>PromptHubs</strong>를 추가하면 여기에 표시됩니다. 페이지가
                아직 없으면 새로 만들어주세요.
              </p>
              <div className="flex flex-wrap justify-center gap-2 pt-1">
                <a
                  href="https://www.notion.so/new"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-50"
                >
                  Notion에서 새 페이지 만들기
                  <ExternalLink className="h-3 w-3" />
                </a>
                <a
                  href="https://www.notion.so/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-50"
                >
                  Notion 열기
                  <ExternalLink className="h-3 w-3" />
                </a>
                <button
                  type="button"
                  onClick={() => pages.refetch()}
                  className="inline-flex items-center rounded-md border border-slate-200 bg-white px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-50"
                >
                  다시 검색
                </button>
              </div>
            </div>
          ) : (
            <ul className="max-h-72 divide-y divide-slate-100 overflow-y-auto rounded-md border border-slate-200">
              {visible.map((p) => (
                <li key={p.id}>
                  <button
                    type="button"
                    onClick={() => onPick(p.id)}
                    disabled={picking}
                    className="flex w-full items-center gap-3 px-3 py-2 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                  >
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded bg-slate-100 text-base">
                      {p.icon && p.icon.length <= 4 ? p.icon : "📄"}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium text-slate-900">
                        {p.title}
                      </span>
                      {p.url && (
                        <span className="block truncate text-[11px] text-slate-400">
                          {p.url}
                        </span>
                      )}
                    </span>
                    {picking && (
                      <Loader2 className="h-3 w-3 animate-spin text-slate-400" />
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : (
        <div className="space-y-2">
          <Label htmlFor="manual-page-id" className="text-xs">
            페이지 ID 또는 URL을 직접 붙여넣기
          </Label>
          <Input
            id="manual-page-id"
            value={manualId}
            onChange={(e) => setManualId(e.target.value)}
            placeholder="https://www.notion.so/My-Page-1a2b3c... 또는 32자 ID"
          />
          <p className="text-[11px] text-slate-500">
            URL 끝의 32자가 페이지 ID입니다. 하이픈은 자동 정규화됩니다.
          </p>
          <div className="flex justify-end gap-2">
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setManualMode(false)
                setManualId("")
              }}
            >
              취소
            </Button>
            <Button
              size="sm"
              onClick={() => {
                const id = extractPageId(manualId)
                if (id) onPick(id)
              }}
              disabled={!manualId.trim() || picking}
            >
              {picking ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "이 페이지 사용"
              )}
            </Button>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between border-t border-slate-100 pt-3">
        <button
          type="button"
          onClick={() => setManualMode((m) => !m)}
          className="text-xs text-primary hover:underline"
        >
          {manualMode ? "← 검색으로 돌아가기" : "페이지가 안 보이면 ID 직접 입력"}
        </button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onDisconnect}
          disabled={disconnecting}
          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
        >
          <X className="mr-1 h-3 w-3" />
          연결 해제
        </Button>
      </div>
    </div>
  )
}

function mcpBaseUrl(): string {
  if (typeof window === "undefined") return "http://localhost:8080"
  const { protocol, hostname, port, host } = window.location
  if (hostname === "localhost" && port === "5173") return "http://localhost:8080"
  return `${protocol}//${host}`
}

function extractPageId(input: string): string | null {
  const trimmed = input.trim()
  if (!trimmed) return null
  const match = trimmed.match(/[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i)
  return match ? match[0] : trimmed
}

function IssuedKeyPanel({
  issued,
  onClose,
}: {
  issued: IssuedKey
  onClose: () => void
}) {
  const [copiedKey, setCopiedKey] = useState(false)
  const [copiedCmd, setCopiedCmd] = useState(false)
  const [copiedHook, setCopiedHook] = useState(false)
  const baseUrl = mcpBaseUrl()
  const cmd =
    `claude mcp add prompthubs --transport http \\\n` +
    `  ${baseUrl}/mcp \\\n` +
    `  --header "Authorization: Bearer ${issued.keyPlaintext}"`

  const hookCommand =
    `PROMPTHUBS_API_KEY=${issued.keyPlaintext} ` +
    `PROMPTHUBS_BASE_URL=${baseUrl} ` +
    `npx -y prompthubs-mcp-hook`

  const hookJson = JSON.stringify(
    {
      hooks: {
        Stop: [
          {
            matcher: "",
            hooks: [{ type: "command", command: hookCommand, timeout: 30 }],
          },
        ],
      },
    },
    null,
    2
  )

  const copy = async (text: string, setFlag: (b: boolean) => void) => {
    await navigator.clipboard.writeText(text)
    setFlag(true)
    setTimeout(() => setFlag(false), 1500)
  }

  return (
    <div className="space-y-4 rounded-md border border-emerald-200 bg-emerald-50 p-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-emerald-800">
        <Check className="h-4 w-4" />
        "{issued.label}" 키가 발급되었습니다
      </div>
      <p className="text-xs text-emerald-700">
        이 키는 이번 화면을 닫으면 다시 볼 수 없습니다. Claude Code 등록 명령과
        자동 저장 Hook 설정도 함께 만들어 두었으니 그대로 복사해서 쓰세요.
      </p>

      <div>
        <p className="mb-1 text-xs font-medium text-emerald-900">① API 키 (평문)</p>
        <div className="flex items-center gap-2">
          <code className="flex-1 overflow-x-auto rounded bg-white px-3 py-2 font-mono text-xs text-slate-900">
            {issued.keyPlaintext}
          </code>
          <Button
            size="sm"
            variant="outline"
            onClick={() => copy(issued.keyPlaintext, setCopiedKey)}
          >
            <Copy className="mr-1 h-3 w-3" />
            {copiedKey ? "복사됨" : "복사"}
          </Button>
        </div>
      </div>

      <div>
        <p className="mb-1 text-xs font-medium text-emerald-900">
          ② MCP 서버 등록 — Claude Code 터미널에 붙여넣기
        </p>
        <div className="flex items-start gap-2">
          <pre className="flex-1 overflow-x-auto rounded bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100">
            {cmd}
          </pre>
          <Button
            size="sm"
            variant="outline"
            onClick={() => copy(cmd, setCopiedCmd)}
          >
            <Copy className="mr-1 h-3 w-3" />
            {copiedCmd ? "복사됨" : "명령 복사"}
          </Button>
        </div>
        {baseUrl.includes("localhost") && (
          <p className="mt-1 text-[11px] text-emerald-700">
            배포 환경에서는 URL 의{" "}
            <code className="rounded bg-emerald-100 px-1">localhost:8080</code>{" "}
            부분이 자동으로 배포 도메인으로 바뀝니다.
          </p>
        )}
      </div>

      <div>
        <p className="mb-1 text-xs font-medium text-emerald-900">
          ③ 모든 대화 자동 저장 — Claude Code Stop hook 등록
        </p>
        <p className="mb-2 text-[11px] text-emerald-700">
          아래 JSON 을 <code className="rounded bg-emerald-100 px-1">~/.claude/settings.json</code>{" "}
          (또는 프로젝트 한정으로 <code className="rounded bg-emerald-100 px-1">.claude/settings.local.json</code>) 의
          최상위 객체에 병합하세요. 이미 <code className="rounded bg-emerald-100 px-1">hooks</code> 키가 있으면
          그 안의 <code className="rounded bg-emerald-100 px-1">Stop</code> 배열에 항목 하나만 추가하면 됩니다.
        </p>
        <div className="flex items-start gap-2">
          <pre className="flex-1 overflow-x-auto rounded bg-slate-950 px-3 py-2 font-mono text-[11px] text-slate-100">
            {hookJson}
          </pre>
          <Button
            size="sm"
            variant="outline"
            onClick={() => copy(hookJson, setCopiedHook)}
          >
            <Copy className="mr-1 h-3 w-3" />
            {copiedHook ? "복사됨" : "JSON 복사"}
          </Button>
        </div>
        <p className="mt-2 text-[11px] text-emerald-700">
          <code className="rounded bg-emerald-100 px-1">npx</code> 가 실행 시점에{" "}
          <code className="rounded bg-emerald-100 px-1">prompthubs-mcp-hook</code> 패키지를
          자동으로 받아오므로 별도 설치는 필요 없습니다. 등록 후 Claude Code 를{" "}
          <strong>새 세션으로 재시작</strong>하면 매 응답이 끝날 때마다 사용자 프롬프트 +
          결과(코드 블록·도구 호출 포함)가 PromptHubs Logs 에 자동 기록되고, Notion Logs DB
          에도 가독성 있게 동기화됩니다.
        </p>
      </div>

      <Button variant="ghost" size="sm" onClick={onClose} className="mt-1">
        저장 완료, 화면 닫기
      </Button>
    </div>
  )
}

function NotionIdLink({ id }: { id: string | null }) {
  if (!id) return <span className="text-slate-400">미설정</span>
  const stripped = id.replace(/-/g, "")
  const href = `https://www.notion.so/${stripped}`
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      title="Notion에서 열기"
      className="inline-flex items-center gap-1 truncate font-mono text-slate-700 hover:text-primary hover:underline"
    >
      <span className="truncate">{id}</span>
      <ExternalLink className="h-3 w-3 shrink-0" />
    </a>
  )
}
