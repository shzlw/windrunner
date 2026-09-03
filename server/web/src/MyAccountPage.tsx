import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { toast } from 'sonner'
import { ChevronLeft, ChevronRight, Copy, Cpu, Eye, EyeOff, KeyRound, LogOut, Mic, Plug, RefreshCw, Server, ShieldAlert, X } from 'lucide-react'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import LanguageSelect from '@/components/LanguageSelect'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  createMyApiKey,
  fetchCurrentUser,
  getSystemInformation,
  listMyApiKeys,
  logout,
  revokeMyApiKey,
  type ApiKey,
  type ApiKeyScope,
  type AuthUser,
  type CreatedApiKey,
  type SystemInformation,
  updatePassword,
} from '@/lib/api'
import { translateRole, translateStatus } from '@/i18n/labels'

function displayValue(value: string | null | undefined) {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function accountInitials(user: AuthUser) {
  const source = displayValue(user.displayName) ?? displayValue(user.username) ?? displayValue(user.email) ?? '?'
  const parts = source.split(/\s+/).filter(Boolean)
  const initials = parts.slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('')
  return initials || '?'
}

function isAdminLike(user: AuthUser | null) {
  const role = user?.globalRole?.toUpperCase()
  return role === 'ADMIN' || role === 'SUPERADMIN'
}

function formatDateTime(value: string | null | undefined, emptyLabel: string) {
  if (!value) {
    return emptyLabel
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

const API_KEY_SCOPE_GROUPS: Array<{
  label: string
  options: Array<{ value: ApiKeyScope; label: string; description: string }>
}> = [
  {
    label: 'Teams',
    options: [
      { value: 'teams:read', label: 'Read teams', description: 'Read teams from the external API.' },
      { value: 'teams:write', label: 'Write teams', description: 'Create, update, and delete teams.' },
      { value: 'team_members:read', label: 'Read members', description: 'Read team membership lists.' },
      { value: 'team_members:write', label: 'Write members', description: 'Add and remove team members.' },
      { value: 'team_projects:read', label: 'Read projects', description: 'Read projects linked to teams.' },
    ],
  },
  {
    label: 'Users',
    options: [
      { value: 'users:read', label: 'Resolve users', description: 'Resolve limited identity information by user ID.' },
    ],
  },
  {
    label: 'Projects',
    options: [
      { value: 'projects:read', label: 'Read projects', description: 'Read projects visible to the key owner.' },
      { value: 'projects:write', label: 'Write projects', description: 'Create projects and update owned projects.' },
      { value: 'project_access:read', label: 'Read access', description: 'Read project members and assigned teams.' },
      { value: 'project_access:write', label: 'Write access', description: 'Manage project members and assigned teams.' },
    ],
  },
  {
    label: 'Work items',
    options: [
      { value: 'work_items:read', label: 'Read work items', description: 'Read structured work items and the outline.' },
      { value: 'work_items:write', label: 'Write work items', description: 'Create, update, move, and delete work items.' },
    ],
  },
  {
    label: 'Entries',
    options: [
      { value: 'entries:read', label: 'Read entries', description: 'Read comments, answers, evidence, and other entries.' },
      { value: 'entries:write', label: 'Write entries', description: 'Create, update, and delete entries.' },
    ],
  },
  {
    label: 'Relationships',
    options: [
      { value: 'relationships:read', label: 'Read relationships', description: 'Read dependencies, blockers, answers, and other semantic links.' },
      { value: 'relationships:write', label: 'Write relationships', description: 'Create and delete semantic relationships.' },
    ],
  },
  {
    label: 'Audit',
    options: [
      { value: 'audit_logs:read', label: 'Read audit logs', description: 'Read global or project audit logs.' },
    ],
  },
]

const TOTAL_SCOPE_COUNT = API_KEY_SCOPE_GROUPS.reduce((sum, group) => sum + group.options.length, 0)

const ALL_SCOPE_VALUES = API_KEY_SCOPE_GROUPS.flatMap((group) => group.options.map((option) => option.value))

const scopeGroupKeys: Record<string, string> = {
  Teams: 'account.scopeGroups.teams',
  Users: 'account.scopeGroups.users',
  Projects: 'account.scopeGroups.projects',
  'Work items': 'account.scopeGroups.workItems',
  Entries: 'account.scopeGroups.entries',
  Relationships: 'account.scopeGroups.relationships',
  Audit: 'account.scopeGroups.audit',
}

const scopeOptionKeys: Record<ApiKeyScope, { label: string; description: string }> = {
  'teams:read': { label: 'account.scopeOptions.teamsReadLabel', description: 'account.scopeOptions.teamsReadDescription' },
  'teams:write': { label: 'account.scopeOptions.teamsWriteLabel', description: 'account.scopeOptions.teamsWriteDescription' },
  'team_members:read': { label: 'account.scopeOptions.teamMembersReadLabel', description: 'account.scopeOptions.teamMembersReadDescription' },
  'team_members:write': { label: 'account.scopeOptions.teamMembersWriteLabel', description: 'account.scopeOptions.teamMembersWriteDescription' },
  'team_projects:read': { label: 'account.scopeOptions.teamProjectsReadLabel', description: 'account.scopeOptions.teamProjectsReadDescription' },
  'users:read': { label: 'account.scopeOptions.usersReadLabel', description: 'account.scopeOptions.usersReadDescription' },
  'projects:read': { label: 'account.scopeOptions.projectsReadLabel', description: 'account.scopeOptions.projectsReadDescription' },
  'projects:write': { label: 'account.scopeOptions.projectsWriteLabel', description: 'account.scopeOptions.projectsWriteDescription' },
  'project_access:read': { label: 'account.scopeOptions.projectAccessReadLabel', description: 'account.scopeOptions.projectAccessReadDescription' },
  'project_access:write': { label: 'account.scopeOptions.projectAccessWriteLabel', description: 'account.scopeOptions.projectAccessWriteDescription' },
  'work_items:read': { label: 'account.scopeOptions.workItemsReadLabel', description: 'account.scopeOptions.workItemsReadDescription' },
  'work_items:write': { label: 'account.scopeOptions.workItemsWriteLabel', description: 'account.scopeOptions.workItemsWriteDescription' },
  'entries:read': { label: 'account.scopeOptions.entriesReadLabel', description: 'account.scopeOptions.entriesReadDescription' },
  'entries:write': { label: 'account.scopeOptions.entriesWriteLabel', description: 'account.scopeOptions.entriesWriteDescription' },
  'relationships:read': { label: 'account.scopeOptions.relationshipsReadLabel', description: 'account.scopeOptions.relationshipsReadDescription' },
  'relationships:write': { label: 'account.scopeOptions.relationshipsWriteLabel', description: 'account.scopeOptions.relationshipsWriteDescription' },
  'audit_logs:read': { label: 'account.scopeOptions.auditLogsReadLabel', description: 'account.scopeOptions.auditLogsReadDescription' },
}

type PasswordFieldProps = {
  label: string
  value: string
  onChange: (value: string) => void
  visible: boolean
  onToggleVisibility: () => void
  autoComplete: string
  required?: boolean
}

function PasswordField({
  label,
  value,
  onChange,
  visible,
  onToggleVisibility,
  autoComplete,
  required = false,
}: PasswordFieldProps) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <label className="block text-sm font-semibold">{label}</label>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="-mr-2 gap-1.5 px-2 text-muted-foreground"
          onClick={onToggleVisibility}
          aria-label={visible ? t('auth.hidePassword') : t('auth.showPassword')}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          {visible ? t('common.hide') : t('common.show')}
        </Button>
      </div>
      <Input
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        required={required}
      />
    </div>
  )
}

type MyAccountPageProps = {
  currentUser: AuthUser | null
  onUserChange: (user: AuthUser | null) => void
}

export default function MyAccountPage({ currentUser, onUserChange }: MyAccountPageProps) {
  const { t } = useTranslation()
  const [user, setUser] = useState<AuthUser | null>(currentUser)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showCurrentPassword, setShowCurrentPassword] = useState(false)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [isUpdatingPassword, setIsUpdatingPassword] = useState(false)
  const [apiKeys, setApiKeys] = useState<ApiKey[]>([])
  const [isLoadingApiKeys, setIsLoadingApiKeys] = useState(false)
  const [apiKeyPage, setApiKeyPage] = useState(0)
  const [apiKeyPageSize, setApiKeyPageSize] = useState(5)
  const [apiKeyTotalItems, setApiKeyTotalItems] = useState(0)
  const [apiKeyTotalPages, setApiKeyTotalPages] = useState(0)
  const [apiKeyName, setApiKeyName] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<ApiKeyScope[]>(['teams:read'])
  const [createdApiKey, setCreatedApiKey] = useState<CreatedApiKey | null>(null)
  const [isCreatingApiKey, setIsCreatingApiKey] = useState(false)
  const [isCreateApiKeyDialogOpen, setIsCreateApiKeyDialogOpen] = useState(false)
  const [revokingApiKeyId, setRevokingApiKeyId] = useState<string | null>(null)
  const [systemInformation, setSystemInformation] = useState<SystemInformation | null>(null)
  const [isLoadingSystemInformation, setIsLoadingSystemInformation] = useState(false)

  async function loadApiKeys(nextPage = apiKeyPage, nextPageSize = apiKeyPageSize) {
    setIsLoadingApiKeys(true)
    try {
      const response = await listMyApiKeys(nextPage, nextPageSize)
      if (response.items.length === 0 && nextPage > 0 && response.totalPages > 0 && nextPage >= response.totalPages) {
        await loadApiKeys(response.totalPages - 1, nextPageSize)
        return
      }
      setApiKeys(response.items)
      setApiKeyPage(response.page)
      setApiKeyTotalItems(response.totalItems)
      setApiKeyTotalPages(response.totalPages)
    } catch (loadError) {
      setApiKeys([])
      setApiKeyTotalItems(0)
      setApiKeyTotalPages(0)
      toast.error(loadError instanceof Error ? loadError.message : t('account.failedLoadKeys'))
    } finally {
      setIsLoadingApiKeys(false)
    }
  }

  async function loadUser() {
    setIsLoading(true)

    try {
      const nextUser = await fetchCurrentUser()
      setUser(nextUser)
      onUserChange(nextUser)
      await loadApiKeys(0, apiKeyPageSize)
      if (isAdminLike(nextUser)) {
        setIsLoadingSystemInformation(true)
        try {
          setSystemInformation(await getSystemInformation())
        } catch (loadError) {
          setSystemInformation(null)
          toast.error(loadError instanceof Error ? loadError.message : t('account.failedLoadSystem'))
        } finally {
          setIsLoadingSystemInformation(false)
        }
      } else {
        setSystemInformation(null)
      }
    } catch (loadError) {
      setUser(null)
      onUserChange(null)
      setApiKeys([])
      toast.error(loadError instanceof Error ? loadError.message : t('account.failedLoadUser'))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void loadUser()
    })
  }, [onUserChange])

  async function handleLogout() {
    setIsLoggingOut(true)
    try {
      await logout()
      onUserChange(null)
      window.location.href = '/login'
    } finally {
      setIsLoggingOut(false)
    }
  }

  function updateSelectedScope(scope: ApiKeyScope, checked: boolean) {
    setSelectedScopes((currentScopes) => {
      if (checked) {
        return currentScopes.includes(scope) ? currentScopes : [...currentScopes, scope]
      }

      return currentScopes.filter((currentScope) => currentScope !== scope)
    })
  }

  function toggleGroup(groupLabel: string, checked: boolean) {
    const group = API_KEY_SCOPE_GROUPS.find((candidate) => candidate.label === groupLabel)
    if (!group) {
      return
    }

    const groupScopes = group.options.map((option) => option.value)
    setSelectedScopes((currentScopes) => {
      if (checked) {
        return [...new Set([...currentScopes, ...groupScopes])]
      }

      return currentScopes.filter((scope) => !groupScopes.includes(scope))
    })
  }

  function handleToggleAllScopes() {
    setSelectedScopes((currentScopes) => {
      if (currentScopes.length === TOTAL_SCOPE_COUNT) {
        return []
      }

      return [...ALL_SCOPE_VALUES]
    })
  }

  async function handleApiKeySubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!apiKeyName.trim()) {
      toast.error(t('account.keyNameRequired'))
      return
    }
    if (selectedScopes.length === 0) {
      toast.error(t('account.scopeRequired'))
      return
    }

    setIsCreatingApiKey(true)
    try {
      const nextApiKey = await createMyApiKey({
        name: apiKeyName,
        scopes: selectedScopes,
      })
      setCreatedApiKey(nextApiKey)
      setApiKeyName('')
      setSelectedScopes(['teams:read'])
      setIsCreateApiKeyDialogOpen(false)
      await loadApiKeys(apiKeyPage, apiKeyPageSize)
      toast.success(t('account.keyCreated'))
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : t('account.failedCreateKey'))
    } finally {
      setIsCreatingApiKey(false)
    }
  }

  async function handleCopyApiKey() {
    if (!createdApiKey?.rawKey) {
      return
    }

    await navigator.clipboard.writeText(createdApiKey.rawKey)
    toast.success(t('account.keyCopied'))
  }

  async function handleRevokeApiKey(apiKeyId: string) {
    setRevokingApiKeyId(apiKeyId)
    try {
      await revokeMyApiKey(apiKeyId)
      if (createdApiKey?.id === apiKeyId) {
        setCreatedApiKey(null)
      }
      await loadApiKeys()
      toast.success(t('account.keyRevoked'))
    } catch (revokeError) {
      toast.error(revokeError instanceof Error ? revokeError.message : t('account.failedRevokeKey'))
    } finally {
      setRevokingApiKeyId(null)
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!user?.mustChangePassword && !currentPassword.trim()) {
      toast.error(t('account.currentPasswordRequired'))
      return
    }
    if (!newPassword.trim()) {
      toast.error(t('account.newPasswordRequired'))
      return
    }
    if (newPassword.length < 6) {
      toast.error(t('auth.passwordMinLength'))
      return
    }
    if (newPassword !== confirmPassword) {
      toast.error(t('auth.passwordsMismatch'))
      return
    }

    setIsUpdatingPassword(true)
    try {
      const nextUser = await updatePassword(
        newPassword,
        user?.mustChangePassword ? undefined : currentPassword.trim()
      )
      setUser(nextUser)
      onUserChange(nextUser)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      toast.success(t('account.passwordUpdated'))
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : t('account.failedUpdatePassword'))
    } finally {
      setIsUpdatingPassword(false)
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center justify-between gap-3 border-b px-4 py-2 md:px-5">
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('account.pageTitle')}</h1>
        <Button
          variant="outline"
          onClick={() => void handleLogout()}
          disabled={isLoggingOut}
          className="gap-2 text-destructive hover:bg-destructive/5 hover:text-destructive"
        >
          <LogOut className="h-4 w-4" />
          {isLoggingOut ? t('account.signingOut') : t('account.signOut')}
        </Button>
      </div>

      <div className="min-w-0 flex-1 space-y-2 overflow-auto p-3 md:p-4">
        {isLoading ? (
          <div className="rounded-md border bg-background p-4">
            <div className="space-y-4">
              <Skeleton className="h-10 w-64" />
              <Skeleton className="h-44 w-full" />
            </div>
          </div>
        ) : null}

        {!isLoading && !user ? (
          <div className="flex min-h-64 flex-col items-center justify-center rounded-md border bg-muted/10 p-6 text-center">
            <p className="text-sm text-muted-foreground">{t('account.noUser')}</p>
          </div>
        ) : null}

        {user ? (
          <Tabs defaultValue="profile" className="gap-4">
            <TabsList variant="line" className="border-b">
              <TabsTrigger value="profile">{t('account.profile')}</TabsTrigger>
              <TabsTrigger value="password">{t('account.password')}</TabsTrigger>
              <TabsTrigger value="api-keys">{t('account.apiKeys')}</TabsTrigger>
              {isAdminLike(user) ? <TabsTrigger value="system">{t('account.system')}</TabsTrigger> : null}
            </TabsList>

            <TabsContent value="profile">
              <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                <div className="flex items-center gap-4">
                  <Avatar className="h-14 w-14 border">
                    <AvatarFallback className="bg-primary/10 text-base font-semibold text-primary">
                      {accountInitials(user)}
                    </AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <h2 className="truncate text-lg font-semibold leading-tight">
                        {displayValue(user.displayName) ?? displayValue(user.username) ?? t('account.account')}
                      </h2>
                      <Badge variant={user.mustChangePassword ? 'destructive' : 'outline'}>
                        {user.mustChangePassword ? t('account.passwordRequired') : user.status ? translateStatus(user.status, t) : t('status.active')}
                      </Badge>
                    </div>
                    <p className="truncate text-sm text-muted-foreground">
                      {displayValue(user.username) ?? 'unknown'}
                    </p>
                  </div>
                </div>

                <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
                  {([
                    { label: t('common.title'), value: displayValue(user.title) },
                    { label: t('usersPage.bio'), value: displayValue(user.bio) },
                    { label: t('common.email'), value: displayValue(user.email) },
                    { label: t('common.timezone'), value: displayValue(user.timezone) },
                    { label: t('common.role'), value: user.globalRole ? translateRole(user.globalRole, t) : null },
                    { label: t('common.status'), value: user.status ? translateStatus(user.status, t) : null },
                  ] satisfies Array<{ label: string; value: string | null }>).map(({ label, value }) => (
                    <div key={label} className="min-w-0">
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
                      <dd className="mt-0.5 break-words text-sm font-medium">
                        {value ?? <span className="italic font-normal text-muted-foreground">{t('common.notSet')}</span>}
                      </dd>
                    </div>
                  ))}
                </dl>

                <div className="border-t pt-4">
                  <h3 className="text-sm font-semibold">{t('language.preferencesTitle')}</h3>
                  <p className="mt-1 mb-4 text-sm text-muted-foreground">{t('language.description')}</p>
                  <LanguageSelect />
                </div>
              </section>
            </TabsContent>

            <TabsContent value="password">
              <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                {user.mustChangePassword ? (
                  <Alert variant="destructive">
                    <ShieldAlert className="h-4 w-4" />
                    <AlertTitle>{t('account.updatePasswordRequired')}</AlertTitle>
                    <AlertDescription>{t('account.updatePasswordDescription')}</AlertDescription>
                  </Alert>
                ) : (
                  <h3 className="flex items-center gap-2 text-sm font-semibold">
                    <KeyRound className="h-4 w-4" />
                    {t('auth.changePassword')}
                  </h3>
                )}

                <form className="max-w-3xl space-y-4" onSubmit={handlePasswordSubmit}>
                  {!user.mustChangePassword ? (
                    <PasswordField
                      label={t('account.currentPassword')}
                      value={currentPassword}
                      onChange={setCurrentPassword}
                      visible={showCurrentPassword}
                      onToggleVisibility={() => setShowCurrentPassword((current) => !current)}
                      autoComplete="current-password"
                      required
                    />
                  ) : null}
                  <PasswordField
                    label={t('auth.newPassword')}
                    value={newPassword}
                    onChange={setNewPassword}
                    visible={showNewPassword}
                    onToggleVisibility={() => setShowNewPassword((current) => !current)}
                    autoComplete="new-password"
                    required
                  />
                  <PasswordField
                    label={t('auth.confirmPassword')}
                    value={confirmPassword}
                    onChange={setConfirmPassword}
                    visible={showConfirmPassword}
                    onToggleVisibility={() => setShowConfirmPassword((current) => !current)}
                    autoComplete="new-password"
                    required
                  />
                  <Button type="submit" disabled={isUpdatingPassword} className="gap-2">
                    <KeyRound className="h-4 w-4" />
                    {isUpdatingPassword ? t('auth.updating') : t('auth.updatePassword')}
                  </Button>
                </form>
              </section>
            </TabsContent>

            {isAdminLike(user) ? (
              <TabsContent value="system">
                <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                  <div>
                    <h3 className="flex items-center gap-2 text-sm font-semibold">
                      <Server className="h-4 w-4" />
                      {t('account.systemInformation')}
                    </h3>
                  </div>

                  {isLoadingSystemInformation ? (
                    <div className="grid gap-5 sm:grid-cols-2">
                      <Skeleton className="h-14" />
                      <Skeleton className="h-14" />
                    </div>
                  ) : systemInformation ? (
                    <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{t('account.serverVersion')}</dt>
                        <dd className="mt-0.5 font-mono text-sm font-medium">{systemInformation.serverVersion}</dd>
                      </div>
                      <div>
                        <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <Cpu className="h-3.5 w-3.5" />
                          {t('account.llmProvider')}
                        </dt>
                        <dd className="mt-0.5 text-sm font-medium">
                          <span className="capitalize">{systemInformation.llmProvider}</span>
                          <span className="mx-1.5 text-muted-foreground">·</span>
                          <span className="font-mono text-xs">{systemInformation.llmModel}</span>
                          <span className="ml-2 text-xs font-normal text-muted-foreground">
                            ({systemInformation.llmAvailable ? t('account.available') : t('account.unavailable')})
                          </span>
                        </dd>
                      </div>
                      <div>
                        <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <Mic className="h-3.5 w-3.5" />
                          {t('account.audioTranscription')}
                        </dt>
                        <dd className="mt-0.5 text-sm font-medium">
                          <span className="capitalize">{systemInformation.audioTranscriptionProvider || '—'}</span>
                          <span className="mx-1.5 text-muted-foreground">·</span>
                          <span className="font-mono text-xs">{displayValue(systemInformation.audioTranscriptionModel) ?? '—'}</span>
                          <span className="ml-2 text-xs font-normal text-muted-foreground">
                            ({systemInformation.audioTranscriptionAvailable ? t('account.available') : t('account.unavailable')})
                          </span>
                        </dd>
                      </div>
                    </dl>
                  ) : (
                    <p className="text-sm text-muted-foreground">{t('account.systemUnavailable')}</p>
                  )}
                </section>
              </TabsContent>
            ) : null}

            <TabsContent value="api-keys">
              <section className="max-w-5xl space-y-4 rounded-md border bg-background p-4">
                <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                  <div className="min-w-0">
                    <h3 className="flex items-center gap-2 text-sm font-semibold">
                      <Plug className="h-4 w-4" />
                      {t('account.apiKeys')}
                    </h3>
                    <p className="mt-1 text-sm text-muted-foreground">{t('account.apiKeysDescription')}</p>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button variant="outline" size="sm" onClick={() => void loadApiKeys()} disabled={isLoadingApiKeys} className="gap-2">
                      <RefreshCw className={`h-4 w-4 ${isLoadingApiKeys ? 'animate-spin' : ''}`} />
                      {isLoadingApiKeys ? t('account.refreshing') : t('common.refresh')}
                    </Button>
                    <Button size="sm" onClick={() => setIsCreateApiKeyDialogOpen(true)} className="gap-2">
                      <KeyRound className="h-4 w-4" />
                      {t('account.createApiKey')}
                    </Button>
                  </div>
                </div>

                {createdApiKey ? (
                  <div className="space-y-2 rounded-md border border-primary/30 bg-primary/5 p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-2">
                        <KeyRound className="h-4 w-4 shrink-0 text-primary" />
                        <p className="text-sm font-semibold">{t('account.newKey')}</p>
                      </div>
                      <Button type="button" variant="outline" size="sm" onClick={() => void handleCopyApiKey()} className="gap-2">
                        <Copy className="h-4 w-4" />
                        {t('account.copy')}
                      </Button>
                    </div>
                    <p className="break-all rounded-md border bg-background/80 px-2 py-1.5 font-mono text-xs text-primary">{createdApiKey.rawKey}</p>
                    <p className="text-xs text-muted-foreground">{t('account.shownOnce')}</p>
                  </div>
                ) : null}

                <div className="space-y-3 border-t pt-4">
                  <div className="flex items-center justify-between gap-3">
                    <h4 className="text-sm font-semibold">{t('account.existingKeys')}</h4>
                    <span className="text-xs text-muted-foreground">{t('account.total', { count: apiKeyTotalItems })}</span>
                  </div>

                  {isLoadingApiKeys ? (
                    <div className="space-y-2">
                      <Skeleton className="h-20 w-full" />
                      <Skeleton className="h-20 w-full" />
                    </div>
                  ) : null}

                  {!isLoadingApiKeys && apiKeys.length === 0 ? (
                    <div className="flex min-h-32 flex-col items-center justify-center gap-2 rounded-md border border-dashed p-6 text-center">
                      <KeyRound className="h-5 w-5 text-muted-foreground" />
                      <p className="text-sm text-muted-foreground">{t('account.noKeys')}</p>
                      <p className="text-xs text-muted-foreground">{t('account.createKeyHint')}</p>
                      <Button type="button" variant="outline" size="sm" onClick={() => setIsCreateApiKeyDialogOpen(true)} className="mt-1 gap-2">
                        <KeyRound className="h-4 w-4" />
                        {t('account.createApiKey')}
                      </Button>
                    </div>
                  ) : null}

                  {!isLoadingApiKeys && apiKeys.map((apiKey) => {
                    const isRevoked = apiKey.status === 'REVOKED'

                    return (
                      <article key={apiKey.id} className="overflow-hidden rounded-md border bg-background">
                        <div className="flex min-w-0 items-start gap-3 px-3 py-2.5">
                          <KeyRound className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                          <div className="min-w-0 flex-1">
                            <div className="flex min-w-0 flex-wrap items-center gap-2">
                              <h4 className="min-w-0 truncate text-sm font-semibold" title={apiKey.name}>{apiKey.name}</h4>
                              <Badge variant={isRevoked ? 'secondary' : 'outline'}>{translateStatus(apiKey.status, t)}</Badge>
                            </div>
                            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                              <span>{t('common.created')}: {formatDateTime(apiKey.createdAt, t('common.never'))}</span>
                              <span>{t('account.lastUsed')}: {formatDateTime(apiKey.lastUsedAt, t('common.never'))}</span>
                            </div>
                          </div>

                          {!isRevoked ? (
                            <DeleteConfirmPopover
                              title={t('account.revokeKey')}
                              description={t('account.revokeDescription')}
                              confirmLabel={t('account.revoke')}
                              disabled={revokingApiKeyId === apiKey.id}
                              trigger={(
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="icon-sm"
                                  disabled={revokingApiKeyId === apiKey.id}
                                  className="shrink-0 text-destructive hover:bg-destructive/5 hover:text-destructive"
                                  aria-label={revokingApiKeyId === apiKey.id ? t('account.revoking') : t('account.revoke')}
                                  title={revokingApiKeyId === apiKey.id ? t('account.revoking') : t('account.revoke')}
                                >
                                  <X className="h-4 w-4" />
                                </Button>
                              )}
                              onConfirm={() => handleRevokeApiKey(apiKey.id)}
                            />
                          ) : null}
                        </div>

                        <div className="border-t bg-muted/10 px-3 py-2.5">
                          <div className="mb-2 flex items-center justify-between gap-2">
                            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{t('account.permissions')}</span>
                            <span className="text-xs text-muted-foreground">{t('account.permissionCount', { count: apiKey.scopes.length })}</span>
                          </div>
                          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                            {API_KEY_SCOPE_GROUPS.map((group) => {
                              const selectedOptions = group.options.filter((option) => apiKey.scopes.includes(option.value))
                              if (selectedOptions.length === 0) {
                                return null
                              }

                              return (
                                <div key={group.label} className="min-w-0 rounded-md border bg-background/70 px-2.5 py-2">
                                  <p className="mb-1 text-xs font-semibold text-foreground">{t(scopeGroupKeys[group.label] ?? group.label)}</p>
                                  <div className="flex flex-wrap gap-1">
                                    {selectedOptions.map((option) => (
                                      <Badge key={option.value} variant="secondary" className="h-5 px-1.5 text-[10px] font-medium">
                                        {t(scopeOptionKeys[option.value].label)}
                                      </Badge>
                                    ))}
                                  </div>
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      </article>
                    )
                  })}

                  {!isLoadingApiKeys && apiKeyTotalPages > 0 ? (
                    <div className="flex justify-end border-t pt-2 text-sm">
                      <div className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="icon-sm"
                          onClick={() => void loadApiKeys(Math.max(0, apiKeyPage - 1), apiKeyPageSize)}
                          disabled={apiKeyPage === 0 || isLoadingApiKeys}
                          aria-label={t('common.previousPage')}
                        >
                          <ChevronLeft className="h-4 w-4" />
                        </Button>
                        <span className="text-sm text-muted-foreground">
                          {t('common.pageOf', { page: apiKeyPage + 1, total: Math.max(apiKeyTotalPages, 1) })}
                        </span>
                        <Button
                          type="button"
                          variant="outline"
                          size="icon-sm"
                          onClick={() => void loadApiKeys(apiKeyPage + 1, apiKeyPageSize)}
                          disabled={isLoadingApiKeys || apiKeyPage >= apiKeyTotalPages - 1}
                          aria-label={t('common.nextPage')}
                        >
                          <ChevronRight className="h-4 w-4" />
                        </Button>
                        <div className="ml-3 border-l pl-3">
                          <NativeSelect
                            className="h-8 w-20"
                            value={String(apiKeyPageSize)}
                            onChange={(event) => {
                              const nextPageSize = Number(event.target.value)
                              setApiKeyPageSize(nextPageSize)
                              void loadApiKeys(0, nextPageSize)
                            }}
                            disabled={isLoadingApiKeys}
                            aria-label={t('common.pageSize')}
                          >
                            <NativeSelectOption value="5">5</NativeSelectOption>
                            <NativeSelectOption value="10">10</NativeSelectOption>
                            <NativeSelectOption value="25">25</NativeSelectOption>
                          </NativeSelect>
                        </div>
                      </div>
                    </div>
                  ) : null}
                </div>
              </section>

              <Dialog open={isCreateApiKeyDialogOpen} onOpenChange={setIsCreateApiKeyDialogOpen}>
                <DialogContent className="max-h-[calc(100vh-2rem)] min-w-0 overflow-y-auto sm:max-w-2xl">
                  <DialogHeader>
                    <DialogTitle>{t('account.createApiKey')}</DialogTitle>
                    <DialogDescription>{t('account.apiKeysDescription')}</DialogDescription>
                  </DialogHeader>

                  <form id="create-api-key-form" className="space-y-4" onSubmit={handleApiKeySubmit}>
                    <div className="space-y-2">
                      <label htmlFor="api-key-name" className="block text-sm font-semibold">{t('account.keyName')}</label>
                      <Input
                        id="api-key-name"
                        autoFocus
                        value={apiKeyName}
                        onChange={(event) => setApiKeyName(event.target.value)}
                        required
                      />
                    </div>

                    <div className="space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="text-sm font-semibold">{t('account.permissions')}</p>
                          <p className="text-xs text-muted-foreground">
                            {t('account.scopesSelected', { selected: selectedScopes.length, total: TOTAL_SCOPE_COUNT })}
                          </p>
                        </div>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="px-2 text-muted-foreground"
                          onClick={handleToggleAllScopes}
                        >
                          {selectedScopes.length === TOTAL_SCOPE_COUNT ? t('common.clearAll') : t('account.selectAll')}
                        </Button>
                      </div>

                      <div className="grid gap-2 sm:grid-cols-2">
                        {API_KEY_SCOPE_GROUPS.map((group) => {
                          const selectedInGroup = group.options.filter((option) => selectedScopes.includes(option.value))
                          const groupChecked = selectedInGroup.length === group.options.length
                          const groupIndeterminate = selectedInGroup.length > 0 && !groupChecked

                          return (
                            <div key={group.label} className="overflow-hidden rounded-md border bg-muted/5">
                              <div className="flex items-center justify-between gap-2 border-b bg-muted/25 px-3 py-2.5">
                                <label className="flex items-center gap-2 text-xs font-semibold text-foreground">
                                  <Checkbox
                                    checked={groupChecked}
                                    indeterminate={groupIndeterminate}
                                    onCheckedChange={(checked) => toggleGroup(group.label, checked === true)}
                                  />
                                  {t(scopeGroupKeys[group.label] ?? group.label)}
                                </label>
                                <span className="rounded-full bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground">
                                  {selectedInGroup.length}/{group.options.length}
                                </span>
                              </div>

                              <div className="space-y-0.5 p-1.5">
                                {group.options.map((option) => {
                                  const checkboxId = `api-key-scope-${option.value.replace(/[^a-z0-9]+/gi, '-')}`

                                  return (
                                    <div key={option.value} className="flex min-h-8 items-center gap-2 rounded-md px-1.5 py-1 hover:bg-muted/60">
                                      <Checkbox
                                        id={checkboxId}
                                        checked={selectedScopes.includes(option.value)}
                                        onCheckedChange={(checked) => updateSelectedScope(option.value, checked === true)}
                                      />
                                      <label htmlFor={checkboxId} className="min-w-0 cursor-pointer text-sm" title={t(scopeOptionKeys[option.value].description)}>
                                        {t(scopeOptionKeys[option.value].label)}
                                      </label>
                                    </div>
                                  )
                                })}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  </form>

                  <DialogFooter>
                    <Button type="button" variant="outline" onClick={() => setIsCreateApiKeyDialogOpen(false)} disabled={isCreatingApiKey}>
                      {t('common.cancel')}
                    </Button>
                    <Button type="submit" form="create-api-key-form" disabled={isCreatingApiKey} className="gap-2">
                      <KeyRound className="h-4 w-4" />
                      {isCreatingApiKey ? t('account.creating') : t('account.createApiKey')}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </TabsContent>

          </Tabs>
        ) : null}
      </div>
    </div>
  )
}
