import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { toast } from 'sonner'
import { Copy, Cpu, Eye, EyeOff, KeyRound, LogOut, Plug, RefreshCw, Server, ShieldAlert, X } from 'lucide-react'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import LanguageSelect from '@/components/LanguageSelect'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
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

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Never'
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
      { value: 'team_projects:write', label: 'Write projects', description: 'Link and unlink teams from projects.' },
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
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          {visible ? 'Hide' : 'Show'}
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
  const [apiKeyName, setApiKeyName] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<ApiKeyScope[]>(['teams:read'])
  const [createdApiKey, setCreatedApiKey] = useState<CreatedApiKey | null>(null)
  const [isCreatingApiKey, setIsCreatingApiKey] = useState(false)
  const [revokingApiKeyId, setRevokingApiKeyId] = useState<string | null>(null)
  const [systemInformation, setSystemInformation] = useState<SystemInformation | null>(null)
  const [isLoadingSystemInformation, setIsLoadingSystemInformation] = useState(false)

  async function loadApiKeys() {
    setIsLoadingApiKeys(true)
    try {
      const nextApiKeys = await listMyApiKeys()
      setApiKeys(nextApiKeys)
    } catch (loadError) {
      setApiKeys([])
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load API keys.')
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
      await loadApiKeys()
      if (isAdminLike(nextUser)) {
        setIsLoadingSystemInformation(true)
        try {
          setSystemInformation(await getSystemInformation())
        } catch (loadError) {
          setSystemInformation(null)
          toast.error(loadError instanceof Error ? loadError.message : 'Failed to load system information.')
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
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load user information.')
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
      toast.error('API key name is required.')
      return
    }
    if (selectedScopes.length === 0) {
      toast.error('Select at least one scope.')
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
      await loadApiKeys()
      toast.success('API key created.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to create API key.')
    } finally {
      setIsCreatingApiKey(false)
    }
  }

  async function handleCopyApiKey() {
    if (!createdApiKey?.rawKey) {
      return
    }

    await navigator.clipboard.writeText(createdApiKey.rawKey)
    toast.success('API key copied.')
  }

  async function handleRevokeApiKey(apiKeyId: string) {
    setRevokingApiKeyId(apiKeyId)
    try {
      await revokeMyApiKey(apiKeyId)
      if (createdApiKey?.id === apiKeyId) {
        setCreatedApiKey(null)
      }
      await loadApiKeys()
      toast.success('API key revoked.')
    } catch (revokeError) {
      toast.error(revokeError instanceof Error ? revokeError.message : 'Failed to revoke API key.')
    } finally {
      setRevokingApiKeyId(null)
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!user?.mustChangePassword && !currentPassword.trim()) {
      toast.error('Current password is required.')
      return
    }
    if (!newPassword.trim()) {
      toast.error('New password is required.')
      return
    }
    if (newPassword.length < 6) {
      toast.error('Password must be at least 6 characters.')
      return
    }
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match.')
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
      toast.success('Password updated.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to update password.')
    } finally {
      setIsUpdatingPassword(false)
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center justify-between gap-3 border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">My Account</h1>
        <Button
          variant="outline"
          onClick={() => void handleLogout()}
          disabled={isLoggingOut}
          className="gap-2 text-destructive hover:bg-destructive/5 hover:text-destructive"
        >
          <LogOut className="h-4 w-4" />
          {isLoggingOut ? 'Signing out...' : 'Sign out'}
        </Button>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
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
            <p className="text-sm text-muted-foreground">No authenticated user is available right now.</p>
          </div>
        ) : null}

        {user ? (
          <Tabs defaultValue="profile" className="gap-4">
            <TabsList variant="line" className="border-b">
              <TabsTrigger value="profile">Profile</TabsTrigger>
              <TabsTrigger value="password">Password</TabsTrigger>
              <TabsTrigger value="api-keys">API keys</TabsTrigger>
              {isAdminLike(user) ? <TabsTrigger value="system">System</TabsTrigger> : null}
            </TabsList>

            <TabsContent value="profile">
              <section className="max-w-4xl space-y-6 rounded-md border bg-background p-6">
                <div className="flex items-center gap-4">
                  <Avatar className="h-14 w-14 border">
                    <AvatarFallback className="bg-primary/10 text-base font-semibold text-primary">
                      {accountInitials(user)}
                    </AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <h2 className="truncate text-lg font-semibold leading-tight">
                        {displayValue(user.displayName) ?? displayValue(user.username) ?? 'Account'}
                      </h2>
                      <Badge variant={user.mustChangePassword ? 'destructive' : 'outline'}>
                        {user.mustChangePassword ? 'Password required' : user.status ?? 'Active'}
                      </Badge>
                    </div>
                    <p className="truncate text-sm text-muted-foreground">
                      @{displayValue(user.username) ?? 'unknown'}
                    </p>
                  </div>
                </div>

                <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
                  {([
                    { label: 'Title', value: displayValue(user.title) },
                    { label: 'Bio', value: displayValue(user.bio) },
                    { label: 'Email', value: displayValue(user.email) },
                    { label: 'Timezone', value: displayValue(user.timezone) },
                    { label: 'Role', value: displayValue(user.globalRole) },
                    { label: 'Status', value: displayValue(user.status) },
                  ] satisfies Array<{ label: string; value: string | null }>).map(({ label, value }) => (
                    <div key={label} className="min-w-0">
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
                      <dd className="mt-0.5 break-words text-sm font-medium">
                        {value ?? <span className="italic font-normal text-muted-foreground">Not set</span>}
                      </dd>
                    </div>
                  ))}
                </dl>

                <div className="border-t pt-5">
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
                    <AlertTitle>Update password required</AlertTitle>
                    <AlertDescription>You must set a new password before continuing.</AlertDescription>
                  </Alert>
                ) : (
                  <h3 className="flex items-center gap-2 text-sm font-semibold">
                    <KeyRound className="h-4 w-4" />
                    Change password
                  </h3>
                )}

                <form className="max-w-3xl space-y-4" onSubmit={handlePasswordSubmit}>
                  {!user.mustChangePassword ? (
                    <PasswordField
                      label="Current password"
                      value={currentPassword}
                      onChange={setCurrentPassword}
                      visible={showCurrentPassword}
                      onToggleVisibility={() => setShowCurrentPassword((current) => !current)}
                      autoComplete="current-password"
                      required
                    />
                  ) : null}
                  <PasswordField
                    label="New password"
                    value={newPassword}
                    onChange={setNewPassword}
                    visible={showNewPassword}
                    onToggleVisibility={() => setShowNewPassword((current) => !current)}
                    autoComplete="new-password"
                    required
                  />
                  <PasswordField
                    label="Confirm password"
                    value={confirmPassword}
                    onChange={setConfirmPassword}
                    visible={showConfirmPassword}
                    onToggleVisibility={() => setShowConfirmPassword((current) => !current)}
                    autoComplete="new-password"
                    required
                  />
                  <Button type="submit" disabled={isUpdatingPassword} className="gap-2">
                    <KeyRound className="h-4 w-4" />
                    {isUpdatingPassword ? 'Updating...' : 'Update password'}
                  </Button>
                </form>
              </section>
            </TabsContent>

            {isAdminLike(user) ? (
              <TabsContent value="system">
                <section className="max-w-4xl space-y-5 rounded-md border bg-background p-6">
                  <div>
                    <h3 className="flex items-center gap-2 text-sm font-semibold">
                      <Server className="h-4 w-4" />
                      System information
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
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Server version</dt>
                        <dd className="mt-0.5 font-mono text-sm font-medium">{systemInformation.serverVersion}</dd>
                      </div>
                      <div>
                        <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <Cpu className="h-3.5 w-3.5" />
                          LLM provider
                        </dt>
                        <dd className="mt-0.5 text-sm font-medium">
                          <span className="capitalize">{systemInformation.llmProvider}</span>
                          <span className="mx-1.5 text-muted-foreground">·</span>
                          <span className="font-mono text-xs">{systemInformation.llmModel}</span>
                          <span className="ml-2 text-xs font-normal text-muted-foreground">
                            ({systemInformation.llmAvailable ? 'available' : 'unavailable'})
                          </span>
                        </dd>
                      </div>
                    </dl>
                  ) : (
                    <p className="text-sm text-muted-foreground">System information is unavailable.</p>
                  )}
                </section>
              </TabsContent>
            ) : null}

            <TabsContent value="api-keys">
              <section className="max-w-5xl space-y-4 rounded-md border bg-background p-4">
                <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                  <h3 className="flex items-center gap-2 text-sm font-semibold">
                    <Plug className="h-4 w-4" />
                    API keys
                  </h3>
                  <Button variant="outline" size="sm" onClick={() => void loadApiKeys()} disabled={isLoadingApiKeys} className="gap-2">
                    <RefreshCw className={`h-4 w-4 ${isLoadingApiKeys ? 'animate-spin' : ''}`} />
                    {isLoadingApiKeys ? 'Refreshing...' : 'Refresh'}
                  </Button>
                </div>

                <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
                  <form className="space-y-4" onSubmit={handleApiKeySubmit}>
                    <div className="space-y-3">
                      <label className="block text-sm font-semibold">Key name</label>
                      <Input
                        value={apiKeyName}
                        onChange={(event) => setApiKeyName(event.target.value)}
                        required
                      />
                    </div>

                    <div className="space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <p className="text-sm font-semibold">Scopes</p>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground">
                            {selectedScopes.length} of {TOTAL_SCOPE_COUNT} selected
                          </span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="px-2 text-muted-foreground"
                            onClick={handleToggleAllScopes}
                          >
                            {selectedScopes.length === TOTAL_SCOPE_COUNT ? 'Clear all' : 'Select all'}
                          </Button>
                        </div>
                      </div>

                      <div className="space-y-4">
                        {API_KEY_SCOPE_GROUPS.map((group) => {
                          const selectedInGroup = group.options.filter((option) => selectedScopes.includes(option.value))
                          const groupChecked = selectedInGroup.length === group.options.length
                          const groupIndeterminate = selectedInGroup.length > 0 && !groupChecked

                          return (
                            <div key={group.label} className="space-y-2">
                              <div className="flex items-center justify-between gap-3">
                                <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                                  <Checkbox
                                    checked={groupChecked}
                                    indeterminate={groupIndeterminate}
                                    onCheckedChange={(checked) => toggleGroup(group.label, checked === true)}
                                  />
                                  {group.label}
                                </label>
                                <span className="text-xs text-muted-foreground">
                                  {selectedInGroup.length}/{group.options.length}
                                </span>
                              </div>

                              <div className="grid gap-1 sm:grid-cols-2">
                                {group.options.map((option) => {
                                  const checkboxId = `api-key-scope-${option.value.replace(/[^a-z0-9]+/gi, '-')}`

                                  return (
                                    <div key={option.value} className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-muted/60">
                                      <Checkbox
                                        id={checkboxId}
                                        checked={selectedScopes.includes(option.value)}
                                        onCheckedChange={(checked) => updateSelectedScope(option.value, checked === true)}
                                      />
                                      <label htmlFor={checkboxId} className="min-w-0 cursor-pointer text-sm" title={option.description}>
                                        {option.label}
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

                    <Button type="submit" disabled={isCreatingApiKey} className="gap-2">
                      <KeyRound className="h-4 w-4" />
                      {isCreatingApiKey ? 'Creating...' : 'Create API key'}
                    </Button>

                    {createdApiKey ? (
                      <div className="space-y-3 rounded-md border border-primary/30 bg-primary/5 p-3">
                        <div className="flex items-center justify-between gap-3">
                          <p className="text-sm font-semibold">New key</p>
                          <Button type="button" variant="outline" size="sm" onClick={() => void handleCopyApiKey()} className="gap-2">
                            <Copy className="h-4 w-4" />
                            Copy
                          </Button>
                        </div>
                        <p className="break-all font-mono text-xs text-primary">{createdApiKey.rawKey}</p>
                        <p className="text-xs text-muted-foreground">Shown once. Store it before leaving this page.</p>
                      </div>
                    ) : null}
                  </form>

                  <div className="space-y-3">
                    <div className="flex items-center justify-between gap-3">
                      <h4 className="text-sm font-semibold">Existing keys</h4>
                      <span className="text-xs text-muted-foreground">{apiKeys.length} total</span>
                    </div>

                    {isLoadingApiKeys ? (
                      <div className="space-y-3">
                        <Skeleton className="h-20 w-full" />
                        <Skeleton className="h-20 w-full" />
                      </div>
                    ) : null}

                    {!isLoadingApiKeys && apiKeys.length === 0 ? (
                      <div className="flex min-h-32 flex-col items-center justify-center gap-2 rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
                        <p>No API keys created.</p>
                        <p className="text-xs">Create one above to get started.</p>
                      </div>
                    ) : null}

                    {!isLoadingApiKeys && apiKeys.map((apiKey) => {
                      const isRevoked = apiKey.status === 'REVOKED'

                      return (
                        <article key={apiKey.id} className="rounded-md border bg-background px-4 py-3">
                          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(220px,280px)_auto] lg:items-center">
                            <div className="min-w-0 space-y-2">
                              <div className="flex min-w-0 flex-wrap items-center gap-2">
                                <h4 className="min-w-0 truncate text-sm font-semibold" title={apiKey.name}>{apiKey.name}</h4>
                                <Badge variant={isRevoked ? 'secondary' : 'outline'}>{apiKey.status}</Badge>
                              </div>
                              <div className="flex flex-wrap gap-1.5">
                                {apiKey.scopes.map((scope) => (
                                  <Badge key={scope} variant="secondary">{scope}</Badge>
                                ))}
                              </div>
                            </div>

                            <dl className="grid gap-2 text-xs text-muted-foreground sm:grid-cols-2 lg:grid-cols-1">
                              <div>
                                <dt className="font-medium text-foreground">Created</dt>
                                <dd>{formatDateTime(apiKey.createdAt)}</dd>
                              </div>
                              <div>
                                <dt className="font-medium text-foreground">Last used</dt>
                                <dd>{formatDateTime(apiKey.lastUsedAt)}</dd>
                              </div>
                            </dl>

                            <div className="flex justify-end">
                              <DeleteConfirmPopover
                                title="Revoke API key?"
                                description="This key will stop working immediately."
                                confirmLabel="Revoke"
                                disabled={isRevoked || revokingApiKeyId === apiKey.id}
                                trigger={(
                                  <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    disabled={isRevoked || revokingApiKeyId === apiKey.id}
                                    className="gap-2 text-destructive hover:bg-destructive/5 hover:text-destructive"
                                  >
                                    <X className="h-4 w-4" />
                                    {revokingApiKeyId === apiKey.id ? 'Revoking...' : 'Revoke'}
                                  </Button>
                                )}
                                onConfirm={() => handleRevokeApiKey(apiKey.id)}
                              />
                            </div>
                          </div>
                        </article>
                      )
                    })}
                  </div>
                </div>
              </section>
            </TabsContent>

          </Tabs>
        ) : null}
      </div>
    </div>
  )
}
