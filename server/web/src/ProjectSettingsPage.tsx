import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronRight, FolderOpen, Loader2, Plus, Save, Trash2, UserPlus, UsersRound, X } from 'lucide-react'
import { NavLink, useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import {
  assignProjectTeam,
  getProject,
  listProjectMembers,
  listProjectTeams,
  listTeams,
  loadSelectableUsers,
  removeProjectMember,
  request,
  unassignProjectTeam,
  updateProject,
  upsertProjectMember,
  type AuthUser,
  type Project,
  type ProjectMember,
  type ProjectTeam,
  type Team,
  type User,
} from '@/lib/api'
import { translateRole } from '@/i18n/labels'

const PROJECT_ROLE_OPTIONS: ProjectTeam['role'][] = ['VIEWER', 'EDITOR', 'OWNER']
const ACCESS_ROLE_GROUPS: ProjectTeam['role'][] = ['OWNER', 'EDITOR', 'VIEWER']
type AddAccessMode = 'member' | 'team'

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

export default function ProjectSettingsPage({ currentUser }: { currentUser: AuthUser | null }) {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const { projectId } = useParams()
  const [project, setProject] = useState<Project | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [projectMembers, setProjectMembers] = useState<ProjectMember[]>([])
  const [projectTeams, setProjectTeams] = useState<ProjectTeam[]>([])
  const [editTitle, setEditTitle] = useState('')
  const [assignTeamId, setAssignTeamId] = useState('')
  const [assignTeamRole, setAssignTeamRole] = useState<ProjectTeam['role']>('VIEWER')
  const [assignMemberUserId, setAssignMemberUserId] = useState('')
  const [assignMemberRole, setAssignMemberRole] = useState<ProjectMember['role']>('VIEWER')
  const [addAccessMode, setAddAccessMode] = useState<AddAccessMode>('member')
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  function workspaceDestination(path: string) {
    const params = new URLSearchParams(location.search)
    if (!params.get('chatSessionId')) {
      return path
    }
    params.set('chatPanel', params.get('chatPanel') === 'closed' ? 'closed' : 'open')
    return `${path}?${params.toString()}`
  }
  const [isAssigningTeam, setIsAssigningTeam] = useState(false)
  const [isAssigningMember, setIsAssigningMember] = useState(false)
  const [updatingMemberUserId, setUpdatingMemberUserId] = useState<string | null>(null)
  const [updatingTeamId, setUpdatingTeamId] = useState<string | null>(null)

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
  const availableMemberUsers = usersForSelection.filter((user) => !projectMemberUserIds.has(user.id))
  const currentMembership = currentUser ? projectMembers.find((member) => member.userId === currentUser.id) ?? null : null

  async function loadPage() {
    if (!projectId) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const [nextProject, nextTeams, nextUsers, nextProjectMembers, nextProjectTeams] = await Promise.all([
        getProject(projectId),
        listTeams(),
        loadSelectableUsers(),
        listProjectMembers(projectId),
        listProjectTeams(projectId),
      ])

      setProject(nextProject)
      setEditTitle(formatProjectTitle(nextProject, t('common.untitledProject')))
      setTeams(nextTeams)
      setUsers(nextUsers)
      setProjectMembers(nextProjectMembers)
      setProjectTeams(nextProjectTeams)
      setAssignMemberUserId('')
      setAssignMemberRole('VIEWER')
      setAssignTeamId('')
      setAssignTeamRole('VIEWER')
    } catch (error) {
      setProject(null)
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load project settings.')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage()
    })
    // Reload when the route target changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  async function loadProjectRelations() {
    if (!projectId) {
      return
    }

    const [nextProjectMembers, nextProjectTeams] = await Promise.all([
      listProjectMembers(projectId),
      listProjectTeams(projectId),
    ])
    setProjectMembers(nextProjectMembers)
    setProjectTeams(nextProjectTeams)
  }

  async function handleUpdateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!project) {
      return
    }

    const title = editTitle.trim()
    if (!title) {
      toast.error(t('projects.nameRequired'))
      return
    }

    setIsUpdating(true)
    try {
      const updated = await updateProject(project.id, { title })
      setProject(updated)
      setEditTitle(formatProjectTitle(updated, t('common.untitledProject')))
      toast.success(t('projects.projectUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedUpdate'))
    } finally {
      setIsUpdating(false)
    }
  }

  async function handleDeleteProject() {
    if (!project) {
      return
    }

    setIsDeleting(true)
    try {
      await request<void>(`/internal-api/v1/projects/${project.id}`, { method: 'DELETE' })
      toast.success(t('projects.projectDeleted'))
      navigate(workspaceDestination('/app/projects'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedDelete'))
    } finally {
      setIsDeleting(false)
    }
  }

  async function handleAssignMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!project || !assignMemberUserId) {
      return
    }

    setIsAssigningMember(true)
    try {
      await upsertProjectMember(project.id, assignMemberUserId, assignMemberRole)
      setAssignMemberUserId('')
      setAssignMemberRole('VIEWER')
      await loadProjectRelations()
      toast.success(t('projects.memberAdded'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedAddMember'))
    } finally {
      setIsAssigningMember(false)
    }
  }

  async function handleUpdateMemberRole(userId: string, role: ProjectMember['role']) {
    if (!project) {
      return
    }

    setUpdatingMemberUserId(userId)
    try {
      await upsertProjectMember(project.id, userId, role)
      setProjectMembers((current) => current.map((member) => (
        member.userId === userId ? { ...member, role } : member
      )))
      toast.success(t('projects.memberUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedUpdateMember'))
      await loadProjectRelations()
    } finally {
      setUpdatingMemberUserId(null)
    }
  }

  async function handleRemoveMember(userId: string) {
    if (!project) {
      return
    }

    try {
      await removeProjectMember(project.id, userId)
      setProjectMembers((current) => current.filter((member) => member.userId !== userId))
      toast.success(t('projects.memberRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedRemoveMember'))
    }
  }

  async function handleAssignTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!project || !assignTeamId) {
      return
    }

    setIsAssigningTeam(true)
    try {
      await assignProjectTeam(project.id, assignTeamId, assignTeamRole)
      setAssignTeamId('')
      setAssignTeamRole('VIEWER')
      await loadProjectRelations()
      toast.success(t('projects.teamAssigned'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedAssignTeam'))
    } finally {
      setIsAssigningTeam(false)
    }
  }

  async function handleUpdateTeamRole(teamId: string, role: ProjectTeam['role']) {
    if (!project) {
      return
    }

    setUpdatingTeamId(teamId)
    try {
      await assignProjectTeam(project.id, teamId, role)
      setProjectTeams((current) => current.map((projectTeam) => (
        projectTeam.teamId === teamId ? { ...projectTeam, role } : projectTeam
      )))
      toast.success(t('projectSettings.teamAccessUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projectSettings.failedUpdateTeamAccess'))
      await loadProjectRelations()
    } finally {
      setUpdatingTeamId(null)
    }
  }

  async function handleUnassignTeam(teamId: string) {
    if (!project) {
      return
    }

    try {
      await unassignProjectTeam(project.id, teamId)
      setProjectTeams((current) => current.filter((projectTeam) => projectTeam.teamId !== teamId))
      toast.success(t('projects.teamRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('projects.failedRemoveTeam'))
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
          <h1 className="text-xl font-semibold leading-none tracking-normal">{t('projectSettings.pageTitle')}</h1>
        </div>
        <div className="flex min-w-0 flex-1 items-center justify-center overflow-auto p-4">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      </div>
    )
  }

  if (errorMessage || !project) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
          <h1 className="text-xl font-semibold leading-none tracking-normal">{t('projectSettings.pageTitle')}</h1>
        </div>
        <Empty className="min-w-0 flex-1 overflow-auto">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FolderOpen />
            </EmptyMedia>
            <EmptyTitle>{errorMessage || t('projectSettings.projectNotFound')}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
        <h1 className="flex min-w-0 items-center gap-2 text-xl font-semibold leading-none tracking-normal">
          <NavLink to={workspaceDestination('/app/projects')} className="shrink-0 text-muted-foreground hover:text-foreground">
            {t('projectSettings.pageTitle')}
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <NavLink to={workspaceDestination(`/app/projects/${project.id}`)} className="flex min-w-0 items-center gap-1.5 text-muted-foreground hover:text-foreground">
            <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="truncate">{formatProjectTitle(project, t('common.untitledProject'))}</span>
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="truncate">{t('projectSettings.settings')}</span>
        </h1>
      </div>

      <div className="min-w-0 flex-1 overflow-auto p-3 md:p-4">
        <Tabs defaultValue="info" className="gap-3">
          <TabsList variant="line" className="border-b">
            <TabsTrigger value="info">{t('projectSettings.info')}</TabsTrigger>
            <TabsTrigger value="access">{t('projectSettings.access')}</TabsTrigger>
          </TabsList>

          <TabsContent value="info">
            <section className="max-w-3xl space-y-4 rounded-md border bg-background p-4">
              <div>
                <h2 className="text-sm font-semibold">{t('projectSettings.projectInfo')}</h2>
              </div>
              <form className="space-y-4" onSubmit={handleUpdateProject}>
                <div className="grid gap-2 sm:grid-cols-[8rem_minmax(0,1fr)] sm:items-center">
                  <label className="text-sm font-medium">{t('common.name')}</label>
                  <Input value={editTitle} onChange={(event) => setEditTitle(event.target.value)} />
                </div>
                <div className="flex justify-end">
                  <Button type="submit" className="gap-2" disabled={isUpdating || editTitle.trim() === formatProjectTitle(project, t('common.untitledProject'))}>
                    {isUpdating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    {t('common.save')}
                  </Button>
                </div>
              </form>

              <div className="flex border-t pt-4">
                <DeleteConfirmPopover
                  title={t('projectSettings.deleteProject')}
                  description={t('projectSettings.deleteProjectDescription')}
                  confirmLabel={t('projects.deleteProjectButton')}
                  disabled={isDeleting}
                  trigger={(
                    <Button type="button" variant="destructive" className="gap-2" disabled={isDeleting}>
                      <Trash2 className="h-4 w-4" />
                      {isDeleting ? t('common.deleting') : t('common.delete')}
                    </Button>
                  )}
                  onConfirm={handleDeleteProject}
                />
              </div>
            </section>
          </TabsContent>

          <TabsContent value="access">
            <div className="space-y-4">
              <section className="rounded-md border bg-background p-4">
                <h2 className="text-sm font-semibold">{t('projectSettings.accessSummary')}</h2>
                <dl className="mt-4 grid gap-3 sm:grid-cols-4">
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">{t('projectSettings.yourAccess')}</dt>
                    <dd className="mt-1 text-sm">{currentMembership?.role ? translateRole(currentMembership.role, t) : t('projectSettings.notDirect')}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">{t('common.members')}</dt>
                    <dd className="mt-1 text-sm">{projectMembers.length}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">{t('common.teams')}</dt>
                    <dd className="mt-1 text-sm">{projectTeams.length}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">{t('common.updated')}</dt>
                    <dd className="mt-1 text-sm">{formatOptionalDate(project.updatedAt, t('common.notAvailable'))}</dd>
                  </div>
                </dl>
              </section>

              <section className="space-y-3 rounded-md border bg-background p-4">
                <div className="flex items-center justify-between gap-3">
                  <h2 className="text-sm font-semibold">{t('projectSettings.accessByRole')}</h2>
                  <Badge variant="secondary">{projectMembers.length + projectTeams.length}</Badge>
                </div>

                <div className="grid gap-3 xl:grid-cols-3">
                  {ACCESS_ROLE_GROUPS.map((role) => {
                    const roleMembers = projectMembers.filter((member) => member.role === role)
                    const roleTeams = projectTeams.filter((projectTeam) => projectTeam.role === role)
                    return (
                      <div key={role} className="space-y-3 rounded-md border p-3">
                        <div className="flex items-center justify-between gap-2">
                          <h3 className="text-sm font-medium">{role === 'OWNER' ? t('role.owner') : role === 'EDITOR' ? t('role.editor') : t('role.viewer')}</h3>
                          <Badge variant="outline">{roleMembers.length + roleTeams.length}</Badge>
                        </div>

                        {roleMembers.length === 0 && roleTeams.length === 0 ? (
                          <div className="rounded-md border border-dashed px-3 py-4 text-sm text-muted-foreground">{t('projectSettings.noAccess')}</div>
                        ) : (
                          <div className="space-y-2">
                            {roleMembers.map((member) => {
                              const user = userById.get(member.userId)
                              return (
                                <div key={`member-${member.userId}`} className="grid gap-2 rounded-md border px-3 py-1.5 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-center">
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                      <UserPlus className="h-3.5 w-3.5 text-muted-foreground" />
                                      <div className="truncate text-sm font-medium">{displayUser(user, t('common.unknownUser'))}</div>
                                    </div>
                                  </div>
                                  <NativeSelect
                                    className="w-full sm:w-32"
                                    value={member.role}
                                    onChange={(event) => void handleUpdateMemberRole(member.userId, event.target.value as ProjectMember['role'])}
                                    disabled={updatingMemberUserId === member.userId}
                                  >
                                    {PROJECT_ROLE_OPTIONS.map((nextRole) => (
                                      <NativeSelectOption key={nextRole} value={nextRole}>
                                        {translateRole(nextRole, t)}
                                      </NativeSelectOption>
                                    ))}
                                  </NativeSelect>
                                  <DeleteConfirmPopover
                                    title={t('projectSettings.removeMember')}
                                    description={t('projectSettings.removeMemberDescription')}
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

                            {roleTeams.map((projectTeam) => {
                              return (
                                <div key={`team-${projectTeam.teamId}`} className="grid gap-2 rounded-md border px-3 py-1.5 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-center">
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                      <UsersRound className="h-3.5 w-3.5 text-muted-foreground" />
                                      {projectTeam.teamName?.trim() ? <div className="truncate text-sm font-medium">{projectTeam.teamName.trim()}</div> : null}
                                    </div>
                                  </div>
                                  <NativeSelect
                                    className="w-full sm:w-32"
                                    value={projectTeam.role}
                                    onChange={(event) => void handleUpdateTeamRole(projectTeam.teamId, event.target.value as ProjectTeam['role'])}
                                    disabled={updatingTeamId === projectTeam.teamId}
                                  >
                                    {PROJECT_ROLE_OPTIONS.map((nextRole) => (
                                      <NativeSelectOption key={nextRole} value={nextRole}>
                                      {translateRole(nextRole, t)}
                                      </NativeSelectOption>
                                    ))}
                                  </NativeSelect>
                                  <DeleteConfirmPopover
                                    title={t('projectSettings.removeTeam')}
                                    description={t('projectSettings.removeTeamDescription')}
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
                      </div>
                    )
                  })}
                </div>
              </section>

              <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                <h2 className="text-sm font-semibold">{t('projectSettings.addAccess')}</h2>

                <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
                  <ToggleGroup
                    value={[addAccessMode]}
                    onValueChange={(nextValue) => {
                      const nextMode = nextValue[0] as AddAccessMode | undefined
                      if (nextMode) {
                        setAddAccessMode(nextMode)
                      }
                    }}
                    variant="outline"
                    spacing={0}
                    className="w-fit"
                  >
                    <ToggleGroupItem value="member" className="gap-2">
                      <UserPlus className="h-4 w-4" />
                      {t('common.member')}
                    </ToggleGroupItem>
                    <ToggleGroupItem value="team" className="gap-2">
                      <UsersRound className="h-4 w-4" />
                      {t('common.team')}
                    </ToggleGroupItem>
                  </ToggleGroup>

                  {addAccessMode === 'member' ? (
                    <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAssignMember}>
                      <NativeSelect className="w-full sm:w-72" value={assignMemberUserId} onChange={(event) => setAssignMemberUserId(event.target.value)} disabled={availableMemberUsers.length === 0}>
                        <NativeSelectOption value="">{t('projectSettings.selectUser')}</NativeSelectOption>
                        {availableMemberUsers.map((user) => (
                          <NativeSelectOption key={user.id} value={user.id}>
                            {displayUser(user, t('common.unknownUser'))}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full sm:w-36" value={assignMemberRole} onChange={(event) => setAssignMemberRole(event.target.value as ProjectMember['role'])}>
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
                  ) : (
                    <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAssignTeam}>
                      <NativeSelect className="w-full sm:w-72" value={assignTeamId} onChange={(event) => setAssignTeamId(event.target.value)} disabled={availableTeams.length === 0}>
                        <NativeSelectOption value="">{t('projectSettings.selectTeam')}</NativeSelectOption>
                        {availableTeams.map((team) => (
                          <NativeSelectOption key={team.id} value={team.id}>
                            {team.name}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full sm:w-36" value={assignTeamRole} onChange={(event) => setAssignTeamRole(event.target.value as ProjectTeam['role'])}>
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
                  )}
                </div>
              </section>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
