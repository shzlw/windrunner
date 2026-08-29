import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, Calendar, Clock, FolderOpen, Loader2, Pencil, Plus, Save, Search, Trash2, X } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  assignProjectTeam,
  createProject,
  listProjectMembers,
  listProjectTeams,
  listProjects,
  listTeams,
  loadSelectableUsers,
  removeProjectMember,
  request,
  unassignProjectTeam,
  upsertProjectMember,
  updateProject,
  type AuthUser,
  type Project,
  type ProjectMember,
  type ProjectTeam,
  type Team,
  type User,
} from '@/lib/api'

type ProjectFormState = {
  title: string
}

const emptyForm: ProjectFormState = {
  title: '',
}

const PROJECT_ROLE_OPTIONS: ProjectTeam['role'][] = ['VIEWER', 'EDITOR', 'OWNER']

function formatProjectTitle(project: Project) {
  return project.title?.trim() ? project.title : 'Untitled project'
}

function displayUser(user: User | AuthUser | null | undefined) {
  if (!user) {
    return 'Unknown user'
  }

  return user.displayName?.trim() || user.username
}

function formatOptionalDate(value: string | undefined) {
  if (!value) {
    return 'Not available'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatShortDate(value: string | undefined) {
  if (!value) {
    return '—'
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
  }).format(new Date(value))
}

function avatarInitials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

function toFormState(project: Project): ProjectFormState {
  return {
    title: project.title ?? project.name ?? '',
  }
}

export default function ProjectsPage({ currentUser }: { currentUser: AuthUser | null }) {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [projectMembers, setProjectMembers] = useState<ProjectMember[]>([])
  const [projectTeams, setProjectTeams] = useState<ProjectTeam[]>([])
  const [query, setQuery] = useState('')
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [form, setForm] = useState<ProjectFormState>(emptyForm)
  const [createOwnerUserIds, setCreateOwnerUserIds] = useState<string[]>([])
  const [createOwnerTeamIds, setCreateOwnerTeamIds] = useState<string[]>([])
  const [createOwnerType, setCreateOwnerType] = useState<'USER' | 'TEAM'>('USER')
  const [createOwnerUserId, setCreateOwnerUserId] = useState('')
  const [createOwnerTeamId, setCreateOwnerTeamId] = useState('')
  const [sheetMode, setSheetMode] = useState<'create' | 'edit' | 'detail'>('create')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [assignTeamId, setAssignTeamId] = useState('')
  const [assignTeamRole, setAssignTeamRole] = useState<ProjectTeam['role']>('VIEWER')
  const [assignMemberUserId, setAssignMemberUserId] = useState('')
  const [assignMemberRole, setAssignMemberRole] = useState<ProjectMember['role']>('VIEWER')
  const [isAssigningTeam, setIsAssigningTeam] = useState(false)
  const [isAssigningMember, setIsAssigningMember] = useState(false)
  const [updatingMemberUserId, setUpdatingMemberUserId] = useState<string | null>(null)

  const loadProjects = useCallback(async () => {
    setIsLoading(true)

    try {
      const [nextProjects, nextTeams, nextUsers] = await Promise.all([
        listProjects(),
        listTeams(),
        loadSelectableUsers(),
      ])
      setProjects(nextProjects)
      setTeams(nextTeams)
      setUsers(nextUsers)
      setSelectedProjectId((current) => {
        if (current && nextProjects.some((project) => project.id === current)) {
          return current
        }
        setForm(emptyForm)
        setProjectMembers([])
        setProjectTeams([])
        setIsSheetOpen(false)
        return null
      })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load projects.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const loadTimer = window.setTimeout(() => {
      void loadProjects()
    }, 0)

    return () => window.clearTimeout(loadTimer)
  }, [loadProjects])

  const filteredProjects = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) {
      return projects
    }

    return projects.filter((project) =>
      [project.title, project.name, project.id]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLowerCase().includes(normalizedQuery)),
    )
  }, [projects, query])

  const selectedProject = projects.find((project) => project.id === selectedProjectId) ?? null
  const teamById = useMemo(() => new Map(teams.map((team) => [team.id, team])), [teams])
  const userById = useMemo(() => {
    const nextUserById = new Map<string, User | AuthUser>(users.map((user) => [user.id, user]))
    if (currentUser) {
      nextUserById.set(currentUser.id, currentUser)
    }
    return nextUserById
  }, [currentUser, users])
  const assignedTeamIds = useMemo(() => new Set(projectTeams.map((projectTeam) => projectTeam.teamId)), [projectTeams])
  const projectMemberUserIds = useMemo(() => new Set(projectMembers.map((member) => member.userId)), [projectMembers])
  const availableTeams = teams.filter((team) => !assignedTeamIds.has(team.id))
  const usersForSelection = useMemo(() => {
    if (!currentUser || currentUser.globalRole?.toUpperCase() === 'SUPERADMIN' || users.some((user) => user.id === currentUser.id)) {
      return users
    }
    return [currentUser, ...users]
  }, [currentUser, users])
  const createOwnerUsers = usersForSelection.filter((user) => !createOwnerUserIds.includes(user.id))
  const createOwnerTeams = teams.filter((team) => !createOwnerTeamIds.includes(team.id))
  const availableMemberUsers = usersForSelection.filter((user) => !projectMemberUserIds.has(user.id))

  function openCreateSheet() {
    setSheetMode('create')
    setForm(emptyForm)
    setCreateOwnerUserIds(
      currentUser && currentUser.globalRole?.toUpperCase() !== 'SUPERADMIN' ? [currentUser.id] : []
    )
    setCreateOwnerTeamIds([])
    setCreateOwnerType('USER')
    setCreateOwnerUserId('')
    setCreateOwnerTeamId('')
    setIsSheetOpen(true)
  }

  function handleOpenProjectSettings(projectId: string) {
    void navigate(`/app/projects/${projectId}/settings`)
  }

  function openEditSheet() {
    if (!selectedProject) {
      return
    }

    setSheetMode('edit')
    setForm(toFormState(selectedProject))
    setIsSheetOpen(true)
  }

  function handleOpenProject(projectId: string) {
    void navigate(`/app/projects/${projectId}`)
  }

  async function handleCreateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const title = form.title.trim()
    if (!title) {
      toast.error('Project name is required.')
      return
    }
    if (createOwnerUserIds.length === 0 && createOwnerTeamIds.length === 0) {
      toast.error('At least one project owner is required.')
      return
    }

    setIsCreating(true)

    try {
      const created = await createProject({
        title,
        ownerUserIds: createOwnerUserIds,
        ownerTeamIds: createOwnerTeamIds,
      })
      setProjects((current) =>
        [...current, created].sort((left, right) => formatProjectTitle(left).localeCompare(formatProjectTitle(right))),
      )
      setSelectedProjectId(created.id)
      setForm(toFormState(created))
      setIsSheetOpen(false)
      toast.success('Project created.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create project.')
    } finally {
      setIsCreating(false)
    }
  }

  async function handleUpdateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!selectedProject) {
      return
    }

    const title = form.title.trim()
    if (!title) {
      toast.error('Project name is required.')
      return
    }

    setIsUpdating(true)

    try {
      const updated = await updateProject(selectedProject.id, { title })
      setProjects((current) =>
        current
          .map((project) => (project.id === updated.id ? updated : project))
          .sort((left, right) => formatProjectTitle(left).localeCompare(formatProjectTitle(right))),
      )
      setSelectedProjectId(updated.id)
      setForm(toFormState(updated))
      setIsSheetOpen(false)
      toast.success('Project updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update project.')
    } finally {
      setIsUpdating(false)
    }
  }

  async function handleDeleteProject() {
    if (!selectedProject) {
      return
    }

    setIsDeleting(true)

    try {
      await request<void>(`/internal-api/v1/projects/${selectedProject.id}`, {
        method: 'DELETE',
      })

      setProjects((current) => {
        const nextProjects = current.filter((project) => project.id !== selectedProject.id)
        setSelectedProjectId(null)
        setForm(emptyForm)
        setProjectMembers([])
        setProjectTeams([])
        return nextProjects
      })
      setIsSheetOpen(false)

      toast.success('Project deleted.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete project.')
    } finally {
      setIsDeleting(false)
    }
  }

  async function loadProjectRelations(projectId: string) {
    try {
      const [nextProjectMembers, nextProjectTeams] = await Promise.all([
        listProjectMembers(projectId),
        listProjectTeams(projectId),
      ])
      setProjectMembers(nextProjectMembers)
      setProjectTeams(nextProjectTeams)
    } catch (error) {
      setProjectMembers([])
      setProjectTeams([])
      toast.error(error instanceof Error ? error.message : 'Failed to load project access.')
    }
  }

  async function handleAssignMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedProject || !assignMemberUserId) {
      return
    }

    setIsAssigningMember(true)

    try {
      await upsertProjectMember(selectedProject.id, assignMemberUserId, assignMemberRole)
      setAssignMemberUserId('')
      setAssignMemberRole('VIEWER')
      await loadProjectRelations(selectedProject.id)
      toast.success('Project member added.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to add project member.')
    } finally {
      setIsAssigningMember(false)
    }
  }

  async function handleUpdateMemberRole(userId: string, role: ProjectMember['role']) {
    if (!selectedProject) {
      return
    }

    setUpdatingMemberUserId(userId)

    try {
      await upsertProjectMember(selectedProject.id, userId, role)
      setProjectMembers((current) => current.map((member) => (
        member.userId === userId ? { ...member, role } : member
      )))
      toast.success('Project member updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update project member.')
      await loadProjectRelations(selectedProject.id)
    } finally {
      setUpdatingMemberUserId(null)
    }
  }

  async function handleRemoveMember(userId: string) {
    if (!selectedProject) {
      return
    }

    try {
      await removeProjectMember(selectedProject.id, userId)
      setProjectMembers((current) => current.filter((member) => member.userId !== userId))
      toast.success('Project member removed.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to remove project member.')
    }
  }

  async function handleAssignTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedProject || !assignTeamId) {
      return
    }

    setIsAssigningTeam(true)

    try {
      await assignProjectTeam(selectedProject.id, assignTeamId, assignTeamRole)
      setAssignTeamId('')
      setAssignTeamRole('VIEWER')
      await loadProjectRelations(selectedProject.id)
      toast.success('Team assigned.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to assign team.')
    } finally {
      setIsAssigningTeam(false)
    }
  }

  async function handleUnassignTeam(teamId: string) {
    if (!selectedProject) {
      return
    }

    try {
      await unassignProjectTeam(selectedProject.id, teamId)
      setProjectTeams((current) => current.filter((projectTeam) => projectTeam.teamId !== teamId))
      toast.success('Team removed from project.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to remove team.')
    }
  }

  function handleAddCreateOwnerUser() {
    if (!createOwnerUserId || createOwnerUserIds.includes(createOwnerUserId)) {
      return
    }
    setCreateOwnerUserIds((current) => [...current, createOwnerUserId])
    setCreateOwnerUserId('')
  }

  function handleAddCreateOwnerTeam() {
    if (!createOwnerTeamId || createOwnerTeamIds.includes(createOwnerTeamId)) {
      return
    }
    setCreateOwnerTeamIds((current) => [...current, createOwnerTeamId])
    setCreateOwnerTeamId('')
  }

  function handleAddCreateOwner() {
    if (createOwnerType === 'USER') {
      handleAddCreateOwnerUser()
    } else {
      handleAddCreateOwnerTeam()
    }
  }

  function handleRemoveCreateOwnerUser(userId: string) {
    setCreateOwnerUserIds((current) => current.filter((currentUserId) => currentUserId !== userId))
  }

  function handleRemoveCreateOwnerTeam(teamId: string) {
    setCreateOwnerTeamIds((current) => current.filter((currentTeamId) => currentTeamId !== teamId))
  }

  const isSubmitting = isCreating || isUpdating

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Projects</h1>
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
          <Button className="gap-2" onClick={openCreateSheet}>
            <Plus className="h-4 w-4" />
            New project
          </Button>
        </div>

        <div className="rounded-md border bg-background p-4">
          {isLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : filteredProjects.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FolderOpen />
                </EmptyMedia>
                <EmptyTitle>No projects found</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Owners</TableHead>
                  <TableHead className="text-right">Links</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
              {filteredProjects.map((project) => {
                const ownerIds = project.ownerUserIds ?? []
                const owners = ownerIds.map((ownerId) => ({
                  id: ownerId,
                  label: project.ownerDisplayNames?.[ownerId] || displayUser(userById.get(ownerId)),
                }))
                return (
                  <TableRow
                      key={project.id}
                      data-state={project.id === selectedProjectId ? 'selected' : undefined}
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleOpenProject(project.id)}
                    >
                    <TableCell className="max-w-0 font-medium">
                      <span className="block truncate" title={formatProjectTitle(project)}>{formatProjectTitle(project)}</span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{formatShortDate(project.createdAt)}</TableCell>
                    <TableCell>
                      {owners.length > 0 ? (
                        <div className="flex -space-x-2" aria-label="Project owners">
                          {owners.slice(0, 4).map((owner) => (
                            <span key={owner.id} className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={owner.label}>
                              {avatarInitials(owner.label)}
                            </span>
                          ))}
                          {owners.length > 4 ? (
                            <span className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={`${owners.length - 4} more owners`}>
                              +{owners.length - 4}
                            </span>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-sm text-muted-foreground">No owners</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button type="button" variant="ghost" size="sm" className="gap-1.5" onClick={() => handleOpenProject(project.id)}>
                        Workspace
                          <ArrowRight className="h-3 w-3" />
                        </Button>
                        <Button type="button" variant="ghost" size="sm" onClick={(event) => { event.stopPropagation(); handleOpenProjectSettings(project.id) }}>
                          Settings
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
              </TableBody>
            </Table>
          )}
        </div>
      </div>

      {/* Sheet: detail / create / edit */}
      <Sheet open={isSheetOpen} onOpenChange={setIsSheetOpen}>
        <SheetContent
          side="right"
          className="!w-full overflow-y-auto p-0 sm:!max-w-xl"
          showCloseButton={false}
        >
          <SheetHeader className="flex min-h-12 flex-row items-center justify-between gap-3 border-b px-4 py-2">
            <SheetTitle className="text-xl">
              {sheetMode === 'create'
                ? 'New project'
                : sheetMode === 'edit'
                  ? 'Edit project'
                  : selectedProject ? formatProjectTitle(selectedProject) : 'Project workspace'}
            </SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-6 py-2">
            {sheetMode === 'detail' ? (
              selectedProject ? (
                <div className="space-y-6">
                  <dl className="space-y-4">
                    <div className="border-b pb-3">
                      <dt className="text-sm font-medium">Name</dt>
                      <dd className="mt-1 break-words text-sm text-muted-foreground">{formatProjectTitle(selectedProject)}</dd>
                    </div>
                    <div className="border-b pb-3">
                      <dt className="flex items-center gap-1 text-sm font-medium">
                        <Calendar className="h-3 w-3 text-muted-foreground" />
                        Created
                      </dt>
                      <dd className="mt-1 text-sm text-muted-foreground">{formatOptionalDate(selectedProject.createdAt)}</dd>
                    </div>
                    <div className="border-b pb-3">
                      <dt className="flex items-center gap-1 text-sm font-medium">
                        <Clock className="h-3 w-3 text-muted-foreground" />
                        Updated
                      </dt>
                      <dd className="mt-1 text-sm text-muted-foreground">{formatOptionalDate(selectedProject.updatedAt)}</dd>
                    </div>
                  </dl>

                  <Button type="button" variant="outline" className="gap-2" onClick={() => handleOpenProject(selectedProject.id)}>
                    Open project
                    <ArrowRight className="h-4 w-4" />
                  </Button>

                  <section className="space-y-3 border-t pt-5">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-sm font-semibold">Project members</h3>
                      <Badge variant="secondary">{projectMembers.length}</Badge>
                    </div>

                    <form className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" onSubmit={handleAssignMember}>
                      <NativeSelect className="w-full" value={assignMemberUserId} onChange={(event) => setAssignMemberUserId(event.target.value)} disabled={availableMemberUsers.length === 0}>
                        <NativeSelectOption value="">Select user</NativeSelectOption>
                        {availableMemberUsers.map((user) => (
                          <NativeSelectOption key={user.id} value={user.id}>
                            {displayUser(user)}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full" value={assignMemberRole} onChange={(event) => setAssignMemberRole(event.target.value as ProjectMember['role'])}>
                        {PROJECT_ROLE_OPTIONS.map((role) => (
                          <NativeSelectOption key={role} value={role}>
                            {role}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <Button type="submit" className="gap-2" disabled={isAssigningMember || !assignMemberUserId}>
                        {isAssigningMember ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                        Add
                      </Button>
                    </form>

                    {projectMembers.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">No direct members assigned.</div>
                    ) : (
                      <div className="space-y-2">
                        {projectMembers.map((member) => {
                          const user = userById.get(member.userId)
                          return (
                            <div key={member.userId} className="grid gap-2 rounded-md border px-3 py-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-center">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{displayUser(user)}</div>
                                <div className="truncate font-mono text-xs text-muted-foreground">{member.userId}</div>
                              </div>
                              <NativeSelect
                                className="w-full"
                                value={member.role}
                                onChange={(event) => void handleUpdateMemberRole(member.userId, event.target.value as ProjectMember['role'])}
                                disabled={updatingMemberUserId === member.userId}
                              >
                                {PROJECT_ROLE_OPTIONS.map((role) => (
                                  <NativeSelectOption key={role} value={role}>
                                    {role}
                                  </NativeSelectOption>
                                ))}
                              </NativeSelect>
                              <DeleteConfirmPopover
                                title="Remove project member?"
                                description="This user will lose direct access granted by this project membership."
                                confirmLabel="Remove"
                                trigger={(
                                  <Button type="button" variant="ghost" size="icon-sm" aria-label="Remove project member">
                                    <X className="h-4 w-4" />
                                  </Button>
                                )}
                                onConfirm={() => handleRemoveMember(member.userId)}
                              />
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </section>

                  <section className="space-y-3 border-t pt-5">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-sm font-semibold">Assigned teams</h3>
                      <Badge variant="secondary">{projectTeams.length}</Badge>
                    </div>

                    <form className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" onSubmit={handleAssignTeam}>
                      <NativeSelect className="w-full" value={assignTeamId} onChange={(event) => setAssignTeamId(event.target.value)} disabled={availableTeams.length === 0}>
                        <NativeSelectOption value="">Select team</NativeSelectOption>
                        {availableTeams.map((team) => (
                          <NativeSelectOption key={team.id} value={team.id}>
                            {team.name}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full" value={assignTeamRole} onChange={(event) => setAssignTeamRole(event.target.value as ProjectTeam['role'])}>
                        {PROJECT_ROLE_OPTIONS.map((role) => (
                          <NativeSelectOption key={role} value={role}>
                            {role}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <Button type="submit" className="gap-2" disabled={isAssigningTeam || !assignTeamId}>
                        {isAssigningTeam ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                        Assign
                      </Button>
                    </form>

                    {projectTeams.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">No teams assigned.</div>
                    ) : (
                      <div className="space-y-2">
                        {projectTeams.map((projectTeam) => {
                          const team = teamById.get(projectTeam.teamId)
                          return (
                            <div key={projectTeam.teamId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{team?.name || 'Unknown team'}</div>
                                <div className="truncate text-xs text-muted-foreground">{projectTeam.role}</div>
                              </div>
                              <DeleteConfirmPopover
                                title="Remove team from project?"
                                description="Team members will lose the project access granted through this team."
                                confirmLabel="Remove"
                                trigger={(
                                  <Button type="button" variant="ghost" size="icon-sm" aria-label="Remove team">
                                    <X className="h-4 w-4" />
                                  </Button>
                                )}
                                onConfirm={() => handleUnassignTeam(projectTeam.teamId)}
                              />
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </section>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Select a project to inspect it.</p>
              )
            ) : (
              <form
                className="space-y-5"
                onSubmit={sheetMode === 'create' ? handleCreateProject : handleUpdateProject}
              >
                <div className="space-y-2">
                  <label className="flex items-center gap-2 text-sm font-semibold">Name</label>
                  <Input
                    value={form.title}
                    onChange={(event) => setForm({ title: event.target.value })}
                    required
                  />
                </div>

                {sheetMode === 'create' ? (
                  <section className="space-y-3 border-t pt-5">
                    <label className="block text-sm font-semibold">Owners</label>

                    <div className="flex gap-2">
                      <NativeSelect className="w-28 shrink-0" value={createOwnerType} onChange={(event) => setCreateOwnerType(event.target.value as 'USER' | 'TEAM')}>
                        <NativeSelectOption value="USER">User</NativeSelectOption>
                        <NativeSelectOption value="TEAM">Team</NativeSelectOption>
                      </NativeSelect>
                      <NativeSelect
                        className="min-w-0 flex-1"
                        value={createOwnerType === 'USER' ? createOwnerUserId : createOwnerTeamId}
                        onChange={(event) => createOwnerType === 'USER' ? setCreateOwnerUserId(event.target.value) : setCreateOwnerTeamId(event.target.value)}
                        disabled={createOwnerType === 'USER' ? createOwnerUsers.length === 0 : createOwnerTeams.length === 0}
                      >
                        <NativeSelectOption value="">Select {createOwnerType === 'USER' ? 'owner' : 'team'}</NativeSelectOption>
                        {createOwnerType === 'USER'
                          ? createOwnerUsers.map((user) => <NativeSelectOption key={user.id} value={user.id}>{displayUser(user)}</NativeSelectOption>)
                          : createOwnerTeams.map((team) => <NativeSelectOption key={team.id} value={team.id}>{team.name}</NativeSelectOption>)}
                      </NativeSelect>
                      <Button type="button" variant="outline" size="icon" onClick={handleAddCreateOwner} disabled={createOwnerType === 'USER' ? !createOwnerUserId : !createOwnerTeamId} aria-label="Add project owner">
                        <Plus className="h-4 w-4" />
                      </Button>
                    </div>

                    {createOwnerUserIds.length === 0 && createOwnerTeamIds.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-5 text-sm text-muted-foreground">
                        No project owners selected.
                      </div>
                    ) : (
                      <div className="space-y-2">
                        {createOwnerUserIds.map((userId) => {
                          const user = userById.get(userId)
                          return (
                            <div key={userId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{displayUser(user)}</div>
                                {user?.email ? <div className="truncate text-xs text-muted-foreground">{user.email}</div> : null}
                              </div>
                              <Button type="button" variant="ghost" size="icon-sm" onClick={() => handleRemoveCreateOwnerUser(userId)} aria-label="Remove user owner">
                                <X className="h-4 w-4" />
                              </Button>
                            </div>
                          )
                        })}

                        {createOwnerTeamIds.map((teamId) => {
                          const team = teamById.get(teamId)
                          return (
                            <div key={teamId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{team?.name || 'Unknown team'}</div>
                                <div className="truncate text-xs text-muted-foreground">Team owner</div>
                              </div>
                              <Button type="button" variant="ghost" size="icon-sm" onClick={() => handleRemoveCreateOwnerTeam(teamId)} aria-label="Remove team owner">
                                <X className="h-4 w-4" />
                              </Button>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </section>
                ) : null}

                <div className="flex gap-2">
                  <Button
                    type="submit"
                    disabled={isSubmitting || (sheetMode === 'create' && createOwnerUserIds.length === 0 && createOwnerTeamIds.length === 0)}
                    className="gap-2"
                  >
                    {isSubmitting ? (
                      'Saving...'
                    ) : (
                      <>
                        <Save className="h-4 w-4" />
                        {sheetMode === 'create' ? 'Create project' : 'Save changes'}
                      </>
                    )}
                  </Button>
                  <Button type="button" variant="outline" className="gap-2" onClick={() => setIsSheetOpen(false)} disabled={isSubmitting}>
                    <X className="h-4 w-4" />
                    Cancel
                  </Button>
                </div>
              </form>
            )}
          </div>

          {sheetMode === 'detail' && selectedProject ? (
            <div className="flex shrink-0 gap-2 border-t p-6">
              <Button onClick={openEditSheet} className="gap-2">
                <Pencil className="h-4 w-4" />
                Edit project
              </Button>
              <DeleteConfirmPopover
                title="Delete project?"
                description="This will permanently delete the project workspace."
                confirmLabel="Delete permanently"
                disabled={isDeleting}
                trigger={(
                  <Button variant="destructive" disabled={isDeleting} className="gap-2">
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? 'Deleting...' : 'Delete project'}
                  </Button>
                )}
                onConfirm={handleDeleteProject}
              />
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

    </div>
  )
}
