import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Check, ChevronRight, FolderOpen, Loader2, Plus, Save, Trash2, UserPlus, UsersRound, X } from 'lucide-react'
import { NavLink, useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import {
  addTeamProject,
  decideTeamJoinRequest,
  deleteTeam,
  listProjects,
  listTeamJoinRequests,
  listTeamMembers,
  listTeamProjects,
  listTeams,
  loadSelectableUsers,
  removeTeamMember,
  removeTeamProject,
  requestTeamJoin,
  upsertTeamMember,
  updateTeam,
  type AuthUser,
  type Project,
  type ProjectTeam,
  type Team,
  type TeamJoinRequest,
  type TeamMember,
  type User,
} from '@/lib/api'

function displayUser(user: User | AuthUser | null | undefined) {
  if (!user) {
    return 'Unknown user'
  }

  return user.displayName?.trim() || user.username
}

function displayProject(project: Project | null | undefined) {
  if (!project) {
    return 'Unknown project'
  }

  return project.title?.trim() || project.name?.trim() || project.id
}

const TEAM_ROLE_OPTIONS: TeamMember['role'][] = ['TEAM_MEMBER', 'TEAM_OWNER']
const PROJECT_ROLE_OPTIONS: ProjectTeam['role'][] = ['VIEWER', 'EDITOR', 'OWNER']

export default function TeamDetailsPage({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { teamId } = useParams()
  const [team, setTeam] = useState<Team | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [members, setMembers] = useState<TeamMember[]>([])
  const [joinRequests, setJoinRequests] = useState<TeamJoinRequest[]>([])
  const [projectLinks, setProjectLinks] = useState<ProjectTeam[]>([])
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [memberUserId, setMemberUserId] = useState('')
  const [memberRole, setMemberRole] = useState<TeamMember['role']>('TEAM_MEMBER')
  const [projectId, setProjectId] = useState('')
  const [projectRole, setProjectRole] = useState<ProjectTeam['role']>('VIEWER')
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isAddingMember, setIsAddingMember] = useState(false)
  const [isAddingProject, setIsAddingProject] = useState(false)
  const [isRequestingJoin, setIsRequestingJoin] = useState(false)
  const [isDecidingJoinRequestId, setIsDecidingJoinRequestId] = useState<string | null>(null)

  const userById = useMemo(() => {
    const nextUserById = new Map<string, User | AuthUser>(users.map((user) => [user.id, user]))
    if (currentUser) {
      nextUserById.set(currentUser.id, currentUser)
    }
    return nextUserById
  }, [currentUser, users])
  const projectById = useMemo(() => new Map(projects.map((project) => [project.id, project])), [projects])
  const memberUserIds = useMemo(() => new Set(members.map((member) => member.userId)), [members])
  const linkedProjectIds = useMemo(() => new Set(projectLinks.map((link) => link.projectId)), [projectLinks])
  const currentMembership = currentUser ? members.find((member) => member.userId === currentUser.id) ?? null : null
  const isAdminLike = currentUser?.globalRole === 'ADMIN' || currentUser?.globalRole === 'SUPERADMIN'
  const canManageTeamLinks = isAdminLike || currentMembership?.role === 'TEAM_OWNER'
  const isCurrentUserMember = Boolean(currentMembership)
  const usersForSelection = useMemo(() => {
    if (!currentUser || currentUser.globalRole?.toUpperCase() === 'SUPERADMIN' || users.some((user) => user.id === currentUser.id)) {
      return users
    }
    return [currentUser, ...users]
  }, [currentUser, users])
  const availableUsers = usersForSelection.filter((user) => !memberUserIds.has(user.id))
  const availableProjects = projects.filter((project) => !linkedProjectIds.has(project.id))

  function workspaceDestination(path: string) {
    const params = new URLSearchParams(location.search)
    if (!params.get('chatSessionId')) {
      return path
    }
    params.set('chatPanel', params.get('chatPanel') === 'closed' ? 'closed' : 'open')
    return `${path}?${params.toString()}`
  }

  async function loadPage() {
    if (!teamId) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const [nextTeams, nextUsers, nextProjects, nextMembers, nextProjectLinks, nextJoinRequests] = await Promise.all([
        listTeams(),
        loadSelectableUsers(),
        listProjects(),
        listTeamMembers(teamId),
        listTeamProjects(teamId),
        listTeamJoinRequests(teamId).catch(() => []),
      ])
      const nextTeam = nextTeams.find((item) => item.id === teamId) ?? null
      if (!nextTeam) {
        setTeam(null)
        setErrorMessage('Team not found.')
        return
      }

      setTeam(nextTeam)
      setEditName(nextTeam.name)
      setEditDescription(nextTeam.description ?? '')
      setUsers(nextUsers)
      setProjects(nextProjects)
      setMembers(nextMembers)
      setProjectLinks(nextProjectLinks)
      setJoinRequests(nextJoinRequests)
      setMemberUserId('')
      setMemberRole('TEAM_MEMBER')
      setProjectId('')
      setProjectRole('VIEWER')
    } catch (error) {
      setTeam(null)
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load team.')
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
  }, [teamId])

  async function reloadRelations() {
    if (!teamId) {
      return
    }

    const [nextMembers, nextProjectLinks, nextJoinRequests] = await Promise.all([
      listTeamMembers(teamId),
      listTeamProjects(teamId),
      listTeamJoinRequests(teamId).catch(() => []),
    ])
    setMembers(nextMembers)
    setProjectLinks(nextProjectLinks)
    setJoinRequests(nextJoinRequests)
  }

  async function handleUpdateTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!team) {
      return
    }

    const name = editName.trim()
    if (!name) {
      toast.error('Team name is required.')
      return
    }

    setIsUpdating(true)

    try {
      const updated = await updateTeam(team.id, { name, description: editDescription.trim() || null })
      setTeam(updated)
      setEditName(updated.name)
      setEditDescription(updated.description ?? '')
      toast.success('Team updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update team.')
    } finally {
      setIsUpdating(false)
    }
  }

  async function handleDeleteTeam() {
    if (!team) {
      return
    }

    setIsDeleting(true)

    try {
      await deleteTeam(team.id)
      toast.success('Team deleted.')
      navigate(workspaceDestination('/app/teams'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete team.')
    } finally {
      setIsDeleting(false)
    }
  }

  async function handleAddMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!team || !memberUserId) {
      return
    }

    setIsAddingMember(true)

    try {
      await upsertTeamMember(team.id, memberUserId, memberRole)
      setMemberUserId('')
      setMemberRole('TEAM_MEMBER')
      await reloadRelations()
      toast.success('Member added.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to add member.')
    } finally {
      setIsAddingMember(false)
    }
  }

  async function handleRemoveMember(userId: string) {
    if (!team) {
      return
    }

    try {
      await removeTeamMember(team.id, userId)
      setMembers((current) => current.filter((member) => member.userId !== userId))
      toast.success('Member removed.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to remove member.')
    }
  }

  async function handleAddProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!team || !projectId) {
      return
    }

    setIsAddingProject(true)

    try {
      await addTeamProject(team.id, projectId, projectRole)
      setProjectId('')
      setProjectRole('VIEWER')
      await reloadRelations()
      toast.success('Project linked.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to link project.')
    } finally {
      setIsAddingProject(false)
    }
  }

  async function handleRemoveProject(nextProjectId: string) {
    if (!team) {
      return
    }

    try {
      await removeTeamProject(team.id, nextProjectId)
      setProjectLinks((current) => current.filter((link) => link.projectId !== nextProjectId))
      toast.success('Project unlinked.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to unlink project.')
    }
  }

  async function handleRequestJoin() {
    if (!team) {
      return
    }

    setIsRequestingJoin(true)

    try {
      await requestTeamJoin(team.id)
      toast.success('Join request submitted.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to request team membership.')
    } finally {
      setIsRequestingJoin(false)
    }
  }

  async function handleDecideJoinRequest(requestId: string, decision: 'APPROVE' | 'REJECT') {
    if (!team) {
      return
    }

    setIsDecidingJoinRequestId(requestId)

    try {
      await decideTeamJoinRequest(team.id, requestId, decision)
      await reloadRelations()
      toast.success(decision === 'APPROVE' ? 'Join request approved.' : 'Join request rejected.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update join request.')
    } finally {
      setIsDecidingJoinRequestId(null)
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">Teams</h1>
        </div>
        <div className="flex min-w-0 flex-1 items-center justify-center overflow-auto p-6">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      </div>
    )
  }

  if (errorMessage || !team) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">Teams</h1>
        </div>
        <Empty className="min-w-0 flex-1 overflow-auto">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <UsersRound />
            </EmptyMedia>
            <EmptyTitle>{errorMessage || 'Team not found.'}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center justify-between gap-3 border-b px-4 py-3 md:px-6">
        <h1 className="flex min-w-0 items-center gap-2 text-xl font-semibold leading-none tracking-normal">
          <NavLink to={workspaceDestination('/app/teams')} className="shrink-0 text-muted-foreground hover:text-foreground">
            Teams
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="flex min-w-0 items-center gap-1.5">
            <UsersRound className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="truncate">{team.name}</span>
          </span>
        </h1>
        {!isCurrentUserMember && currentUser && currentUser.globalRole !== 'SUPERADMIN' ? (
          <Button type="button" variant="outline" className="gap-2" disabled={isRequestingJoin} onClick={() => void handleRequestJoin()}>
            {isRequestingJoin ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}
            Request access
          </Button>
        ) : null}
      </div>

      <div className="min-w-0 flex-1 overflow-auto p-4 md:p-6">
        <Tabs defaultValue="info" className="gap-4">
          <TabsList variant="line" className="border-b">
            <TabsTrigger value="info">Info</TabsTrigger>
            <TabsTrigger value="members">Members</TabsTrigger>
            <TabsTrigger value="projects">Projects</TabsTrigger>
            {canManageTeamLinks ? <TabsTrigger value="join-requests">Join requests</TabsTrigger> : null}
          </TabsList>

          <TabsContent value="info">
            <section className="max-w-3xl space-y-4 rounded-md border bg-background p-4">
              <div>
                <h2 className="text-sm font-semibold">Basic info</h2>
              </div>
              <form className="space-y-4" onSubmit={handleUpdateTeam}>
                <div className="grid gap-2 sm:grid-cols-[8rem_minmax(0,1fr)] sm:items-center">
                  <label className="text-sm font-medium">Name</label>
                  <Input value={editName} onChange={(event) => setEditName(event.target.value)} disabled={!isAdminLike} />
                </div>
                <div className="grid gap-2 sm:grid-cols-[8rem_minmax(0,1fr)] sm:items-start">
                  <label className="pt-2 text-sm font-medium">Description</label>
                  <Textarea
                    value={editDescription}
                    onChange={(event) => setEditDescription(event.target.value)}
                    disabled={!isAdminLike}
                    placeholder="What is this team responsible for?"
                    rows={4}
                  />
                </div>
                {isAdminLike ? (
                  <div className="flex justify-end">
                    <Button type="submit" className="gap-2" disabled={isUpdating || (editName.trim() === team.name && (editDescription.trim() || '') === (team.description?.trim() || ''))}>
                      {isUpdating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                      Save
                    </Button>
                  </div>
                ) : null}
              </form>

              {isAdminLike ? (
                <div className="flex border-t pt-4">
                  <DeleteConfirmPopover
                    title="Delete team?"
                    description="Team memberships and project links will be removed."
                    confirmLabel="Delete team"
                    disabled={isDeleting}
                    trigger={(
                      <Button type="button" variant="destructive" className="gap-2" disabled={isDeleting}>
                        <Trash2 className="h-4 w-4" />
                        {isDeleting ? 'Deleting...' : 'Delete'}
                      </Button>
                    )}
                    onConfirm={handleDeleteTeam}
                  />
                </div>
              ) : null}
            </section>
          </TabsContent>

          <TabsContent value="members">
            <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
              <div className="flex items-center justify-between gap-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <UserPlus className="h-4 w-4" />
                  Members
                </h2>
                <Badge variant="secondary">{members.length}</Badge>
              </div>
              <div className="space-y-3">
                {canManageTeamLinks ? (
                  <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAddMember}>
                    <NativeSelect className="w-full sm:w-72" value={memberUserId} onChange={(event) => setMemberUserId(event.target.value)} disabled={availableUsers.length === 0}>
                      <NativeSelectOption value="">Select user</NativeSelectOption>
                      {availableUsers.map((user) => (
                        <NativeSelectOption key={user.id} value={user.id}>
                          {displayUser(user)}
                        </NativeSelectOption>
                      ))}
                    </NativeSelect>
                    <NativeSelect className="w-full sm:w-40" value={memberRole} onChange={(event) => setMemberRole(event.target.value as TeamMember['role'])}>
                      {TEAM_ROLE_OPTIONS.map((role) => (
                        <NativeSelectOption key={role} value={role}>
                          {role}
                        </NativeSelectOption>
                      ))}
                    </NativeSelect>
                    <Button type="submit" className="gap-2" disabled={isAddingMember || !memberUserId}>
                      {isAddingMember ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                      Add
                    </Button>
                  </form>
                ) : null}

                <div className="grid gap-3 xl:grid-cols-2">
                  {([['TEAM_OWNER', 'Owners'], ['TEAM_MEMBER', 'Members']] as const).map(([role, label]) => {
                    const roleMembers = members.filter((member) => member.role === role)
                    return (
                      <div key={role} className="space-y-3 rounded-md border p-3">
                        <div className="flex items-center justify-between gap-2">
                          <h3 className="text-sm font-medium">{label}</h3>
                          <Badge variant="outline">{roleMembers.length}</Badge>
                        </div>
                        {roleMembers.length === 0 ? (
                          <div className="rounded-md border border-dashed px-3 py-5 text-sm text-muted-foreground">No {label.toLowerCase()} assigned.</div>
                        ) : (
                          <div className="space-y-2">
                            {roleMembers.map((member) => {
                              const user = userById.get(member.userId)
                              return (
                                <div key={member.userId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                                  <div className="min-w-0">
                                    <div className="truncate text-sm font-medium">{displayUser(user)}</div>
                                    {user?.email ? <div className="truncate text-xs text-muted-foreground">{user.email}</div> : null}
                                  </div>
                                  {canManageTeamLinks ? (
                                    <DeleteConfirmPopover
                                      title="Remove team member?"
                                      description="This user will lose access granted through this team."
                                      confirmLabel="Remove"
                                      trigger={(
                                        <Button type="button" variant="ghost" size="icon-sm" aria-label="Remove member">
                                          <X className="h-4 w-4" />
                                        </Button>
                                      )}
                                      onConfirm={() => handleRemoveMember(member.userId)}
                                    />
                                  ) : null}
                                </div>
                              )
                            })}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            </section>
          </TabsContent>

          <TabsContent value="projects">
            <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
              <div className="flex items-center justify-between gap-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <FolderOpen className="h-4 w-4" />
                  Projects
                </h2>
                <Badge variant="secondary">{projectLinks.length}</Badge>
              </div>
              <div className="space-y-3">
                {canManageTeamLinks ? (
                  <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAddProject}>
                    <NativeSelect className="w-full sm:w-80" value={projectId} onChange={(event) => setProjectId(event.target.value)} disabled={availableProjects.length === 0}>
                      <NativeSelectOption value="">Select project</NativeSelectOption>
                      {availableProjects.map((project) => (
                        <NativeSelectOption key={project.id} value={project.id}>
                          {displayProject(project)}
                        </NativeSelectOption>
                      ))}
                    </NativeSelect>
                    <NativeSelect className="w-full sm:w-36" value={projectRole} onChange={(event) => setProjectRole(event.target.value as ProjectTeam['role'])}>
                      {PROJECT_ROLE_OPTIONS.map((role) => (
                        <NativeSelectOption key={role} value={role}>
                          {role}
                        </NativeSelectOption>
                      ))}
                    </NativeSelect>
                    <Button type="submit" className="gap-2" disabled={isAddingProject || !projectId}>
                      {isAddingProject ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                      Add
                    </Button>
                  </form>
                ) : null}

                {projectLinks.length === 0 ? (
                  <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">No projects linked.</div>
                ) : (
                  <div className="space-y-2">
                    {projectLinks.map((link) => {
                      const project = projectById.get(link.projectId)
                      return (
                        <div key={link.projectId} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
                          <button type="button" className="min-w-0 text-left" onClick={() => navigate(workspaceDestination(`/app/projects/${link.projectId}`))}>
                            <div className="truncate text-sm font-medium">{displayProject(project)}</div>
                            <div className="truncate text-xs text-muted-foreground">{link.role}</div>
                          </button>
                          {canManageTeamLinks ? (
                            <DeleteConfirmPopover
                              title="Unlink project?"
                              description="This team will lose the project access granted by this link."
                              confirmLabel="Unlink"
                              trigger={(
                                <Button type="button" variant="ghost" size="icon-sm" aria-label="Unlink project">
                              <X className="h-4 w-4" />
                                </Button>
                              )}
                              onConfirm={() => handleRemoveProject(link.projectId)}
                            />
                          ) : null}
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            </section>
          </TabsContent>

          {canManageTeamLinks ? (
            <TabsContent value="join-requests">
              <section className="max-w-4xl space-y-4 rounded-md border bg-background p-4">
                <div className="flex items-center justify-between gap-3">
                  <h2 className="flex items-center gap-2 text-sm font-semibold">
                    <Check className="h-4 w-4" />
                    Join requests
                  </h2>
                  <Badge variant="secondary">{joinRequests.length}</Badge>
                </div>
                <div className="space-y-2">
                  {joinRequests.length === 0 ? (
                    <div className="rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground">No pending requests.</div>
                  ) : (
                    joinRequests.map((joinRequest) => {
                      const user = userById.get(joinRequest.userId)
                      return (
                        <div key={joinRequest.id} className="space-y-2 rounded-md border px-3 py-2">
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium">{displayUser(user)}</div>
                            {user?.email ? <div className="truncate text-xs text-muted-foreground">{user.email}</div> : null}
                          </div>
                          <div className="flex gap-1">
                            <Button type="button" size="sm" className="gap-1" disabled={isDecidingJoinRequestId === joinRequest.id} onClick={() => void handleDecideJoinRequest(joinRequest.id, 'APPROVE')}>
                              Approve
                            </Button>
                            <Button type="button" variant="outline" size="sm" disabled={isDecidingJoinRequestId === joinRequest.id} onClick={() => void handleDecideJoinRequest(joinRequest.id, 'REJECT')}>
                              Reject
                            </Button>
                          </div>
                        </div>
                      )
                    })
                  )}
                </div>
              </section>
            </TabsContent>
          ) : null}
        </Tabs>
      </div>
    </div>
  )
}
