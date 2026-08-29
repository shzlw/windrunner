import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Loader2, Plus, Search, UsersRound, X } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  createTeam,
  listTeams,
  loadSelectableUsers,
  type AuthUser,
  type Team,
  type User,
} from '@/lib/api'

function displayUser(user: User | AuthUser | null | undefined) {
  if (!user) {
    return 'Unknown user'
  }

  return user.displayName?.trim() || user.username
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
  const navigate = useNavigate()
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [createName, setCreateName] = useState('')
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

    return teams.filter((team) => [team.name, team.id].some((value) => value.toLowerCase().includes(normalizedQuery)))
  }, [query, teams])

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
      toast.error(error instanceof Error ? error.message : 'Failed to load teams.')
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
      toast.error('Team name is required.')
      return
    }
    if (createOwnerUserIds.length === 0) {
      setOwnerSubmitAttempted(true)
      toast.error('Select at least one team owner.')
      return
    }

    setIsCreating(true)

    try {
      const created = await createTeam({ name, ownerUserIds: createOwnerUserIds })
      setCreateName('')
      setCreateOwnerUserIds([])
      setCreateOwnerUserId('')
      setIsSheetOpen(false)
      toast.success('Team created.')
      navigate(`/app/teams/${created.id}`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create team.')
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Teams</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input className="pl-10" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search" />
          </div>
          {isAdminLike ? (
            <Button className="gap-2" onClick={openCreateSheet}>
              <Plus className="h-4 w-4" />
              New team
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
                <EmptyTitle>No teams found</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Members</TableHead>
                  <TableHead>Projects</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>My role</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredTeams.map((team) => {
                  const members = (team.memberUserIds ?? []).map((userId) => ({ id: userId, label: team.memberDisplayNames?.[userId] || displayUser(userById.get(userId)) }))
                  return (
                    <TableRow key={team.id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/app/teams/${team.id}`)}>
                      <TableCell className="font-medium">{team.name}</TableCell>
                      <TableCell>
                        {members.length > 0 ? (
                          <div className="flex -space-x-2" aria-label={`${members.length} members`}>
                            {members.slice(0, 4).map((member) => <span key={member.id} className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={member.label}>{avatarInitials(member.label)}</span>)}
                            {members.length > 4 ? <span className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={`${members.length - 4} more members`}>+{members.length - 4}</span> : null}
                          </div>
                        ) : <span className="text-muted-foreground">—</span>}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{team.projectCount ?? 0}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{formatShortDate(team.createdAt)}</TableCell>
                      <TableCell className="text-muted-foreground">{team.currentUserRole === 'TEAM_OWNER' ? 'Owner' : team.currentUserRole === 'TEAM_MEMBER' ? 'Member' : '—'}</TableCell>
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
            <SheetTitle className="text-xl">New team</SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-6 py-4">
            <form className="space-y-5" onSubmit={handleCreateTeam}>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">Name</label>
                <Input value={createName} onChange={(event) => setCreateName(event.target.value)} />
              </div>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">Team owners</label>
                <div className="flex gap-2">
                  <NativeSelect className="w-full" value={createOwnerUserId} onChange={(event) => setCreateOwnerUserId(event.target.value)} disabled={createOwnerUsers.length === 0}>
                    <NativeSelectOption value="">Select owner</NativeSelectOption>
                    {createOwnerUsers.map((user) => (
                      <NativeSelectOption key={user.id} value={user.id}>
                        {displayUser(user)}
                      </NativeSelectOption>
                    ))}
                  </NativeSelect>
                  <Button type="button" variant="outline" size="icon" onClick={addCreateOwner} disabled={!createOwnerUserId} aria-label="Add team owner">
                    <Plus className="h-4 w-4" />
                  </Button>
                </div>
                {ownerSubmitAttempted && createOwnerUserIds.length === 0 ? (
                  <p className="text-sm text-destructive">Select at least one team owner.</p>
                ) : null}
                {createOwnerUserIds.length > 0 ? (
                  <div className="space-y-2">
                    {createOwnerUserIds.map((userId) => {
                      const user = userById.get(userId)
                      return (
                        <div key={userId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                          <span className="truncate text-sm">{displayUser(user)}</span>
                          <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeCreateOwner(userId)} aria-label={`Remove ${displayUser(user)} as team owner`}>
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
                  Create
                </Button>
                <Button type="button" variant="outline" className="gap-2" onClick={() => setIsSheetOpen(false)} disabled={isCreating}>
                  <X className="h-4 w-4" />
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
