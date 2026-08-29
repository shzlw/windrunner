import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Search, Plus, Trash2, Edit3, Save, X, Lock, ChevronLeft, ChevronRight, Loader2, UsersRound, Eye, EyeOff } from 'lucide-react'
import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { listUsers, request, type AuthUser } from '@/lib/api'
import { JAVA_SUPPORTED_TIMEZONES } from '@/lib/timezones'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from '@/components/ui/combobox'
import { toast } from 'sonner'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Field,
  FieldGroup,
} from '@/components/ui/field'

type User = {
  id: string
  username: string
  email: string | null
  displayName: string | null
  title: string | null
  bio: string | null
  timezone: string | null
  status: string | null
  globalRole: string | null
  mustChangePassword: boolean
  createdAt: string
  updatedAt: string
}

type UserFormState = {
  username: string
  email: string
  displayName: string
  title: string
  bio: string
  timezone: string
  status: string
  globalRole: string
  password: string
}

const USER_STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'] as const
const USER_ROLE_OPTIONS = ['USER', 'ADMIN'] as const

const pageSize = 20
const defaultTimezone = 'UTC'
const timezoneOptions = JAVA_SUPPORTED_TIMEZONES

const emptyForm: UserFormState = {
  username: '',
  email: '',
  displayName: '',
  title: '',
  bio: '',
  timezone: defaultTimezone,
  status: 'ACTIVE',
  globalRole: 'USER',
  password: '',
}

function toFormState(user: User): UserFormState {
  return {
    username: user.username ?? '',
    email: user.email ?? '',
    displayName: user.displayName ?? '',
    title: user.title ?? '',
    bio: user.bio ?? '',
    timezone: user.timezone ?? defaultTimezone,
    status: user.status ?? 'ACTIVE',
    globalRole: user.globalRole ?? 'USER',
    password: '',
  }
}

function TimezonePicker({
  value,
  onValueChange,
}: {
  value: string
  onValueChange: (value: string) => void
}) {
  return (
    <Combobox
      items={timezoneOptions}
      value={value || 'UTC'}
      onValueChange={(nextValue) => onValueChange(nextValue ?? 'UTC')}
      itemToStringLabel={(timezone) => timezone}
      itemToStringValue={(timezone) => timezone}
      autoHighlight
    >
      <ComboboxInput className="w-full" placeholder="Search timezone" />
      <ComboboxContent>
        <ComboboxEmpty>No timezone found.</ComboboxEmpty>
        <ComboboxList>
          {timezoneOptions.map((timezone, index) => (
            <ComboboxItem key={timezone} value={timezone} index={index}>
              {timezone}
            </ComboboxItem>
          ))}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatValue(value: string | null) {
  return value && value.trim() ? value : 'Not set'
}

function isValidOptionalEmail(value: string) {
  const normalizedValue = value.trim()
  return !normalizedValue || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedValue)
}

export default function UsersPage({ currentUser }: { currentUser: AuthUser | null }) {
  const isSuperAdmin = currentUser?.globalRole === 'SUPERADMIN'
  const isAdminLike = currentUser?.globalRole === 'ADMIN' || currentUser?.globalRole === 'SUPERADMIN'
  const [users, setUsers] = useState<User[]>([])
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [form, setForm] = useState<UserFormState>(emptyForm)
  const [sheetMode, setSheetMode] = useState<'create' | 'edit' | 'detail'>('create')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [passwordResetValue, setPasswordResetValue] = useState('')
  const [showPasswordReset, setShowPasswordReset] = useState(false)
  const [forcePasswordChange, setForcePasswordChange] = useState(true)
  const [isResettingPassword, setIsResettingPassword] = useState(false)
  const [showInitialPassword, setShowInitialPassword] = useState(false)
  const [query, setQuery] = useState('')

  async function loadPage(nextPage: number) {
    setIsListLoading(true)

    try {
      const data = await listUsers(nextPage, pageSize)

      setUsers(data.items)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        clearSelection()
        return
      }

      if (selectedUserId && !data.items.some((user) => user.id === selectedUserId)) {
        clearSelection()
      }
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load users.')
      setUsers([])
      setTotalPages(0)
      clearSelection()
    } finally {
      setIsListLoading(false)
    }
  }

  async function selectUser(userId: string, options: { openSheet?: boolean } = {}) {
    const { openSheet = true } = options

    setSelectedUserId(userId)
    setIsDetailLoading(true)

    try {
      const user = await request<User>(`/internal-api/v1/users/${userId}`, { method: 'GET' })
      setSelectedUser(user)
      setForcePasswordChange(user.mustChangePassword)
      if (sheetMode === 'edit' && isSheetOpen) {
        setForm(toFormState(user))
      } else {
        setSheetMode('detail')
        setIsSheetOpen(openSheet)
      }
    } catch (loadError) {
      setSelectedUser(null)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load user details.')
    } finally {
      setIsDetailLoading(false)
    }
  }

  function clearSelection() {
    setSelectedUserId(null)
    setSelectedUser(null)
    setForm(emptyForm)
    setSheetMode('create')
    setIsSheetOpen(false)
    setPasswordResetValue('')
    setForcePasswordChange(true)
  }

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage(page)
    })
  }, [page])

  function openCreateSheet() {
    setSheetMode('create')
    setForm(emptyForm)
    setIsSheetOpen(true)
  }

  function openEditSheet() {
    if (!selectedUser) {
      return
    }

    setSheetMode('edit')
    setForm(toFormState(selectedUser))
    setIsSheetOpen(true)
  }

  function updateField<Key extends keyof UserFormState>(key: Key, value: UserFormState[Key]) {
    setForm((current) => ({
      ...current,
      [key]: value,
    }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!isValidOptionalEmail(form.email)) {
      toast.error('Enter a valid email address.')
      return
    }
    setIsSubmitting(true)

    const payload = {
      username: form.username.trim(),
      email: form.email.trim() || null,
      displayName: form.displayName.trim() || null,
      title: form.title.trim() || null,
      bio: form.bio.trim() || null,
      timezone: form.timezone.trim() || 'UTC',
      status: form.status.trim() || 'ACTIVE',
      ...(isSuperAdmin ? { globalRole: form.globalRole.trim() || 'USER' } : {}),
    }

    try {
      const user =
        sheetMode === 'create'
          ? await request<User>('/internal-api/v1/users', {
              method: 'POST',
              body: JSON.stringify({
                ...payload,
                password: form.password,
              }),
            })
          : await request<User>(`/internal-api/v1/users/${selectedUserId}`, {
              method: 'PUT',
              body: JSON.stringify(payload),
            })

      await loadPage(page)
      await selectUser(user.id)
      setIsSheetOpen(false)
      toast.success(sheetMode === 'create' ? 'User created.' : 'User updated.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to save user.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete() {
    if (!selectedUserId) {
      return
    }

    setIsDeleting(true)

    try {
      await request<void>(`/internal-api/v1/users/${selectedUserId}`, {
        method: 'DELETE',
      })

      const shouldMoveToPreviousPage = page > 0 && users.length === 1
      if (shouldMoveToPreviousPage) {
        setPage((current) => current - 1)
      } else {
        await loadPage(page)
      }
      toast.success('User deleted.')
    } catch (deleteError) {
      toast.error(deleteError instanceof Error ? deleteError.message : 'Failed to delete user.')
    } finally {
      setIsDeleting(false)
    }
  }

  async function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUserId) {
      return
    }

    if (!passwordResetValue || passwordResetValue.length < 6) {
      toast.error('Password must be at least 6 characters.')
      return
    }

    setIsResettingPassword(true)

    try {
      const updatedUser = await request<User>(`/internal-api/v1/users/${selectedUserId}/password`, {
        method: 'POST',
        body: JSON.stringify({
          newPassword: passwordResetValue,
          mustChangePassword: forcePasswordChange,
        }),
      })
      setSelectedUser(updatedUser)
      setPasswordResetValue('')
      setForcePasswordChange(updatedUser.mustChangePassword)
      await loadPage(page)
      toast.success('Password reset successfully.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to reset password.')
    } finally {
      setIsResettingPassword(false)
    }
  }

  const normalizedQuery = query.trim().toLowerCase()
  const emailValidationMessage = isValidOptionalEmail(form.email) ? null : 'Enter a valid email address.'
  const filteredUsers = users.filter((user) => {
    if (!normalizedQuery) {
      return true
    }

    return [user.username, user.displayName, user.title, user.bio, user.email, user.timezone, user.status, user.globalRole]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLowerCase().includes(normalizedQuery))
  })

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Users</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-10"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search"
            />
          </div>
          {isAdminLike ? (
            <Button className="gap-2" onClick={openCreateSheet}>
              <Plus className="h-4 w-4" />
              New user
            </Button>
          ) : null}
        </div>

        <div className="rounded-md border bg-background p-4">
          {isListLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : filteredUsers.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <UsersRound />
                </EmptyMedia>
                <EmptyTitle>No users found</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Title</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Username</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Role</TableHead>
                    <TableHead>Password</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredUsers.map((user) => {
                    const isSelected = user.id === selectedUserId
                    return (
                      <TableRow
                        key={user.id}
                        data-state={isSelected ? 'selected' : undefined}
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => void selectUser(user.id)}
                      >
                        <TableCell className="font-medium">{user.displayName?.trim() || user.username}</TableCell>
                        <TableCell className="max-w-[220px] truncate text-muted-foreground">{formatValue(user.title)}</TableCell>
                        <TableCell className="max-w-[280px] truncate text-muted-foreground">{formatValue(user.email)}</TableCell>
                        <TableCell className="font-mono text-[11px] text-muted-foreground">@{user.username}</TableCell>
                        <TableCell>
                          <Badge variant={user.status === 'ACTIVE' ? 'secondary' : 'outline'}>
                            {formatValue(user.status)}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{formatValue(user.globalRole)}</Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {user.mustChangePassword ? 'Reset required' : 'Ready'}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              <div className="flex justify-end border-t pt-3 text-sm">
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={page === 0 || isListLoading}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <div className="text-sm text-muted-foreground">
                    {totalPages === 0 ? 0 : page + 1} / {Math.max(totalPages, 1)}
                  </div>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={isListLoading || totalPages === 0 || page >= totalPages - 1}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      <Sheet open={isSheetOpen} onOpenChange={setIsSheetOpen}>
        <SheetContent
          side="right"
          className="!w-full overflow-y-auto p-0 sm:!max-w-xl"
          showCloseButton={false}
        >
          <SheetHeader className="flex min-h-12 flex-row items-center justify-between gap-3 border-b px-4 py-2">
            <SheetTitle className="text-xl">
              {sheetMode === 'create'
                ? 'New user'
                : sheetMode === 'edit'
                  ? 'Edit user'
                  : selectedUser?.displayName || selectedUser?.username || 'User details'}
            </SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-6 py-2">
            {sheetMode === 'detail' && isDetailLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : null}

            {sheetMode === 'detail' ? (
              selectedUser ? (
                <div className="space-y-6">
                  <dl className="space-y-4">
                    {[
                      ['Display name', formatValue(selectedUser.displayName)],
                      ['Title', formatValue(selectedUser.title)],
                      ['Bio', formatValue(selectedUser.bio)],
                      ['Username', selectedUser.username],
                      ['Email', formatValue(selectedUser.email)],
                      ['Timezone', formatValue(selectedUser.timezone)],
                      ['Status', formatValue(selectedUser.status)],
                      ['Role', formatValue(selectedUser.globalRole)],
                      ['Password reset required', selectedUser.mustChangePassword ? 'Yes' : 'No'],
                      ['Created', formatDate(selectedUser.createdAt)],
                      ['Updated', formatDate(selectedUser.updatedAt)],
                    ].map(([label, value]) => (
                      <div key={label} className="border-b pb-3 last:border-b-0">
                        <dt className="text-sm font-medium">{label}</dt>
                        <dd className="mt-1 break-words text-sm text-muted-foreground">{value}</dd>
                      </div>
                    ))}
                  </dl>

                  {isAdminLike ? (
                  <form className="space-y-4 border-t pt-5" onSubmit={handlePasswordReset}>
                    <h3 className="text-sm font-medium">Security</h3>
                    <FieldGroup className="gap-4">
                      <Field className="gap-2">
                        <div className="flex items-center justify-between gap-3">
                          <label className="block text-sm font-semibold" htmlFor="reset-password">Temporary password</label>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="-mr-2 gap-1.5 px-2 text-muted-foreground"
                            onClick={() => setShowPasswordReset((current) => !current)}
                            aria-label={showPasswordReset ? 'Hide password' : 'Show password'}
                          >
                            {showPasswordReset ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                          </Button>
                        </div>
                        <Input
                          id="reset-password"
                          type={showPasswordReset ? 'text' : 'password'}
                          value={passwordResetValue}
                          onChange={(event) => setPasswordResetValue(event.target.value)}
                          autoComplete="new-password"
                          required
                        />
                      </Field>

                      <Field orientation="horizontal" className="items-center">
                        <Checkbox
                          id="force-password-change"
                          checked={forcePasswordChange}
                          onCheckedChange={(checked) => setForcePasswordChange(checked === true)}
                        />
                        <label className="block text-sm font-semibold" htmlFor="force-password-change">
                          Require password update on next sign-in
                        </label>
                      </Field>
                    </FieldGroup>

                    <Button type="submit" disabled={isResettingPassword || !passwordResetValue.trim()} className="gap-2">
                      {isResettingPassword ? (
                        'Resetting...'
                      ) : (
                        <>
                          <Lock className="h-4 w-4" /> Reset password
                        </>
                      )}
                    </Button>
                  </form>
                  ) : null}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Select a user to inspect it.</p>
              )
            ) : (
              <form className="space-y-5" onSubmit={handleSubmit}>
                <FieldGroup className="gap-4">
                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-username">Username</label>
                    <Input
                      id="user-username"
                      value={form.username}
                      onChange={(event) => updateField('username', event.target.value)}
                      required
                    />
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-display-name">Display name</label>
                    <Input
                      id="user-display-name"
                      value={form.displayName}
                      onChange={(event) => updateField('displayName', event.target.value)}
                    />
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-email">Email</label>
                    <Input
                      id="user-email"
                      type="email"
                      value={form.email}
                      onChange={(event) => updateField('email', event.target.value)}
                      aria-invalid={Boolean(emailValidationMessage)}
                    />
                    {emailValidationMessage ? <p className="text-sm text-destructive">{emailValidationMessage}</p> : null}
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-title">Title</label>
                    <Input
                      id="user-title"
                      value={form.title}
                      onChange={(event) => updateField('title', event.target.value)}
                      placeholder="e.g. Product manager"
                    />
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-bio">Bio</label>
                    <Textarea
                      id="user-bio"
                      value={form.bio}
                      onChange={(event) => updateField('bio', event.target.value)}
                      placeholder="A short description about this person"
                      rows={4}
                    />
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold" htmlFor="user-timezone">Timezone</label>
                    <TimezonePicker
                      value={form.timezone}
                      onValueChange={(value) => updateField('timezone', value)}
                    />
                  </Field>

                  <Field className="gap-2">
                    <label className="block text-sm font-semibold">Status</label>
                    <Select value={form.status} onValueChange={(value) => updateField('status', value ?? 'ACTIVE')}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {USER_STATUS_OPTIONS.map((status) => (
                          <SelectItem key={status} value={status}>
                            {status}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>

                  {isSuperAdmin ? (
                    <Field className="gap-2">
                      <label className="block text-sm font-semibold">Role</label>
                      <Select value={form.globalRole} onValueChange={(value) => updateField('globalRole', value ?? 'USER')}>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {USER_ROLE_OPTIONS.map((globalRole) => (
                            <SelectItem key={globalRole} value={globalRole}>
                              {globalRole}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </Field>
                  ) : null}

                  {sheetMode === 'create' ? (
                    <Field className="gap-2">
                      <div className="flex items-center justify-between gap-3">
                        <label className="block text-sm font-semibold" htmlFor="user-password">Initial password</label>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="-mr-2 gap-1.5 px-2 text-muted-foreground"
                          onClick={() => setShowInitialPassword((current) => !current)}
                          aria-label={showInitialPassword ? 'Hide password' : 'Show password'}
                        >
                          {showInitialPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                        </Button>
                      </div>
                      <Input
                        id="user-password"
                        type={showInitialPassword ? 'text' : 'password'}
                        value={form.password}
                        onChange={(event) => updateField('password', event.target.value)}
                        autoComplete="new-password"
                        required
                      />
                    </Field>
                  ) : null}
                </FieldGroup>

                <div className="flex gap-2">
                  <Button type="submit" disabled={isSubmitting} className="gap-2">
                    {isSubmitting ? (
                      'Saving...'
                    ) : (
                      <>
                        <Save className="h-4 w-4" />
                        {sheetMode === 'create' ? 'Create user' : 'Save changes'}
                      </>
                    )}
                  </Button>
                  <Button type="button" variant="outline" className="gap-2" onClick={() => setIsSheetOpen(false)} disabled={isSubmitting}>
                    <X className="h-4 w-4" /> Cancel
                  </Button>
                </div>
              </form>
            )}
          </div>

          {sheetMode === 'detail' && selectedUser ? (
            isAdminLike ? (
            <div className="flex shrink-0 gap-2 border-t p-6">
              <Button onClick={openEditSheet} className="gap-2">
                <Edit3 className="h-4 w-4" /> Edit user
              </Button>
              <DeleteConfirmPopover
                title="Delete user?"
                description="This will deactivate the user account, remove their project and team memberships, and sign them out immediately. Their past activity keeps their name."
                confirmLabel="Delete user"
                disabled={isDeleting}
                trigger={(
                  <Button variant="destructive" disabled={isDeleting} className="gap-2">
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? 'Deleting...' : 'Delete user'}
                  </Button>
                )}
                onConfirm={handleDelete}
              />
            </div>
            ) : null
          ) : null}
        </SheetContent>
      </Sheet>

    </div>
  )
}
