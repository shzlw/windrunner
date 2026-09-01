import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, Calendar, Clock, FolderOpen, Loader2, Pencil, Plus, Save, Search, Trash2, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

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
import { translateRole } from '@/i18n/labels'

type ProjectFormState = {
  title: string
}

const emptyForm: ProjectFormState = {
  title: '',
}

const PROJECT_ROLE_OPTIONS: ProjectTeam['role'][] = ['VIEWER', 'EDITOR', 'OWNER']

function formatProjectTitle(project: Project, fallback: string) {
  return project.title?.trim() ? project.title : fallback
}

function displayUser(user: User | AuthUser | null | undefined, fallback: string) {
  if (!user) {
    return fallback
  }

  return user.displayName?.trim() || user.username
}

function formatOptionalDate(value: string | undefined, fallback: string) {
  if (!value) {
    return fallback
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
  const { t } = useTranslation()
  const location = useLocation()
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
      toast.error(error instanceof Error ? error.message : t('projects.failedLoad'))
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
    void navigate(workspaceDestination(`/app/projects/${projectId}/settings`))
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
    void navigate(workspaceDestination(`/app/projects/${projectId}`))
  }

  function workspaceDestination(path: string) {
    const params = new URLSearchParams(location.search)
    if (!params.get('chatSessionId')) {
      return path
    }
    params.set('chatPanel', params.get('chatPanel') === 'closed' ? 'closed' : 'open')
    return `${path}?${params.toString()}`
  }

  async function handleCreateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const title = form.title.trim()
    if (!title) {
      toast.error(t('projects.nameRequired'))
      return
    }
    if (createOwnerUserIds.length === 0 && createOwnerTeamIds.length === 0) {
      toast.error(t('projects.ownerRequired'))
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
        [...current, created].sort((left, right) => formatProjectTitle(left, t('common.untitledProject')).localeCompare(formatProjectTitle(right, t('common.untitledProject')))),
      )
      setSelectedProjectId(created.id)
      setForm(toFormState(created))
      setIsSheetOpen(false)
      toast.success(t('projects.projectCreated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedCreate'))
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
      toast.error(t('projects.nameRequired'))
      return
    }

    setIsUpdating(true)

    try {
      const updated = await updateProject(selectedProject.id, { title })
      setProjects((current) =>
        current
          .map((project) => (project.id === updated.id ? updated : project))
          .sort((left, right) => formatProjectTitle(left, t('common.untitledProject')).localeCompare(formatProjectTitle(right, t('common.untitledProject')))),
      )
      setSelectedProjectId(updated.id)
      setForm(toFormState(updated))
      setIsSheetOpen(false)
      toast.success(t('projects.projectUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedUpdate'))
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

      toast.success(t('projects.projectDeleted'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedDelete'))
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
      toast.error(error instanceof Error ? error.message : t('projects.failedLoadAccess'))
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
      toast.success(t('projects.memberAdded'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedAddMember'))
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
      toast.success(t('projects.memberUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedUpdateMember'))
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
      toast.success(t('projects.memberRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedRemoveMember'))
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
      toast.success(t('projects.teamAssigned'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedAssignTeam'))
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
      toast.success(t('projects.teamRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedRemoveTeam'))
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
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('projects.pageTitle')}</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-10"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t('common.search')}
            />
          </div>
          <Button className="gap-2" onClick={openCreateSheet}>
            <Plus className="h-4 w-4" />
            {t('projects.newProject')}
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
                <EmptyTitle>{t('projects.noProjects')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.name')}</TableHead>
                  <TableHead>{t('common.created')}</TableHead>
                  <TableHead>{t('projects.owners')}</TableHead>
                  <TableHead className="text-right">{t('projects.links')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
              {filteredProjects.map((project) => {
                const ownerIds = project.ownerUserIds ?? []
                const owners = ownerIds.map((ownerId) => ({
                  id: ownerId,
                  label: project.ownerDisplayNames?.[ownerId] || displayUser(userById.get(ownerId), t('common.unknownUser')),
                }))
                return (
                  <TableRow
                      key={project.id}
                      data-state={project.id === selectedProjectId ? 'selected' : undefined}
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleOpenProject(project.id)}
                    >
                    <TableCell className="max-w-0 font-medium">
                      <span className="block truncate" title={formatProjectTitle(project, t('common.untitledProject'))}>{formatProjectTitle(project, t('common.untitledProject'))}</span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{formatShortDate(project.createdAt)}</TableCell>
                    <TableCell>
                      {owners.length > 0 ? (
                        <div className="flex -space-x-2" aria-label={t('projects.projectOwners')}>
                          {owners.slice(0, 4).map((owner) => (
                            <span key={owner.id} className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={owner.label}>
                              {avatarInitials(owner.label)}
                            </span>
                          ))}
                          {owners.length > 4 ? (
                            <span className="grid size-7 place-items-center rounded-full border-2 border-background bg-muted text-[10px] font-medium text-muted-foreground" title={t('projects.moreOwners', { count: owners.length - 4 })}>
                              +{owners.length - 4}
                            </span>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-sm text-muted-foreground">{t('projects.noOwners')}</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button type="button" variant="ghost" size="sm" className="gap-1.5" onClick={() => handleOpenProject(project.id)}>
                        {t('projects.workspace')}
                          <ArrowRight className="h-3 w-3" />
                        </Button>
                        <Button type="button" variant="ghost" size="sm" onClick={(event) => { event.stopPropagation(); handleOpenProjectSettings(project.id) }}>
                          {t('projects.settings')}
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
                ? t('projects.newProject')
                : sheetMode === 'edit'
                  ? t('projects.editProject')
                  : selectedProject ? formatProjectTitle(selectedProject, t('common.untitledProject')) : t('projects.projectWorkspace')}
            </SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label={t('common.close')}
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
                      <dt className="text-sm font-medium">{t('common.name')}</dt>
                      <dd className="mt-1 break-words text-sm text-muted-foreground">{formatProjectTitle(selectedProject, t('common.untitledProject'))}</dd>
                    </div>
                    <div className="border-b pb-3">
                      <dt className="flex items-center gap-1 text-sm font-medium">
                        <Calendar className="h-3 w-3 text-muted-foreground" />
                        {t('common.created')}
                      </dt>
                      <dd className="mt-1 text-sm text-muted-foreground">{formatOptionalDate(selectedProject.createdAt, t('common.notAvailable'))}</dd>
                    </div>
                    <div className="border-b pb-3">
                      <dt className="flex items-center gap-1 text-sm font-medium">
                        <Clock className="h-3 w-3 text-muted-foreground" />
                        {t('common.updated')}
                      </dt>
                      <dd className="mt-1 text-sm text-muted-foreground">{formatOptionalDate(selectedProject.updatedAt, t('common.notAvailable'))}</dd>
                    </div>
                  </dl>

                  <Button type="button" variant="outline" className="gap-2" onClick={() => handleOpenProject(selectedProject.id)}>
                    {t('projects.openProject')}
                    <ArrowRight className="h-4 w-4" />
                  </Button>

                  <section className="space-y-3 border-t pt-5">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-sm font-semibold">{t('projects.projectMembers')}</h3>
                      <Badge variant="secondary">{projectMembers.length}</Badge>
                    </div>

                    <form className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" onSubmit={handleAssignMember}>
                      <NativeSelect className="w-full" value={assignMemberUserId} onChange={(event) => setAssignMemberUserId(event.target.value)} disabled={availableMemberUsers.length === 0}>
                        <NativeSelectOption value="">{t('projects.selectUser')}</NativeSelectOption>
                        {availableMemberUsers.map((user) => (
                          <NativeSelectOption key={user.id} value={user.id}>
                            {displayUser(user, t('common.unknownUser'))}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full" value={assignMemberRole} onChange={(event) => setAssignMemberRole(event.target.value as ProjectMember['role'])}>
                        {PROJECT_ROLE_OPTIONS.map((role) => (
                          <NativeSelectOption key={role} value={role}>
                            {translateRole(role, t)}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <Button type="submit" className="gap-2" disabled={isAssigningMember || !assignMemberUserId}>
                        {isAssigningMember ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                        {t('common.add')}
                      </Button>
                    </form>

                    {projectMembers.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">{t('projects.noDirectMembers')}</div>
                    ) : (
                      <div className="space-y-2">
                        {projectMembers.map((member) => {
                          const user = userById.get(member.userId)
                          return (
                            <div key={member.userId} className="grid gap-2 rounded-md border px-3 py-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-center">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{displayUser(user, t('common.unknownUser'))}</div>
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
                                    {translateRole(role, t)}
                                  </NativeSelectOption>
                                ))}
                              </NativeSelect>
                              <DeleteConfirmPopover
                                title={t('projects.removeMember')}
                                description={t('projects.removeMemberDescription')}
                                confirmLabel={t('common.remove')}
                                trigger={(
                                  <Button type="button" variant="ghost" size="icon-sm" aria-label={t('projects.removeMemberAction')}>
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
                      <h3 className="text-sm font-semibold">{t('projects.assignedTeams')}</h3>
                      <Badge variant="secondary">{projectTeams.length}</Badge>
                    </div>

                    <form className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" onSubmit={handleAssignTeam}>
                      <NativeSelect className="w-full" value={assignTeamId} onChange={(event) => setAssignTeamId(event.target.value)} disabled={availableTeams.length === 0}>
                        <NativeSelectOption value="">{t('projects.selectTeam')}</NativeSelectOption>
                        {availableTeams.map((team) => (
                          <NativeSelectOption key={team.id} value={team.id}>
                            {team.name}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full" value={assignTeamRole} onChange={(event) => setAssignTeamRole(event.target.value as ProjectTeam['role'])}>
                        {PROJECT_ROLE_OPTIONS.map((role) => (
                          <NativeSelectOption key={role} value={role}>
                            {translateRole(role, t)}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <Button type="submit" className="gap-2" disabled={isAssigningTeam || !assignTeamId}>
                        {isAssigningTeam ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                        {t('projects.assign')}
                      </Button>
                    </form>

                    {projectTeams.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">{t('projects.noTeams')}</div>
                    ) : (
                      <div className="space-y-2">
                        {projectTeams.map((projectTeam) => {
                          const team = teamById.get(projectTeam.teamId)
                          return (
                            <div key={projectTeam.teamId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{team?.name || t('common.unknownTeam')}</div>
                                <div className="truncate text-xs text-muted-foreground">{translateRole(projectTeam.role, t)}</div>
                              </div>
                              <DeleteConfirmPopover
                                title={t('projects.removeTeam')}
                                description={t('projects.removeTeamDescription')}
                                confirmLabel={t('common.remove')}
                                trigger={(
                                  <Button type="button" variant="ghost" size="icon-sm" aria-label={t('projects.removeTeamAction')}>
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
                <p className="text-sm text-muted-foreground">{t('projects.selectProject')}</p>
              )
            ) : (
              <form
                className="space-y-5"
                onSubmit={sheetMode === 'create' ? handleCreateProject : handleUpdateProject}
              >
                <div className="space-y-2">
                  <label className="flex items-center gap-2 text-sm font-semibold">{t('common.name')}</label>
                  <Input
                    value={form.title}
                    onChange={(event) => setForm({ title: event.target.value })}
                    required
                  />
                </div>

                {sheetMode === 'create' ? (
                  <section className="space-y-3 border-t pt-5">
                    <label className="block text-sm font-semibold">{t('projects.owners')}</label>

                    <div className="flex gap-2">
                      <NativeSelect className="w-28 shrink-0" value={createOwnerType} onChange={(event) => setCreateOwnerType(event.target.value as 'USER' | 'TEAM')}>
                        <NativeSelectOption value="USER">{t('common.user')}</NativeSelectOption>
                        <NativeSelectOption value="TEAM">{t('common.team')}</NativeSelectOption>
                      </NativeSelect>
                      <NativeSelect
                        className="min-w-0 flex-1"
                        value={createOwnerType === 'USER' ? createOwnerUserId : createOwnerTeamId}
                        onChange={(event) => createOwnerType === 'USER' ? setCreateOwnerUserId(event.target.value) : setCreateOwnerTeamId(event.target.value)}
                        disabled={createOwnerType === 'USER' ? createOwnerUsers.length === 0 : createOwnerTeams.length === 0}
                      >
                        <NativeSelectOption value="">{createOwnerType === 'USER' ? t('projects.selectOwner') : t('projects.selectTeam')}</NativeSelectOption>
                        {createOwnerType === 'USER'
                          ? createOwnerUsers.map((user) => <NativeSelectOption key={user.id} value={user.id}>{displayUser(user, t('common.unknownUser'))}</NativeSelectOption>)
                          : createOwnerTeams.map((team) => <NativeSelectOption key={team.id} value={team.id}>{team.name}</NativeSelectOption>)}
                      </NativeSelect>
                      <Button type="button" variant="outline" size="icon" onClick={handleAddCreateOwner} disabled={createOwnerType === 'USER' ? !createOwnerUserId : !createOwnerTeamId} aria-label={t('projects.addOwner')}>
                        <Plus className="h-4 w-4" />
                      </Button>
                    </div>

                    {createOwnerUserIds.length === 0 && createOwnerTeamIds.length === 0 ? (
                      <div className="rounded-md border border-dashed px-4 py-5 text-sm text-muted-foreground">
                        {t('projects.noOwnersSelected')}
                      </div>
                    ) : (
                      <div className="space-y-2">
                        {createOwnerUserIds.map((userId) => {
                          const user = userById.get(userId)
                          return (
                            <div key={userId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                              <div className="min-w-0">
                                <div className="truncate text-sm font-medium">{displayUser(user, t('common.unknownUser'))}</div>
                                {user?.email ? <div className="truncate text-xs text-muted-foreground">{user.email}</div> : null}
                              </div>
                              <Button type="button" variant="ghost" size="icon-sm" onClick={() => handleRemoveCreateOwnerUser(userId)} aria-label={t('projects.removeUserOwner')}>
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
                                <div className="truncate text-sm font-medium">{team?.name || t('common.unknownTeam')}</div>
                                <div className="truncate text-xs text-muted-foreground">{t('projects.teamOwner')}</div>
                              </div>
                              <Button type="button" variant="ghost" size="icon-sm" onClick={() => handleRemoveCreateOwnerTeam(teamId)} aria-label={t('projects.removeTeamOwner')}>
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
                      t('common.saving')
                    ) : (
                      <>
                        <Save className="h-4 w-4" />
                        {sheetMode === 'create' ? t('projects.createProject') : t('common.saveChanges')}
                      </>
                    )}
                  </Button>
                  <Button type="button" variant="outline" className="gap-2" onClick={() => setIsSheetOpen(false)} disabled={isSubmitting}>
                    <X className="h-4 w-4" />
                    {t('common.cancel')}
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
                title={t('projects.deleteProject')}
                description={t('projects.deleteProjectDescription')}
                confirmLabel={t('projects.deletePermanently')}
                disabled={isDeleting}
                trigger={(
                  <Button variant="destructive" disabled={isDeleting} className="gap-2">
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? t('common.deleting') : t('projects.deleteProjectButton')}
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
