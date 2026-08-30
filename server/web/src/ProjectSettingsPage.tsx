import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronRight, FolderOpen, Loader2, Plus, Save, Trash2, UserPlus, UsersRound, X } from 'lucide-react'
import { NavLink, useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'

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

const PROJECT_ROLE_OPTIONS: ProjectTeam['role'][] = ['VIEWER', 'EDITOR', 'OWNER']
const ACCESS_ROLE_GROUPS: ProjectTeam['role'][] = ['OWNER', 'EDITOR', 'VIEWER']
type AddAccessMode = 'member' | 'team'

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

export default function ProjectSettingsPage({ currentUser }: { currentUser: AuthUser | null }) {
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
    params.set('chatPanel', 'open')
    return `${path}?${params.toString()}`
  }
  const [isAssigningTeam, setIsAssigningTeam] = useState(false)
  const [isAssigningMember, setIsAssigningMember] = useState(false)
  const [updatingMemberUserId, setUpdatingMemberUserId] = useState<string | null>(null)
  const [updatingTeamId, setUpdatingTeamId] = useState<string | null>(null)

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
      setEditTitle(formatProjectTitle(nextProject))
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
      toast.error('Project name is required.')
      return
    }

    setIsUpdating(true)
    try {
      const updated = await updateProject(project.id, { title })
      setProject(updated)
      setEditTitle(formatProjectTitle(updated))
      toast.success('Project updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update project.')
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
      toast.success('Project deleted.')
      navigate(workspaceDestination('/app/projects'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete project.')
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
      toast.success('Project member added.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to add project member.')
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
      toast.success('Project member updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update project member.')
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
      toast.success('Project member removed.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to remove project member.')
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
      toast.success('Team assigned.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to assign team.')
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
      toast.success('Team access updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update team access.')
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
      toast.success('Team removed from project.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to remove team.')
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">Projects</h1>
        </div>
        <div className="flex min-w-0 flex-1 items-center justify-center overflow-auto p-6">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      </div>
    )
  }

  if (errorMessage || !project) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">Projects</h1>
        </div>
        <Empty className="min-w-0 flex-1 overflow-auto">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FolderOpen />
            </EmptyMedia>
            <EmptyTitle>{errorMessage || 'Project not found.'}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="flex min-w-0 items-center gap-2 text-xl font-semibold leading-none tracking-normal">
          <NavLink to={workspaceDestination('/app/projects')} className="shrink-0 text-muted-foreground hover:text-foreground">
            Projects
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <NavLink to={workspaceDestination(`/app/projects/${project.id}`)} className="flex min-w-0 items-center gap-1.5 text-muted-foreground hover:text-foreground">
            <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="truncate">{formatProjectTitle(project)}</span>
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="truncate">Settings</span>
        </h1>
      </div>

      <div className="min-w-0 flex-1 overflow-auto p-4 md:p-6">
        <Tabs defaultValue="info" className="gap-4">
          <TabsList variant="line" className="border-b">
            <TabsTrigger value="info">Info</TabsTrigger>
            <TabsTrigger value="access">Access</TabsTrigger>
          </TabsList>

          <TabsContent value="info">
            <section className="max-w-3xl space-y-4 rounded-md border bg-background p-4">
              <div>
                <h2 className="text-sm font-semibold">Project info</h2>
              </div>
              <form className="space-y-4" onSubmit={handleUpdateProject}>
                <div className="grid gap-2 sm:grid-cols-[8rem_minmax(0,1fr)] sm:items-center">
                  <label className="text-sm font-medium">Name</label>
                  <Input value={editTitle} onChange={(event) => setEditTitle(event.target.value)} />
                </div>
                <div className="flex justify-end">
                  <Button type="submit" className="gap-2" disabled={isUpdating || editTitle.trim() === formatProjectTitle(project)}>
                    {isUpdating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    Save
                  </Button>
                </div>
              </form>

              <div className="flex border-t pt-4">
                <DeleteConfirmPopover
                  title="Delete project?"
                  description="Project nodes and access assignments will be removed."
                  confirmLabel="Delete project"
                  disabled={isDeleting}
                  trigger={(
                    <Button type="button" variant="destructive" className="gap-2" disabled={isDeleting}>
                      <Trash2 className="h-4 w-4" />
                      {isDeleting ? 'Deleting...' : 'Delete'}
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
                <h2 className="text-sm font-semibold">Access summary</h2>
                <dl className="mt-4 grid gap-3 sm:grid-cols-4">
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">Your access</dt>
                    <dd className="mt-1 text-sm">{currentMembership?.role ?? 'Not direct'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">Members</dt>
                    <dd className="mt-1 text-sm">{projectMembers.length}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">Teams</dt>
                    <dd className="mt-1 text-sm">{projectTeams.length}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase text-muted-foreground">Updated</dt>
                    <dd className="mt-1 text-sm">{formatOptionalDate(project.updatedAt)}</dd>
                  </div>
                </dl>
              </section>

              <section className="space-y-3 rounded-md border bg-background p-4">
                <div className="flex items-center justify-between gap-3">
                  <h2 className="text-sm font-semibold">Access by role</h2>
                  <Badge variant="secondary">{projectMembers.length + projectTeams.length}</Badge>
                </div>

                <div className="grid gap-3 xl:grid-cols-3">
                  {ACCESS_ROLE_GROUPS.map((role) => {
                    const roleMembers = projectMembers.filter((member) => member.role === role)
                    const roleTeams = projectTeams.filter((projectTeam) => projectTeam.role === role)
                    return (
                      <div key={role} className="space-y-3 rounded-md border p-3">
                        <div className="flex items-center justify-between gap-2">
                          <h3 className="text-sm font-medium">{role}</h3>
                          <Badge variant="outline">{roleMembers.length + roleTeams.length}</Badge>
                        </div>

                        {roleMembers.length === 0 && roleTeams.length === 0 ? (
                          <div className="rounded-md border border-dashed px-3 py-5 text-sm text-muted-foreground">No access assigned.</div>
                        ) : (
                          <div className="space-y-2">
                            {roleMembers.map((member) => {
                              const user = userById.get(member.userId)
                              return (
                                <div key={`member-${member.userId}`} className="grid gap-2 rounded-md border px-3 py-2 sm:grid-cols-[minmax(0,1fr)_8.5rem_auto] sm:items-center xl:grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_8.5rem_auto]">
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                      <UserPlus className="h-3.5 w-3.5 text-muted-foreground" />
                                      <div className="truncate text-sm font-medium">{displayUser(user)}</div>
                                    </div>
                                  </div>
                                  <NativeSelect
                                    className="w-full sm:w-36"
                                    value={member.role}
                                    onChange={(event) => void handleUpdateMemberRole(member.userId, event.target.value as ProjectMember['role'])}
                                    disabled={updatingMemberUserId === member.userId}
                                  >
                                    {PROJECT_ROLE_OPTIONS.map((nextRole) => (
                                      <NativeSelectOption key={nextRole} value={nextRole}>
                                        {nextRole}
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

                            {roleTeams.map((projectTeam) => {
                              const team = teamById.get(projectTeam.teamId)
                              return (
                                <div key={`team-${projectTeam.teamId}`} className="grid gap-2 rounded-md border px-3 py-2 sm:grid-cols-[minmax(0,1fr)_8.5rem_auto] sm:items-center xl:grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_8.5rem_auto]">
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                      <UsersRound className="h-3.5 w-3.5 text-muted-foreground" />
                                      <div className="truncate text-sm font-medium">{team?.name || projectTeam.teamId}</div>
                                    </div>
                                    <div className="truncate font-mono text-xs text-muted-foreground">{projectTeam.teamId}</div>
                                  </div>
                                  <NativeSelect
                                    className="w-full sm:w-36"
                                    value={projectTeam.role}
                                    onChange={(event) => void handleUpdateTeamRole(projectTeam.teamId, event.target.value as ProjectTeam['role'])}
                                    disabled={updatingTeamId === projectTeam.teamId}
                                  >
                                    {PROJECT_ROLE_OPTIONS.map((nextRole) => (
                                      <NativeSelectOption key={nextRole} value={nextRole}>
                                        {nextRole}
                                      </NativeSelectOption>
                                    ))}
                                  </NativeSelect>
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
                      </div>
                    )
                  })}
                </div>
              </section>

              <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                <h2 className="text-sm font-semibold">Add access</h2>

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
                      Member
                    </ToggleGroupItem>
                    <ToggleGroupItem value="team" className="gap-2">
                      <UsersRound className="h-4 w-4" />
                      Team
                    </ToggleGroupItem>
                  </ToggleGroup>

                  {addAccessMode === 'member' ? (
                    <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAssignMember}>
                      <NativeSelect className="w-full sm:w-72" value={assignMemberUserId} onChange={(event) => setAssignMemberUserId(event.target.value)} disabled={availableMemberUsers.length === 0}>
                        <NativeSelectOption value="">Select user</NativeSelectOption>
                        {availableMemberUsers.map((user) => (
                          <NativeSelectOption key={user.id} value={user.id}>
                            {displayUser(user)}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full sm:w-36" value={assignMemberRole} onChange={(event) => setAssignMemberRole(event.target.value as ProjectMember['role'])}>
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
                  ) : (
                    <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAssignTeam}>
                      <NativeSelect className="w-full sm:w-72" value={assignTeamId} onChange={(event) => setAssignTeamId(event.target.value)} disabled={availableTeams.length === 0}>
                        <NativeSelectOption value="">Select team</NativeSelectOption>
                        {availableTeams.map((team) => (
                          <NativeSelectOption key={team.id} value={team.id}>
                            {team.name}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                      <NativeSelect className="w-full sm:w-36" value={assignTeamRole} onChange={(event) => setAssignTeamRole(event.target.value as ProjectTeam['role'])}>
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
