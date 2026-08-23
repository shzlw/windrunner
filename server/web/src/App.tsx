import { useEffect, useState } from 'react'
import type { FormEvent, ReactElement } from 'react'
import { NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router'
import { Eye, EyeOff, Bookmark, FileClock, FolderOpen, KeyRound, ListTodo, TrendingUp, UserCircle, Users, UsersRound, Wind } from 'lucide-react'

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
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
import { Toaster } from '@/components/ui/sonner'
import { fetchCurrentUser, login, type AuthUser, updatePassword } from '@/lib/api'

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
import AssignedPage from './AssignedPage'
import NotificationCenter, { NotificationProvider } from './components/NotificationCenter'

const baseMenuItems = [
  { label: 'Projects', path: '/app/projects', icon: FolderOpen },
  { label: 'Assigned', path: '/app/assigned', icon: ListTodo },
  { label: 'Subscriptions', path: '/app/subscriptions', icon: Bookmark },
  { label: 'Teams', path: '/app/teams', icon: UsersRound },
  { label: 'Users', path: '/app/users', icon: Users },
]

const aiEfficiencyMenuItem = { label: 'AI Efficiency', path: '/app/ai-efficiency', icon: TrendingUp }


function isAdminLike(user: AuthUser | null) {
  return user?.globalRole === 'ADMIN' || user?.globalRole === 'SUPERADMIN'
}

function authRedirectPath(user: AuthUser | null) {
  if (!user) {
    return '/login'
  }

  return user.mustChangePassword ? '/change-password' : '/app/projects'
}

function AppLayout({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()
  const menuItems = isAdminLike(currentUser)
    ? [
        ...baseMenuItems,
        { label: 'Audit Logs', path: '/app/audit-logs', icon: FileClock },
        aiEfficiencyMenuItem,
      ]
    : [...baseMenuItems, aiEfficiencyMenuItem]
  const accountLabel = `@${currentUser?.displayName || currentUser?.username || 'account'}`

  return (
    <TooltipProvider>
      <SidebarProvider className="h-svh min-h-0 overflow-hidden">
        <Sidebar collapsible="icon">
          <SidebarHeader>
            <div className="flex h-12 items-center justify-between group-data-[collapsible=icon]:justify-center">
              <NavLink
                to="/app/projects"
                className="flex h-8 items-center gap-2 overflow-hidden rounded-md p-2 group-data-[collapsible=icon]:hidden"
              >
                <Wind className="size-4 shrink-0 text-sky-600" strokeWidth={2.6} />
                <div className="min-w-0 flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">Windrunner</span>
                </div>
              </NavLink>
              <SidebarTrigger className="-mr-1 shrink-0 group-data-[collapsible=icon]:m-0" />
            </div>
          </SidebarHeader>

          <SidebarContent>
            <SidebarGroup>
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

          <SidebarFooter>
            <SidebarSeparator />
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
          </SidebarFooter>

          <SidebarRail />
        </Sidebar>

        <SidebarInset className="min-h-0 min-w-0 overflow-hidden bg-white">
          <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4 md:hidden">
            <SidebarTrigger className="-ml-1" />
          </header>
          <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
            <Outlet />
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
    return <Navigate to="/app/projects" replace />
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
    : '/app/projects'

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
      navigate('/app/projects', { replace: true })
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
        <Route index element={<Navigate to="projects" replace />} />
        <Route path="projects" element={<ProjectsPage currentUser={currentUser} />} />
        <Route path="projects/:projectId" element={<ProjectWorkspacePage />} />
        <Route path="projects/:projectId/settings" element={<ProjectSettingsPage currentUser={currentUser} />} />
        <Route path="ai-efficiency" element={<AIEfficiencyPage />} />
        <Route path="subscriptions" element={<SubscriptionsPage />} />
        <Route path="assigned" element={<AssignedPage />} />
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
