import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Loader2, Plus, Search, UsersRound, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Textarea } from '@/components/ui/textarea'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  createTeam,
  listTeams,
  loadSelectableUsers,
  type AuthUser,
  type Team,
  type User,
} from '@/lib/api'

function displayUser(user: User | AuthUser | null | undefined, fallback: string) {
  if (!user) {
    return fallback
  }

    return user.displayName?.trim() || user.username || fallback
}

function sortTeams(teams: Team[]) {
  return [...teams].sort((left, right) => left.name.localeCompare(right.name) || left.id.localeCompare(right.id))
}

function formatShortDate(value: string | undefined) {
  return value ? new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(value)) : '—'
}

function avatarInitials(label: string) {
  return label.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

export default function TeamsPage({ currentUser }: { currentUser: AuthUser | null }) {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [createName, setCreateName] = useState('')
  const [createDescription, setCreateDescription] = useState('')
  const [createOwnerUserIds, setCreateOwnerUserIds] = useState<string[]>([])
  const [createOwnerUserId, setCreateOwnerUserId] = useState('')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [ownerSubmitAttempted, setOwnerSubmitAttempted] = useState(false)
  const [query, setQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)

  const isAdminLike = currentUser?.globalRole === 'ADMIN' || currentUser?.globalRole === 'SUPERADMIN'
  const userById = useMemo(() => {
    const nextUserById = new Map<string, User | AuthUser>(users.map((user) => [user.id, user]))
    if (currentUser) {
      nextUserById.set(currentUser.id, currentUser)
    }
    return nextUserById
  }, [currentUser, users])
  const usersForSelection = useMemo(() => {
    if (!currentUser || currentUser.globalRole?.toUpperCase() === 'SUPERADMIN' || users.some((user) => user.id === currentUser.id)) {
      return users
    }
    return [currentUser, ...users]
  }, [currentUser, users])
  const createOwnerUsers = usersForSelection.filter((user) => !createOwnerUserIds.includes(user.id))
  const filteredTeams = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) {
      return teams
    }

    return teams.filter((team) => [team.name, team.description ?? '', team.id].some((value) => value.toLowerCase().includes(normalizedQuery)))
  }, [query, teams])

  function workspaceDestination(path: string) {
    const params = new URLSearchParams(location.search)
    if (!params.get('chatSessionId')) {
      return path
    }
    params.set('chatPanel', params.get('chatPanel') === 'closed' ? 'closed' : 'open')
    return `${path}?${params.toString()}`
  }

  async function loadPage() {
    setIsLoading(true)

    try {
      const [nextTeams, nextUsers] = await Promise.all([
        listTeams(),
        loadSelectableUsers(),
      ])
      setTeams(sortTeams(nextTeams))
      setUsers(nextUsers)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('teams.failedLoad'))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage()
    })
  }, [])

  function openCreateSheet() {
    setCreateName('')
    setCreateDescription('')
    setCreateOwnerUserIds([])
    setCreateOwnerUserId('')
    setOwnerSubmitAttempted(false)
    setIsSheetOpen(true)
  }

  function addCreateOwner() {
    if (!createOwnerUserId) {
      return
    }
    setCreateOwnerUserIds((current) => [...current, createOwnerUserId])
    setCreateOwnerUserId('')
  }

  function removeCreateOwner(userId: string) {
    setCreateOwnerUserIds((current) => current.filter((currentUserId) => currentUserId !== userId))
  }

  async function handleCreateTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = createName.trim()
    if (!name) {
      toast.error(t('teams.nameRequired'))
      return
    }
    if (createOwnerUserIds.length === 0) {
      setOwnerSubmitAttempted(true)
      toast.error(t('teams.ownerRequired'))
      return
    }

    setIsCreating(true)

    try {
      const created = await createTeam({ name, description: createDescription.trim() || null, ownerUserIds: createOwnerUserIds })
      setCreateName('')
      setCreateDescription('')
      setCreateOwnerUserIds([])
      setCreateOwnerUserId('')
      setIsSheetOpen(false)
      toast.success(t('teams.teamCreated'))
      navigate(workspaceDestination(`/app/teams/${created.id}`))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('teams.failedCreate'))
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('teams.pageTitle')}</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input className="pl-10" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('common.search')} />
          </div>
          {isAdminLike ? (
            <Button className="gap-2" onClick={openCreateSheet}>
              <Plus className="h-4 w-4" />
              {t('teams.newTeam')}
            </Button>
          ) : null}
        </div>

        <div className="rounded-md border bg-background p-4">
          {isLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : filteredTeams.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <UsersRound />
                </EmptyMedia>
                <EmptyTitle>{t('teams.noTeams')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.name')}</TableHead>
                  <TableHead>{t('common.description')}</TableHead>
                  <TableHead>{t('common.members')}</TableHead>
                  <TableHead>{t('common.projects')}</TableHead>
                  <TableHead>{t('common.created')}</TableHead>
                  <TableHead>{t('teams.myRole')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredTeams.map((team) => {
                  const members = (team.memberUserIds ?? []).map((userId) => ({ id: userId, label: team.memberDisplayNames?.[userId] || displayUser(userById.get(userId), t('common.unknownUser')) }))
                  return (
                    <TableRow key={team.id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(workspaceDestination(`/app/teams/${team.id}`))}>
                      <TableCell className="font-medium">{team.name}</TableCell>
                      <TableCell className="max-w-[360px] truncate text-muted-foreground">{team.description?.trim() || '—'}</TableCell>
                      <TableCell>
                        {members.length > 0 ? (
                          <div className="flex -space-x-2" aria-label={t('common.memberCount', { count: members.length })}>
                            {members.slice(0, 4).map((member) => <span key={member.id} className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={member.label}>{avatarInitials(member.label)}</span>)}
                            {members.length > 4 ? <span className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={t('teams.moreMembers', { count: members.length - 4 })}>+{members.length - 4}</span> : null}
                          </div>
                        ) : <span className="text-muted-foreground">—</span>}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{team.projectCount ?? 0}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{formatShortDate(team.createdAt)}</TableCell>
                      <TableCell className="text-muted-foreground">{team.currentUserRole === 'TEAM_OWNER' ? t('common.owner') : team.currentUserRole === 'TEAM_MEMBER' ? t('common.member') : '—'}</TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
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
            <SheetTitle className="text-xl">{t('teams.newTeam')}</SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label={t('common.close')}
            >
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-6 py-4">
            <form className="space-y-5" onSubmit={handleCreateTeam}>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">{t('common.name')}</label>
                <Input value={createName} onChange={(event) => setCreateName(event.target.value)} />
              </div>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">{t('common.description')}</label>
                <Textarea
                  value={createDescription}
                  onChange={(event) => setCreateDescription(event.target.value)}
                  rows={3}
                />
              </div>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">{t('teams.teamOwners')}</label>
                <div className="flex gap-2">
                  <NativeSelect className="w-full" value={createOwnerUserId} onChange={(event) => setCreateOwnerUserId(event.target.value)} disabled={createOwnerUsers.length === 0}>
                    <NativeSelectOption value="">{t('teams.selectOwner')}</NativeSelectOption>
                    {createOwnerUsers.map((user) => (
                      <NativeSelectOption key={user.id} value={user.id}>
                {displayUser(user, t('common.unknownUser'))}
                      </NativeSelectOption>
                    ))}
                  </NativeSelect>
                  <Button type="button" variant="outline" size="icon" onClick={addCreateOwner} disabled={!createOwnerUserId} aria-label={t('teams.addOwner')}>
                    <Plus className="h-4 w-4" />
                  </Button>
                </div>
                {ownerSubmitAttempted && createOwnerUserIds.length === 0 ? (
                  <p className="text-sm text-destructive">{t('teams.ownerRequired')}</p>
                ) : null}
                {createOwnerUserIds.length > 0 ? (
                  <div className="space-y-2">
                    {createOwnerUserIds.map((userId) => {
                      const user = userById.get(userId)
                      return (
                        <div key={userId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                          <span className="truncate text-sm">{displayUser(user, t('common.unknownUser'))}</span>
                          <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeCreateOwner(userId)} aria-label={t('teams.removeOwner', { name: displayUser(user, t('common.unknownUser')) })}>
                            <X className="h-4 w-4" />
                          </Button>
                        </div>
                      )
                    })}
                  </div>
                ) : null}
              </div>
              <div className="flex gap-2">
                <Button type="submit" className="gap-2" disabled={isCreating}>
                  {isCreating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                  {t('teams.create')}
                </Button>
                <Button type="button" variant="outline" className="gap-2" onClick={() => setIsSheetOpen(false)} disabled={isCreating}>
                  <X className="h-4 w-4" />
                  {t('common.cancel')}
                </Button>
              </div>
            </form>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
