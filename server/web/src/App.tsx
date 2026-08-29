import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactElement } from 'react'
import { NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router'
import { Eye, EyeOff, Bookmark, FileClock, FolderOpen, Home, KeyRound, ListTodo, Loader2, MessageSquareText, MoreHorizontal, Pencil, Plus, Search, Trash2, TrendingUp, UserCircle, Users, UsersRound, Wind } from 'lucide-react'

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarSeparator,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { Toaster } from '@/components/ui/sonner'
import { deleteChatSession as deleteChatSessionRequest, fetchCurrentUser, listChatSessions, listProjects, login, renameChatSession as renameChatSessionRequest, startNewChatSession, type AuthUser, type ChatSessionSummary, updatePassword } from '@/lib/api'
import { toast } from 'sonner'

import './App.css'
import AuditLogsPage from './AuditLogsPage'
import AIEfficiencyPage from './AIEfficiencyPage'
import ProjectWorkspacePage from './ProjectWorkspacePage'
import MyAccountPage from './MyAccountPage'
import ProjectsPage from './ProjectsPage'
import ProjectSettingsPage from './ProjectSettingsPage'
import TeamDetailsPage from './TeamDetailsPage'
import TeamsPage from './TeamsPage'
import UsersPage from './UsersPage'
import SubscriptionsPage from './SubscriptionsPage'
import MyWorkPage from './MyWorkPage'
import AskPage from './AskPage'
import HomePage from './HomePage'
import NotificationCenter, { NotificationProvider } from './components/NotificationCenter'

const baseMenuItems = [
  { label: 'Projects', path: '/app/projects', icon: FolderOpen },
  { label: 'My Work', path: '/app/my-work', icon: ListTodo },
  { label: 'Subscriptions', path: '/app/subscriptions', icon: Bookmark },
  { label: 'Teams', path: '/app/teams', icon: UsersRound },
  { label: 'Users', path: '/app/users', icon: Users },
]

const aiEfficiencyMenuItem = { label: 'AI Efficiency', path: '/app/ai-efficiency', icon: TrendingUp }
const sessionPageSize = 10

export type AskPageOutletContext = {
  askProjectId: string
  chatSessions: ChatSessionSummary[]
  selectedSessionId: string | null
  newChatRequestKey: number
  isLoadingSessions: boolean
  refreshChatSessions: (projectId: string, preferredSessionId?: string) => Promise<void>
  setAskProjectId: (projectId: string) => void
  onStreamingChange: (isStreaming: boolean) => void
}


function isAdminLike(user: AuthUser | null) {
  return user?.globalRole === 'ADMIN' || user?.globalRole === 'SUPERADMIN'
}

function authRedirectPath(user: AuthUser | null) {
  if (!user) {
    return '/login'
  }

  return user.mustChangePassword ? '/change-password' : '/app/home'
}

function formatRelativeAge(timestamp?: string) {
  if (!timestamp) {
    return 'now'
  }
  const createdAt = new Date(timestamp).getTime()
  if (!Number.isFinite(createdAt)) {
    return 'now'
  }
  const elapsedMilliseconds = Math.max(0, Date.now() - createdAt)
  const minutes = Math.floor(elapsedMilliseconds / 60000)
  if (minutes < 1) {
    return 'now'
  }
  if (minutes < 60) {
    return `${minutes}m`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours}h`
  }
  const days = Math.floor(hours / 24)
  if (days < 30) {
    return `${days}d`
  }
  const months = Math.floor(days / 30)
  if (months < 12) {
    return `${months}mo`
  }
  return `${Math.floor(months / 12)}y`
}

function AskSessionsSidebar({
  projectId,
  sessions,
  selectedSessionId,
  isLoading,
  onSelectSession,
  onRenameSession,
  onDeleteSession,
}: {
  projectId: string
  sessions: ChatSessionSummary[]
  selectedSessionId: string | null
  isLoading: boolean
  onSelectSession: (sessionId: string) => void
  onRenameSession: (sessionId: string, title: string) => Promise<void>
  onDeleteSession: (sessionId: string) => Promise<void>
}) {
  const [page, setPage] = useState(0)
  const [openActionSessionId, setOpenActionSessionId] = useState<string | null>(null)
  const [actionMode, setActionMode] = useState<'actions' | 'rename' | 'delete'>('actions')
  const [renameValue, setRenameValue] = useState('')
  const [isRenaming, setIsRenaming] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const visibleSessions = useMemo(
    () => projectId ? sessions.filter((session) => session.projectId === projectId) : sessions,
    [projectId, sessions],
  )
  const totalPages = Math.max(1, Math.ceil(visibleSessions.length / sessionPageSize))
  const currentPage = Math.min(page, totalPages - 1)
  const loadedSessions = visibleSessions.slice(0, (currentPage + 1) * sessionPageSize)

  async function handleRename(event: FormEvent<HTMLFormElement>, sessionId: string) {
    event.preventDefault()
    const title = renameValue.trim()
    if (!title || isRenaming) {
      return
    }

    setIsRenaming(true)
    try {
      await onRenameSession(sessionId, title)
      setOpenActionSessionId(null)
      setActionMode('actions')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to rename chat session.')
    } finally {
      setIsRenaming(false)
    }
  }

  async function handleDelete(sessionId: string) {
    if (isDeleting) {
      return
    }

    setIsDeleting(true)
    try {
      await onDeleteSession(sessionId)
      setOpenActionSessionId(null)
      setActionMode('actions')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete chat session.')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-0 overflow-hidden">
      <div className="min-h-0 flex-1 overflow-y-auto p-2 group-data-[collapsible=icon]:no-scrollbar">
        {!projectId ? (
          <div className="px-2 py-8 text-center text-sm text-muted-foreground group-data-[collapsible=icon]:hidden">Start a chat to see conversations</div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
          </div>
        ) : visibleSessions.length === 0 ? (
          <div className="px-2 py-8 text-center text-sm text-muted-foreground group-data-[collapsible=icon]:hidden">No recent conversations yet</div>
        ) : (
          <div className="space-y-1">
            {loadedSessions.map((session) => {
              const isSelected = session.id === selectedSessionId
              return (
                <div
                  key={session.id}
                  className={[
                    'group/session-row flex w-full min-w-0 items-center gap-1 rounded-md px-1.5 py-1 text-left transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                    'group-data-[collapsible=icon]:size-8 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0',
                    isSelected ? 'bg-sidebar-accent text-sidebar-accent-foreground' : '',
                  ].join(' ')}
                >
                  <button
                    type="button"
                    className="flex min-w-0 flex-1 items-center gap-2 rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring group-data-[collapsible=icon]:size-8 group-data-[collapsible=icon]:flex-none group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-2"
                    onClick={() => onSelectSession(session.id)}
                    aria-current={isSelected ? 'page' : undefined}
                    title={session.title}
                  >
                    <MessageSquareText className={isSelected ? 'h-4 w-4 shrink-0 text-primary' : 'h-4 w-4 shrink-0 text-muted-foreground'} />
                    <span className="min-w-0 flex-1 truncate text-sm group-data-[collapsible=icon]:hidden">{session.title}</span>
                  </button>
                  <div className="relative h-5 w-8 shrink-0 group-data-[collapsible=icon]:hidden">
                    <span className={['block text-right text-xs text-muted-foreground transition-opacity group-hover/session-row:opacity-0', openActionSessionId === session.id ? 'opacity-0' : ''].join(' ')}>{formatRelativeAge(session.createdAt)}</span>
                    <Popover
                      open={openActionSessionId === session.id}
                      onOpenChange={(open) => {
                        if (open) {
                          setOpenActionSessionId(session.id)
                          setActionMode('actions')
                        } else if (openActionSessionId === session.id) {
                          setOpenActionSessionId(null)
                          setActionMode('actions')
                        }
                      }}
                    >
                      <PopoverTrigger
                        render={(
                          <Button
                            type="button"
                            size="icon-xs"
                            variant="ghost"
                            className="absolute inset-0 !h-auto !w-auto p-0 opacity-0 transition-opacity group-hover/session-row:opacity-100 focus-visible:opacity-100 data-popup-open:opacity-100"
                            aria-label={`More actions for ${session.title}`}
                            title="More actions"
                          >
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        )}
                      />
                      <PopoverContent side="right" align="start" className="w-56 gap-2 p-2">
                        {actionMode === 'actions' ? (
                          <div className="flex flex-col gap-1">
                            <Button
                              type="button"
                              variant="ghost"
                              className="justify-start gap-2"
                              onClick={() => {
                                setRenameValue(session.title)
                                setActionMode('rename')
                              }}
                            >
                              <Pencil className="h-4 w-4" />
                              Rename
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              className="justify-start gap-2 text-destructive hover:text-destructive"
                              onClick={() => setActionMode('delete')}
                            >
                              <Trash2 className="h-4 w-4" />
                              Delete
                            </Button>
                          </div>
                        ) : actionMode === 'rename' ? (
                          <>
                            <PopoverHeader>
                              <PopoverTitle>Rename session</PopoverTitle>
                            </PopoverHeader>
                            <form className="space-y-3" onSubmit={(event) => void handleRename(event, session.id)}>
                              <Input
                                value={renameValue}
                                onChange={(event) => setRenameValue(event.target.value)}
                                maxLength={120}
                                autoFocus
                                aria-label="Session name"
                              />
                              <div className="flex justify-end gap-2">
                                <Button type="button" size="sm" variant="ghost" onClick={() => setOpenActionSessionId(null)} disabled={isRenaming}>Cancel</Button>
                                <Button type="submit" size="sm" disabled={isRenaming || !renameValue.trim()}>
                                  {isRenaming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Pencil className="h-4 w-4" />}
                                  Save
                                </Button>
                              </div>
                            </form>
                          </>
                        ) : (
                          <>
                            <PopoverHeader>
                              <PopoverTitle>Delete session?</PopoverTitle>
                              <p className="text-muted-foreground">This permanently deletes the conversation and its messages.</p>
                            </PopoverHeader>
                            <div className="flex justify-end gap-2">
                              <Button type="button" size="sm" variant="outline" onClick={() => setActionMode('actions')} disabled={isDeleting}>Cancel</Button>
                              <Button type="button" size="sm" variant="destructive" onClick={() => void handleDelete(session.id)} disabled={isDeleting}>
                                {isDeleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                                Delete
                              </Button>
                            </div>
                          </>
                        )}
                      </PopoverContent>
                    </Popover>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {loadedSessions.length < visibleSessions.length ? (
        <div className="shrink-0 px-2 py-1 group-data-[collapsible=icon]:hidden">
          <Button
            type="button"
            variant="ghost"
            className="h-7 w-full text-xs"
            onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
          >
            Load more
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function AppLayout({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [askProjectId, setAskProjectId] = useState('')
  const [askSessions, setAskSessions] = useState<ChatSessionSummary[]>([])
  const [selectedAskSessionId, setSelectedAskSessionId] = useState<string | null>(null)
  const [sessionsProjectId, setSessionsProjectId] = useState<string | null>(null)
  const [isStartingSession, setIsStartingSession] = useState(false)
  const [isChatStreaming, setIsChatStreaming] = useState(false)
  const [newChatRequestKey, setNewChatRequestKey] = useState(0)
  const menuItems = isAdminLike(currentUser)
    ? [
        ...baseMenuItems,
        { label: 'Audit Logs', path: '/app/audit-logs', icon: FileClock },
        aiEfficiencyMenuItem,
      ]
    : [...baseMenuItems, aiEfficiencyMenuItem]
  const accountLabel = `@${currentUser?.displayName || currentUser?.username || 'account'}`
  const refreshChatSessions = useCallback(async (projectId: string, preferredSessionId?: string) => {
    const nextSessions = await listChatSessions(projectId)
    setAskSessions(nextSessions)
    setSessionsProjectId(projectId)
    setSelectedAskSessionId((current) => {
      if (preferredSessionId && nextSessions.some((session) => session.id === preferredSessionId)) {
        return preferredSessionId
      }
      if (current && nextSessions.some((session) => session.id === current)) {
        return current
      }
      return nextSessions.find((session) => session.status === 'ACTIVE')?.id ?? nextSessions[0]?.id ?? null
    })
  }, [])

  const renameChatSession = useCallback(async (sessionId: string, title: string) => {
    await renameChatSessionRequest(askProjectId, sessionId, title)
    await refreshChatSessions(askProjectId)
  }, [askProjectId, refreshChatSessions])

  const deleteChatSession = useCallback(async (sessionId: string) => {
    await deleteChatSessionRequest(askProjectId, sessionId)
    await refreshChatSessions(askProjectId)
  }, [askProjectId, refreshChatSessions])

  useEffect(() => {
    if (askProjectId) {
      return
    }

    let isMounted = true
    listProjects()
      .then((projects) => {
        if (isMounted && projects[0]) {
          setAskProjectId(projects[0].id)
        }
      })
      .catch(() => {
        // The Home and workspace navigation remain usable if projects cannot be loaded.
      })

    return () => {
      isMounted = false
    }
  }, [askProjectId])

  useEffect(() => {
    if (!askProjectId) {
      return
    }
    let isMounted = true
    listChatSessions(askProjectId)
      .then((nextSessions) => {
        if (!isMounted) return
        setAskSessions(nextSessions)
        setSessionsProjectId(askProjectId)
        setSelectedAskSessionId(nextSessions.find((session) => session.status === 'ACTIVE')?.id ?? nextSessions[0]?.id ?? null)
      })
      .catch((error) => {
        if (isMounted) {
          setSessionsProjectId(askProjectId)
          toast.error(error instanceof Error ? error.message : 'Failed to load chat sessions.')
        }
      })

    return () => {
      isMounted = false
    }
  }, [askProjectId])

  async function handleStartNewSession() {
    if (!askProjectId || isStartingSession || isChatStreaming) return
    setIsStartingSession(true)
    try {
      const session = await startNewChatSession(askProjectId)
      await refreshChatSessions(askProjectId, session.id)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to start a new chat session.')
    } finally {
      setIsStartingSession(false)
    }
  }

  async function handleNewChat() {
    setNewChatRequestKey((current) => current + 1)
    if (!askProjectId) {
      navigate('/app/ask-ai')
      return
    }
    await handleStartNewSession()
    navigate('/app/ask-ai')
  }

  const askPageContext: AskPageOutletContext = {
    askProjectId,
    chatSessions: askSessions,
    selectedSessionId: selectedAskSessionId,
    newChatRequestKey,
    isLoadingSessions: Boolean(askProjectId) && sessionsProjectId !== askProjectId,
    refreshChatSessions,
    setAskProjectId,
    onStreamingChange: setIsChatStreaming,
  }

  return (
    <TooltipProvider>
      <SidebarProvider className="h-svh min-h-0 overflow-hidden">
        <Sidebar collapsible="icon">
          <SidebarHeader className="pb-0">
            <div className="flex h-10 items-center justify-between group-data-[collapsible=icon]:justify-center">
              <NavLink
                to="/app/home"
                className="flex h-8 items-center gap-2 overflow-hidden rounded-md p-2 group-data-[collapsible=icon]:hidden"
              >
                <div className="min-w-0 flex-1 text-left text-lg leading-tight">
                  <span className="truncate font-semibold">Windrunner</span>
                </div>
              </NavLink>
              <SidebarTrigger className="-mr-1 shrink-0 group-data-[collapsible=icon]:m-0" />
            </div>
          </SidebarHeader>

          <SidebarContent className="gap-0">
            <SidebarGroup className="pb-1">
              <SidebarGroupContent>
                <SidebarMenu>
                  <SidebarMenuItem>
                    <SidebarMenuButton
                      render={<NavLink to="/app/home" />}
                      isActive={location.pathname === '/app' || location.pathname === '/app/home'}
                      tooltip="Home"
                    >
                      <Home />
                      <span>Home</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                  <SidebarMenuItem>
                    <SidebarMenuButton
                      render={<button type="button" onClick={() => void handleNewChat()} />}
                      tooltip="New chat"
                    >
                      <Plus />
                      <span>New chat</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                  <SidebarMenuItem>
                    <SidebarMenuButton
                      render={<button type="button" onClick={() => navigate('/app/home?focus=search')} />}
                      tooltip="Search"
                    >
                      <Search />
                      <span>Search</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
            <SidebarSeparator className="mx-0" />
            <SidebarGroup className="min-h-0 flex-1 overflow-hidden pt-2">
              <SidebarGroupLabel className="h-6 px-2">Recent conversations</SidebarGroupLabel>
              <SidebarGroupContent className="flex min-h-0 flex-1 flex-col overflow-hidden">
              <AskSessionsSidebar
                key={askProjectId || 'no-project'}
                projectId={askProjectId}
                sessions={askSessions}
                selectedSessionId={selectedAskSessionId}
                isLoading={Boolean(askProjectId) && sessionsProjectId !== askProjectId}
                onSelectSession={(sessionId) => {
                  setSelectedAskSessionId(sessionId)
                  navigate('/app/ask-ai')
                }}
                onRenameSession={renameChatSession}
                onDeleteSession={deleteChatSession}
              />
              </SidebarGroupContent>
            </SidebarGroup>
            <SidebarSeparator className="mx-0" />
            <SidebarGroup className="shrink-0 pt-2">
              <SidebarGroupLabel className="h-6 px-2">Workspace</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {menuItems.map((item) => (
                    <SidebarMenuItem key={item.path}>
                      <SidebarMenuButton
                        render={<NavLink to={item.path} />}
                        isActive={location.pathname === item.path || location.pathname.startsWith(`${item.path}/`)}
                        tooltip={item.label}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </SidebarContent>

          <SidebarFooter className="gap-0 p-0">
            <SidebarSeparator className="mx-0" />
            <div className="flex flex-col gap-0 p-2">
              <SidebarMenu>
                <SidebarMenuItem>
                  <NotificationCenter inSidebar />
                </SidebarMenuItem>
              </SidebarMenu>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton
                    render={<NavLink to="/app/account" />}
                    isActive={location.pathname === '/app/account'}
                    tooltip={accountLabel}
                  >
                    <UserCircle />
                    <span>{accountLabel}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </div>
          </SidebarFooter>

          <SidebarRail />
        </Sidebar>

        <SidebarInset className="min-h-0 min-w-0 overflow-hidden bg-white">
          <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4 md:hidden">
            <SidebarTrigger className="-ml-1" />
          </header>
          <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
            <Outlet context={askPageContext} />
          </div>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}

function ProtectedApp({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()

  if (!currentUser) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (currentUser.mustChangePassword) {
    return <Navigate to="/change-password" replace />
  }

  return <AppLayout currentUser={currentUser} />
}

function LegacyWorkspaceRedirect() {
  const location = useLocation()
  const nextPath = location.pathname.replace(/^\/workspace/, '/app')
  return <Navigate to={`${nextPath}${location.search}${location.hash}`} replace />
}

function AdminOnlyRoute({
  currentUser,
  children,
}: {
  currentUser: AuthUser | null
  children: ReactElement
}) {
  if (!isAdminLike(currentUser)) {
    return <Navigate to="/app/home" replace />
  }

  return children
}

function LoginPage({
  currentUser,
  onLogin,
}: {
  currentUser: AuthUser | null
  onLogin: (user: AuthUser) => void
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const [loginValue, setLoginValue] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (currentUser) {
    return <Navigate to={authRedirectPath(currentUser)} replace />
  }

  const fromPath = typeof location.state === 'object' && location.state && 'from' in location.state
    ? String(location.state.from)
    : '/app/home'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setErrorMessage(null)

    try {
      const user = await login(loginValue, password)
      onLogin(user)
      navigate(user.mustChangePassword ? '/change-password' : fromPath, { replace: true })
    } catch (submitError) {
      setErrorMessage(submitError instanceof Error ? submitError.message : 'Failed to sign in.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-white p-6">
      <Card className="w-full max-w-md border bg-background shadow-sm">
        <CardHeader className="gap-3">
          <div className="flex items-center justify-center gap-2">
            <Wind className="size-5 text-sky-600" strokeWidth={2.6} />
            <CardTitle className="text-2xl font-semibold leading-none">Windrunner</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="block text-sm font-semibold">Username or email</label>
              <Input
                value={loginValue}
                onChange={(event) => setLoginValue(event.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <label className="block text-sm font-semibold">Password</label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="-mr-2 gap-1.5 px-2 text-muted-foreground"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  {showPassword ? 'Hide' : 'Show'}
                </Button>
              </div>
              <Input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            {errorMessage ? (
              <div className="border border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {errorMessage}
              </div>
            ) : null}
            <Button
              type="submit"
              disabled={isSubmitting}
              className="w-full"
            >
              {isSubmitting ? 'Signing in...' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}

function ChangePasswordPage({
  currentUser,
  onUserChange,
}: {
  currentUser: AuthUser | null
  onUserChange: (user: AuthUser) => void
}) {
  const navigate = useNavigate()
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!currentUser) {
    return <Navigate to="/login" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!newPassword.trim()) {
      setErrorMessage('New password is required.')
      return
    }

    if (newPassword.length < 6) {
      setErrorMessage('Password must be at least 6 characters.')
      return
    }

    if (newPassword !== confirmPassword) {
      setErrorMessage('Passwords do not match.')
      return
    }

    setIsSubmitting(true)
    setErrorMessage(null)

    try {
      const user = await updatePassword(newPassword)
      onUserChange(user)
      navigate('/app/home', { replace: true })
    } catch (submitError) {
      setErrorMessage(submitError instanceof Error ? submitError.message : 'Failed to update password.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-white p-6">
      <Card className="w-full max-w-md border bg-background shadow-sm">
        <CardHeader className="gap-3">
          <div className="flex flex-col items-center gap-1.5 text-center">
            <div className="flex items-center gap-2">
              <Wind className="size-5 text-sky-600" strokeWidth={2.6} />
              <CardTitle className="text-2xl font-semibold leading-none">Windrunner</CardTitle>
            </div>
            <p className="text-sm text-muted-foreground">Change your password</p>
          </div>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <label className="block text-sm font-semibold">New password</label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="-mr-2 gap-1.5 px-2 text-muted-foreground"
                  onClick={() => setShowNewPassword((current) => !current)}
                  aria-label={showNewPassword ? 'Hide password' : 'Show password'}
                >
                  {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  {showNewPassword ? 'Hide' : 'Show'}
                </Button>
              </div>
              <Input
                type={showNewPassword ? 'text' : 'password'}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <label className="block text-sm font-semibold">Confirm password</label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="-mr-2 gap-1.5 px-2 text-muted-foreground"
                  onClick={() => setShowConfirmPassword((current) => !current)}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  {showConfirmPassword ? 'Hide' : 'Show'}
                </Button>
              </div>
              <Input
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            {errorMessage ? (
              <div className="border border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {errorMessage}
              </div>
            ) : null}
            <Button
              type="submit"
              disabled={isSubmitting}
              className="w-full gap-2"
            >
              <KeyRound className="h-4 w-4" />
              {isSubmitting ? 'Updating...' : 'Update password'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}

function App() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [isAuthLoading, setIsAuthLoading] = useState(true)

  useEffect(() => {
    let isMounted = true

    async function loadCurrentUser() {
      try {
        const user = await fetchCurrentUser()
        if (isMounted) {
          setCurrentUser(user)
        }
      } catch {
        if (isMounted) {
          setCurrentUser(null)
        }
      } finally {
        if (isMounted) {
          setIsAuthLoading(false)
        }
      }
    }

    void loadCurrentUser()

    return () => {
      isMounted = false
    }
  }, [])

  if (isAuthLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-muted/20 p-6">
        <Card>
          <CardContent className="py-8 text-sm text-muted-foreground">Loading session...</CardContent>
        </Card>
      </main>
    )
  }

  return (
    <NotificationProvider key={currentUser?.id ?? 'anonymous'} currentUser={currentUser}>
      <>
    <Routes>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route
        path="/login"
        element={<LoginPage currentUser={currentUser} onLogin={setCurrentUser} />}
      />
      <Route
        path="/change-password"
        element={<ChangePasswordPage currentUser={currentUser} onUserChange={setCurrentUser} />}
      />
      <Route
        path="/app"
        element={<ProtectedApp currentUser={currentUser} />}
      >
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<HomePage displayName={currentUser?.displayName} />} />
        <Route path="ask-ai" element={<AskPage />} />
        <Route path="projects" element={<ProjectsPage currentUser={currentUser} />} />
        <Route path="projects/:projectId" element={<ProjectWorkspacePage />} />
        <Route path="projects/:projectId/settings" element={<ProjectSettingsPage currentUser={currentUser} />} />
        <Route path="ai-efficiency" element={<AIEfficiencyPage />} />
        <Route path="subscriptions" element={<SubscriptionsPage />} />
        <Route path="my-work" element={<MyWorkPage />} />
        <Route
          path="teams"
          element={<TeamsPage currentUser={currentUser} />}
        />
        <Route
          path="teams/:teamId"
          element={<TeamDetailsPage currentUser={currentUser} />}
        />
        <Route
          path="account"
          element={<MyAccountPage currentUser={currentUser} onUserChange={setCurrentUser} />}
        />
        <Route
          path="users"
          element={<UsersPage currentUser={currentUser} />}
        />
        <Route
          path="audit-logs"
          element={
            <AdminOnlyRoute currentUser={currentUser}>
              <AuditLogsPage />
            </AdminOnlyRoute>
          }
        />
      </Route>
      <Route path="/workspace/*" element={<LegacyWorkspaceRedirect />} />
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
    <Toaster />
      </>
    </NotificationProvider>
  )
}

export default App
