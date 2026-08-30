import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useOutletContext, useSearchParams } from 'react-router'
import { AlertTriangle, Loader2, Plus, Search, X } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ChatPanel, { type ChatWorkItemReference } from '@/ChatPanel'
import { addChatSessionContext, deleteChatSessionContext, getLlmStatus, listChatSessionContext, listNodes, listProjects, listTeams, loadSelectableUsers, type ChatSessionContext, type Project, type ProjectNode, type Team, type User } from '@/lib/api'
import type { AskPageOutletContext } from './App'

const maxSelectedProjects = 10
type ContextType = 'projects' | 'teams' | 'users'

type AskPageProps = {
  projectId?: string
  onGraphChangeProposalSaved?: () => void | Promise<void>
}

function projectTitle(project: Project) {
  return project.title?.trim() || project.name?.trim() || 'Untitled project'
}

function userTitle(user: User) {
  return user.displayName?.trim() || user.username
}

function fieldValue(node: ProjectNode, name: string) {
  return node.fields.find((field) => field.name === name)?.value
}

function referencesForNodes(nodes: ProjectNode[]) {
  return new Map(nodes.map((node) => [node.id, {
    id: node.id,
    title: node.title,
    type: node.type,
    status: String(fieldValue(node, 'status') ?? 'OPEN'),
    dueDate: String(fieldValue(node, 'dueDate') ?? '').trim() || null,
    projectId: node.projectId,
  } satisfies ChatWorkItemReference]))
}

export default function AskPage({ projectId: routeProjectId, onGraphChangeProposalSaved }: AskPageProps = {}) {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const {
    chatSessions,
    selectedSessionId,
    newChatRequestKey,
    isLoadingSessions,
    refreshChatSessions,
    createChatSession,
    onStreamingChange,
  } = useOutletContext<AskPageOutletContext>()
  const hasLoadedSessionsRef = useRef(!isLoadingSessions)
  if (!isLoadingSessions) {
    hasLoadedSessionsRef.current = true
  }
  const [projects, setProjects] = useState<Project[]>([])
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [selectedProjectIds, setSelectedProjectIds] = useState<string[]>([])
  const [contextType, setContextType] = useState<ContextType>('projects')
  const [contextQuery, setContextQuery] = useState('')
  const [references, setReferences] = useState<Map<string, ChatWorkItemReference>>(new Map())
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingReferences, setIsLoadingReferences] = useState(false)
  const [isLlmAvailable, setIsLlmAvailable] = useState(false)
  const [sessionContexts, setSessionContexts] = useState<ChatSessionContext[]>([])

  const selectedProjects = useMemo(
    () => selectedProjectIds
      .map((projectId) => projects.find((project) => project.id === projectId))
      .filter((project): project is Project => Boolean(project)),
    [projects, selectedProjectIds],
  )
  const filteredProjects = useMemo(() => {
    const query = contextQuery.trim().toLowerCase()
    if (!query) {
      return projects
    }
    return projects.filter((project) => projectTitle(project).toLowerCase().includes(query))
  }, [contextQuery, projects])
  const filteredTeams = useMemo(() => {
    const query = contextQuery.trim().toLowerCase()
    if (!query) {
      return teams
    }
    return teams.filter((team) => [team.name, team.description ?? '', team.id].some((value) => value.toLowerCase().includes(query)))
  }, [contextQuery, teams])
  const filteredUsers = useMemo(() => {
    const query = contextQuery.trim().toLowerCase()
    if (!query) {
      return users
    }
    return users.filter((user) => [userTitle(user), user.username, user.title ?? '', user.id].some((value) => value.toLowerCase().includes(query)))
  }, [contextQuery, users])
  const selectedTeamIds = useMemo(
    () => new Set(sessionContexts.filter((context) => context.entityType === 'TEAM').map((context) => context.entityId)),
    [sessionContexts],
  )
  const selectedUserIds = useMemo(
    () => new Set(sessionContexts.filter((context) => context.entityType === 'USER').map((context) => context.entityId)),
    [sessionContexts],
  )
  const activeChatProjectId = routeProjectId || selectedProjectIds[0] || ''
  const requestedSessionId = searchParams.get('chatSessionId')
  const initialPrompt = searchParams.get('prompt') ?? ''
  const autoSubmitInitialDraft = searchParams.get('autoSend') === '1'

  const selectedSession = useMemo(
    () => chatSessions.find((session) => session.id === (requestedSessionId ?? selectedSessionId)),
    [chatSessions, requestedSessionId, selectedSessionId],
  )

  useEffect(() => {
    let isMounted = true
    if (!selectedSession?.id) {
      setSessionContexts([])
      return
    }
    listChatSessionContext(selectedSession.id)
      .then((contexts) => {
        if (isMounted) {
          setSessionContexts(contexts)
          setSelectedProjectIds(contexts.filter((context) => context.entityType === 'PROJECT').map((context) => context.entityId))
        }
      })
      .catch((error) => {
        if (isMounted) toast.error(error instanceof Error ? error.message : 'Failed to load chat context.')
      })
    return () => { isMounted = false }
  }, [selectedSession?.id])

  useEffect(() => {
    if (newChatRequestKey === 0) {
      return
    }

    let isMounted = true
    queueMicrotask(() => {
      if (isMounted) {
        setSelectedProjectIds([])
        setContextQuery('')
        setContextType('projects')
      }
    })
    return () => {
      isMounted = false
    }
  }, [newChatRequestKey])

  useEffect(() => {
    let isMounted = true

    async function loadPage() {
      setIsLoading(true)
      try {
        const [nextProjects, nextTeams, nextUsers, llmStatus] = await Promise.all([
          listProjects(),
          listTeams(),
          loadSelectableUsers(),
          getLlmStatus().catch(() => ({ provider: 'none', available: false })),
        ])
        if (!isMounted) {
          return
        }
        setProjects(nextProjects)
        setTeams(nextTeams)
        setUsers(nextUsers)
        setIsLlmAvailable(llmStatus.available)
          const defaultProjectId = routeProjectId && nextProjects.some((project) => project.id === routeProjectId)
            ? routeProjectId
            : ''
          setSelectedProjectIds((current) => {
            if (newChatRequestKey > 0) {
              return []
            }
            if (defaultProjectId && current.length === 0) {
              return [defaultProjectId]
            }
            const stillAvailable = current.filter((projectId) => nextProjects.some((project) => project.id === projectId))
            return stillAvailable
        })
      } catch (error) {
        if (isMounted) {
          toast.error(error instanceof Error ? error.message : 'Failed to load Ask.')
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadPage()
    return () => {
      isMounted = false
    }
  }, [newChatRequestKey, routeProjectId])

  useEffect(() => {
    if (selectedProjectIds.length === 0) {
      return
    }

    let isMounted = true
    Promise.all(selectedProjectIds.map((projectId) => listNodes(projectId)))
      .then((nodeLists) => {
        if (isMounted) {
          setReferences(referencesForNodes(nodeLists.flat()))
        }
      })
      .catch((error) => {
        if (isMounted) {
          setReferences(new Map())
          toast.error(error instanceof Error ? error.message : 'Failed to load project context.')
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoadingReferences(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [selectedProjectIds])

  function toggleProject(projectId: string) {
    setSelectedProjectIds((current) => {
      if (current.includes(projectId)) {
        return current.filter((currentProjectId) => currentProjectId !== projectId)
      }
      if (current.length >= maxSelectedProjects) {
        toast.error(`You can add up to ${maxSelectedProjects} projects as context.`)
        return current
      }
      return [...current, projectId]
    })
    if (selectedSession?.id) {
      const existing = sessionContexts.find((context) => context.entityType === 'PROJECT' && context.entityId === projectId)
      if (existing) {
        void deleteChatSessionContext(selectedSession.id, existing.id).catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to update context.'))
        setSessionContexts((current) => current.filter((context) => context.id !== existing.id))
      } else {
        void addChatSessionContext(selectedSession.id, 'PROJECT', projectId)
          .then((context) => setSessionContexts((current) => [...current, context]))
          .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to update context.'))
      }
    }
  }

  async function toggleTeam(teamId: string) {
    const existing = selectedSession && sessionContexts.find((context) => context.entityType === 'TEAM' && context.entityId === teamId)
    if (existing && selectedSession) {
      void deleteChatSessionContext(selectedSession.id, existing.id)
        .then(() => setSessionContexts((current) => current.filter((context) => context.id !== existing.id)))
        .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to remove team context.'))
      return
    }

    let sessionId = selectedSession?.id
    if (!sessionId) {
      const session = await createChatSession()
      sessionId = session?.id
    }
    if (!sessionId) {
      return
    }

    void addChatSessionContext(sessionId, 'TEAM', teamId)
      .then((context) => setSessionContexts((current) => [...current.filter((item) => item.id !== context.id), context]))
      .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to add team context.'))
  }

  async function toggleUser(userId: string) {
    const existing = selectedSession && sessionContexts.find((context) => context.entityType === 'USER' && context.entityId === userId)
    if (existing && selectedSession) {
      void deleteChatSessionContext(selectedSession.id, existing.id)
        .then(() => setSessionContexts((current) => current.filter((context) => context.id !== existing.id)))
        .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to remove user context.'))
      return
    }

    let sessionId = selectedSession?.id
    if (!sessionId) {
      const session = await createChatSession()
      sessionId = session?.id
    }
    if (!sessionId) {
      return
    }

    void addChatSessionContext(sessionId, 'USER', userId)
      .then((context) => setSessionContexts((current) => [...current.filter((item) => item.id !== context.id), context]))
      .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to add user context.'))
  }

  function removeProject(projectId: string) {
    setSelectedProjectIds((current) => current.filter((currentProjectId) => currentProjectId !== projectId))
    const existing = selectedSession && sessionContexts.find((context) => context.entityType === 'PROJECT' && context.entityId === projectId)
    if (existing && selectedSession) {
      void deleteChatSessionContext(selectedSession.id, existing.id)
        .then(() => setSessionContexts((current) => current.filter((context) => context.id !== existing.id)))
        .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to remove context.'))
    }
  }

  function removeContext(context: ChatSessionContext) {
    if (!selectedSession) {
      return
    }
    void deleteChatSessionContext(selectedSession.id, context.id)
      .then(() => {
        setSessionContexts((current) => current.filter((item) => item.id !== context.id))
        if (context.entityType === 'PROJECT') {
          setSelectedProjectIds((current) => current.filter((projectId) => projectId !== context.entityId))
        }
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to remove context.'))
  }

  function clearContexts() {
    if (!selectedSession) {
      setSelectedProjectIds([])
      return
    }
    const contexts = [...sessionContexts]
    contexts.forEach((context) => {
      void deleteChatSessionContext(selectedSession.id, context.id).catch((error) => toast.error(error instanceof Error ? error.message : 'Failed to clear context.'))
    })
    setSelectedProjectIds([])
    setSessionContexts([])
  }

  const visibleReferences = useMemo(() => {
    if (selectedProjectIds.length === 0) {
      return references
    }
    return new Map(
      [...references].filter(([, reference]) => !reference.projectId || selectedProjectIds.includes(reference.projectId)),
    )
  }, [references, selectedProjectIds])
  const sessionsLoading = isLoadingSessions
  const referencesLoading = selectedProjectIds.length > 0 && isLoadingReferences
  const hasContext = selectedProjects.length > 0 || sessionContexts.length > 0

  function showSessionError(error: unknown) {
    toast.error(error instanceof Error ? error.message : 'Failed to refresh chat sessions.')
  }

  const projectContext = (
    <div className="flex min-h-7 flex-wrap items-center gap-2 text-xs">
      {projects.length > 0 || teams.length > 0 || users.length > 0 ? (
        <Popover onOpenChange={(open) => { if (!open) setContextQuery('') }}>
          <PopoverTrigger
            render={(
              <Button type="button" size={hasContext ? 'icon-xs' : 'xs'} variant="ghost" className="gap-1.5 text-muted-foreground" aria-label="Add context" title="Add context">
                <Plus className="h-3.5 w-3.5" />
                <span className={hasContext ? 'sr-only' : undefined}>Add context</span>
              </Button>
            )}
          />
          <PopoverContent align="start" className="w-[22rem] gap-0 p-0">
            <PopoverHeader className="border-b px-4 py-3">
              <PopoverTitle>Add context</PopoverTitle>
            </PopoverHeader>
            <Tabs value={contextType} onValueChange={(value) => { setContextType(value as ContextType); setContextQuery('') }}>
              <TabsList variant="line" className="w-full rounded-none border-b px-3">
                <TabsTrigger value="projects">Projects</TabsTrigger>
                <TabsTrigger value="teams">Teams</TabsTrigger>
                <TabsTrigger value="users">People</TabsTrigger>
              </TabsList>
              <div className="border-b p-3">
                <div className="relative">
                  <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={contextQuery}
                    onChange={(event) => setContextQuery(event.target.value)}
                    placeholder="Search"
                    className="pl-9"
                    autoFocus
                  />
                </div>
              </div>
              <TabsContent value="projects" className="mt-0">
                <div className="max-h-72 overflow-y-auto p-2">
                  {filteredProjects.length === 0 ? (
                    <div className="py-6 text-center text-sm text-muted-foreground">No matching projects</div>
                  ) : filteredProjects.map((project) => {
                    const isSelected = selectedProjectIds.includes(project.id)
                    return (
                      <div key={project.id} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-muted">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => toggleProject(project.id)}
                          aria-label={`Use ${projectTitle(project)} as context`}
                        />
                        <button
                          type="button"
                          className="min-w-0 flex-1 truncate text-left text-sm"
                          onClick={() => toggleProject(project.id)}
                        >
                          {projectTitle(project)}
                        </button>
                      </div>
                    )
                  })}
                </div>
              </TabsContent>
              <TabsContent value="teams" className="mt-0">
                <div className="max-h-72 overflow-y-auto p-2">
                  {filteredTeams.length === 0 ? (
                    <div className="py-6 text-center text-sm text-muted-foreground">No matching teams</div>
                  ) : filteredTeams.map((team) => {
                    const isSelected = selectedTeamIds.has(team.id)
                    return (
                      <div key={team.id} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-muted">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => void toggleTeam(team.id)}
                          aria-label={`Use ${team.name} as context`}
                        />
                        <button
                          type="button"
                          className="min-w-0 flex-1 truncate text-left text-sm"
                          onClick={() => void toggleTeam(team.id)}
                        >
                          {team.name}
                        </button>
                      </div>
                    )
                  })}
                </div>
              </TabsContent>
              <TabsContent value="users" className="mt-0">
                <div className="max-h-72 overflow-y-auto p-2">
                  {filteredUsers.length === 0 ? (
                    <div className="py-6 text-center text-sm text-muted-foreground">No matching people</div>
                  ) : filteredUsers.map((user) => {
                    const isSelected = selectedUserIds.has(user.id)
                    return (
                      <div key={user.id} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-muted">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => void toggleUser(user.id)}
                          aria-label={`Use ${userTitle(user)} as context`}
                        />
                        <button
                          type="button"
                          className="min-w-0 flex-1 truncate text-left text-sm"
                          onClick={() => void toggleUser(user.id)}
                        >
                          <span className="block truncate">{userTitle(user)}</span>
                          {user.title ? <span className="block truncate text-xs text-muted-foreground">{user.title}</span> : null}
                        </button>
                      </div>
                    )
                  })}
                </div>
              </TabsContent>
            </Tabs>
            <div className="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground">
              <span>{contextType === 'projects' ? `${selectedProjects.length} selected` : contextType === 'teams' ? `${selectedTeamIds.size} selected` : `${selectedUserIds.size} selected`}</span>
              <Button type="button" size="xs" variant="ghost" onClick={clearContexts} disabled={!hasContext}>Clear all</Button>
            </div>
          </PopoverContent>
        </Popover>
      ) : null}
      {selectedProjects.map((project) => (
        <Badge key={project.id} variant="outline" className="h-7 max-w-full gap-2 px-2.5 pr-1 text-xs font-normal">
          <span className="h-2 w-2 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
          <span className="shrink-0 text-muted-foreground">Project:</span>
          <span className="max-w-56 truncate">{projectTitle(project)}</span>
          <button type="button" className="rounded-sm p-1 hover:bg-muted" onClick={() => removeProject(project.id)} aria-label={`Remove ${projectTitle(project)} from context`}>
            <X className="h-3 w-3" />
          </button>
        </Badge>
      ))}
      {sessionContexts.filter((context) => context.entityType !== 'PROJECT').map((context) => (
        <Badge key={context.id} variant="outline" className="h-7 max-w-full gap-2 px-2.5 pr-1 text-xs font-normal">
          <span className="h-2 w-2 shrink-0 rounded-full bg-violet-500" aria-hidden="true" />
          <span className="shrink-0 text-muted-foreground">{context.entityType.replace('_', ' ').toLowerCase()}:</span>
          <span className="max-w-56 truncate">{context.label}</span>
          <button type="button" className="rounded-sm p-1 hover:bg-muted" onClick={() => removeContext(context)} aria-label={`Remove ${context.label} from context`}>
            <X className="h-3 w-3" />
          </button>
        </Badge>
      ))}
      {hasContext ? <Button type="button" size="xs" variant="ghost" className="text-muted-foreground" onClick={clearContexts}>Clear all</Button> : null}
      {referencesLoading ? <span className="text-muted-foreground">Loading context…</span> : null}
    </div>
  )

  const chatContent = sessionsLoading && !hasLoadedSessionsRef.current ? (
    <div className="flex h-full min-h-0 items-center justify-center bg-background text-muted-foreground">
      <Loader2 className="h-5 w-5 animate-spin" />
    </div>
  ) : !isLlmAvailable ? (
    <div className="flex h-full min-h-0 items-center justify-center bg-background p-6">
      <Empty className="border-0">
        <EmptyHeader>
          <EmptyMedia variant="icon"><AlertTriangle /></EmptyMedia>
          <EmptyTitle>AI is unavailable</EmptyTitle>
          <EmptyDescription>Configure an AI provider to start asking questions and generating artifacts.</EmptyDescription>
        </EmptyHeader>
      </Empty>
    </div>
  ) : (
    <ChatPanel
      projectId={activeChatProjectId}
      projectIds={selectedProjectIds}
      sessionId={selectedSession?.id}
      initialDraft={initialPrompt}
      autoSubmitInitialDraft={autoSubmitInitialDraft}
      onInitialDraftSubmitted={() => {
        const nextParams = new URLSearchParams(searchParams)
        nextParams.delete('prompt')
        nextParams.delete('autoSend')
        const query = nextParams.toString()
        navigate(`${location.pathname}${query ? `?${query}` : ''}${location.hash}`, { replace: true })
      }}
      onCreateSession={createChatSession}
      onSessionActivity={() => refreshChatSessions().catch(showSessionError)}
      onStreamingChange={onStreamingChange}
      onGraphChangeProposalSaved={onGraphChangeProposalSaved}
      showHeader={false}
      allowEmptyProject
      composerFooter={projectContext}
      projectReferences={new Map(projects.map((project) => [project.id, projectTitle(project)] as const))}
      workItemReferences={visibleReferences}
      teamReferences={new Map(sessionContexts.filter((context) => context.entityType === 'TEAM').map((context) => [context.entityId, context.label]))}
      userReferences={new Map([
        ...users.map((user) => [user.id, userTitle(user)] as const),
        ...sessionContexts.filter((context) => context.entityType === 'USER').map((context) => [context.entityId, context.label] as const),
      ])}
      onWorkItemReferenceClick={async (workItemId) => {
        const projectId = visibleReferences.get(workItemId)?.projectId ?? activeChatProjectId
        const nextParams = new URLSearchParams({
          chatPanel: 'open',
          workItemId,
        })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
        }
        if (sessionId && projectId) {
          try {
            await Promise.all([
              addChatSessionContext(sessionId, 'PROJECT', projectId),
              addChatSessionContext(sessionId, 'WORK_ITEM', workItemId),
            ])
          } catch (error) {
            toast.error(error instanceof Error ? error.message : 'Failed to add work item context.')
          }
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onProjectReferenceClick={async (projectId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open' })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
        }
        if (sessionId) {
          try {
            await addChatSessionContext(sessionId, 'PROJECT', projectId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : 'Failed to add project context.')
          }
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onTeamReferenceClick={async (teamId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', chatSessionId: selectedSession?.id ?? requestedSessionId ?? '' })
        if (!nextParams.get('chatSessionId')) nextParams.delete('chatSessionId')
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          try {
            await addChatSessionContext(sessionId, 'TEAM', teamId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : 'Failed to add team context.')
          }
        }
        navigate(`/app/teams/${teamId}?${nextParams.toString()}`)
      }}
      onUserReferenceClick={async (userId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', userId })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
        }
        if (sessionId) {
          try {
            await addChatSessionContext(sessionId, 'USER', userId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : 'Failed to add user context.')
          }
        }
        navigate(`/app/users?${nextParams.toString()}`)
      }}
      flush
      className="h-full min-h-0"
    />
  )

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Ask AI</h1>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {isLoading ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-background">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          chatContent
        )}
      </div>
    </div>
  )
}
