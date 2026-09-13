import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useOutletContext, useSearchParams } from 'react-router'
import { AlertTriangle, Check, ChevronDown, Loader2, Maximize2, Search, X } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import ChatPanel, { type ChatClarificationChoice } from '@/ChatPanel'
import type { ChatWorkItemReference } from '@/WorkItemResultCards'
import { addChatSessionContext, CHAT_CONTEXT_ENTITY_TYPES, clearChatSessionProjectFocus, deleteChatSessionContext, getLlmStatus, getWorkspace, listChatSessionContext, listProjects, listTeams, loadSelectableUsers, setChatSessionProjectFocus, type ChatSessionContext, type GraphChangeProposal, type Project, type Team, type User, type Workspace } from '@/lib/api'
import type { AiAgentPageOutletContext } from './App'

const BLOCKED_BY_RELATIONSHIP_TYPE = 'BLOCKED_BY'

type AiAgentPageProps = {
  projectId?: string
  onGraphChangeProposalSaved?: () => void | Promise<void>
  workspaceProposalRefreshKey?: number
  showWelcome?: boolean
  showStandaloneAction?: boolean
}

function projectTitle(project: Project, fallback: string) {
  return project.title?.trim() || project.name?.trim() || fallback
}

function userTitle(user: User) {
  return user.displayName?.trim() || user.username
}

function referencesForWorkspaces(workspaces: Workspace[]) {
  return new Map(workspaces.flatMap((workspace) => {
    const blockedWorkItemIds = new Set(workspace.relationships
      .filter((relationship) => relationship.type === BLOCKED_BY_RELATIONSHIP_TYPE && relationship.fromEntityType === CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM)
      .map((relationship) => relationship.fromEntityId))
    return workspace.workItems.map(({ workItem, assignees }) => [workItem.id, {
      id: workItem.id,
      title: workItem.title,
      type: workItem.type,
      status: workItem.status,
      priority: workItem.priority,
      dueDate: workItem.dueDate,
      projectId: workItem.projectId,
      assignees,
      blocked: workItem.status === 'BLOCKED' || blockedWorkItemIds.has(workItem.id),
    } satisfies ChatWorkItemReference] as const)
  }))
}

function proposalWorkItemId(proposal: GraphChangeProposal) {
  for (const change of proposal.changes) {
    if (change.entityType === 'NODE') return change.targetId
    if (change.entityType === 'ENTRY') {
      const workItemId = change.entry?.workItemId ?? change.previousEntry?.workItemId
      if (workItemId) return workItemId
    }
    const relationship = change.relationship ?? change.previousRelationship
    if (relationship?.fromEntityType === CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM) return relationship.fromEntityId
    if (relationship?.toEntityType === CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM) return relationship.toEntityId
  }
  return null
}

export default function AiAgentPage({ projectId: routeProjectId, onGraphChangeProposalSaved, workspaceProposalRefreshKey = 0, showWelcome = false, showStandaloneAction = false }: AiAgentPageProps = {}) {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const {
    displayName,
    chatSessions,
    selectedSessionId,
    newChatRequestKey,
    isLoadingSessions,
    refreshChatSessions,
    createChatSession,
    openStandaloneAiAgent,
    onStreamingChange,
  } = useOutletContext<AiAgentPageOutletContext>()
  const [projects, setProjects] = useState<Project[]>([])
  const [teams, setTeams] = useState<Team[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [focusedProjectId, setFocusedProjectId] = useState('')
  const [contextQuery, setContextQuery] = useState('')
  const [isProjectSwitcherOpen, setIsProjectSwitcherOpen] = useState(false)
  const [references, setReferences] = useState<Map<string, ChatWorkItemReference>>(new Map())
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingReferences, setIsLoadingReferences] = useState(false)
  const [isLlmAvailable, setIsLlmAvailable] = useState(false)
  const [sessionContexts, setSessionContexts] = useState<ChatSessionContext[]>([])

  const focusedProject = useMemo(
    () => projects.find((project) => project.id === focusedProjectId),
    [focusedProjectId, projects],
  )
  const filteredProjects = useMemo(() => {
    const query = contextQuery.trim().toLowerCase()
    if (!query) {
      return projects
    }
    return projects.filter((project) => projectTitle(project, t('common.untitledProject')).toLowerCase().includes(query))
  }, [contextQuery, projects, t])
  const activeChatProjectId = focusedProjectId
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
      queueMicrotask(() => {
        if (isMounted) {
          setSessionContexts([])
        }
      })
      return () => { isMounted = false }
    }
    listChatSessionContext(selectedSession.id)
      .then((contexts) => {
        if (isMounted) {
          setSessionContexts(contexts)
          setFocusedProjectId(contexts.filter((context) => context.entityType === CHAT_CONTEXT_ENTITY_TYPES.PROJECT).at(-1)?.entityId ?? '')
        }
      })
      .catch((error) => {
        if (isMounted) toast.error(error instanceof Error ? error.message : t('aiAgent.failedLoadContext'))
      })
    return () => { isMounted = false }
  }, [selectedSession?.id, t])

  useEffect(() => {
    if (newChatRequestKey === 0) {
      return
    }

    let isMounted = true
    queueMicrotask(() => {
      if (isMounted) {
        setFocusedProjectId('')
        setContextQuery('')
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
          setFocusedProjectId((current) => {
            if (newChatRequestKey > 0) {
              return ''
            }
            if (defaultProjectId && !current) {
              return defaultProjectId
            }
            return nextProjects.some((project) => project.id === current) ? current : ''
        })
      } catch (error) {
        if (isMounted) {
          toast.error(error instanceof Error ? error.message : t('aiAgent.failedLoadAiAgent'))
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
  }, [newChatRequestKey, routeProjectId, t])

  useEffect(() => {
    if (!focusedProjectId) {
      return
    }

    let isMounted = true
    queueMicrotask(() => {
      if (isMounted) setIsLoadingReferences(true)
    })
    getWorkspace(focusedProjectId)
      .then((workspace) => {
        if (isMounted) {
          setReferences(referencesForWorkspaces([workspace]))
        }
      })
      .catch((error) => {
        if (isMounted) {
          setReferences(new Map())
          toast.error(error instanceof Error ? error.message : t('aiAgent.failedLoadProjectContext'))
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
  }, [focusedProjectId, t])

  async function refreshSessionContexts(sessionId: string) {
    const contexts = await listChatSessionContext(sessionId)
    setSessionContexts(contexts)
    setFocusedProjectId(contexts.filter((context) => context.entityType === CHAT_CONTEXT_ENTITY_TYPES.PROJECT).at(-1)?.entityId ?? '')
  }

  async function focusProject(projectId: string) {
    let sessionId = selectedSession?.id ?? requestedSessionId
    if (!sessionId) {
      const session = await createChatSession()
      sessionId = session?.id ?? null
    }
    if (!sessionId) return

    await setChatSessionProjectFocus(sessionId, projectId)
    await refreshSessionContexts(sessionId)
    setContextQuery('')
    setIsProjectSwitcherOpen(false)
  }

  async function focusWorkItem(sessionId: string, projectId: string, workItemId: string) {
    await setChatSessionProjectFocus(sessionId, projectId)
    await addChatSessionContext(sessionId, CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM, workItemId)
    await refreshSessionContexts(sessionId)
  }

  async function clearProjectFocus() {
    const sessionId = selectedSession?.id ?? requestedSessionId
    if (!sessionId) {
      setFocusedProjectId('')
      return
    }
    try {
      await clearChatSessionProjectFocus(sessionId)
      setFocusedProjectId('')
      setSessionContexts((current) => current.filter((context) => context.entityType !== CHAT_CONTEXT_ENTITY_TYPES.PROJECT && context.entityType !== CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('aiAgent.failedRemoveContext'))
    }
  }

  function removeContext(context: ChatSessionContext) {
    if (!selectedSession) {
      return
    }
    void deleteChatSessionContext(selectedSession.id, context.id)
      .then(() => {
        setSessionContexts((current) => current.filter((item) => item.id !== context.id))
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : t('aiAgent.failedRemoveContext')))
  }

  const visibleReferences = useMemo(() => {
    if (!focusedProjectId) return new Map<string, ChatWorkItemReference>()
    return new Map([...references].filter(([, reference]) => !reference.projectId || reference.projectId === focusedProjectId))
  }, [focusedProjectId, references])
  const sessionsLoading = isLoadingSessions
  const referencesLoading = Boolean(focusedProjectId) && isLoadingReferences

  function showSessionError(error: unknown) {
    toast.error(error instanceof Error ? error.message : t('aiAgent.failedRefreshSessions'))
  }

  async function selectClarificationChoice(choice: ChatClarificationChoice) {
    const sessionId = selectedSession?.id ?? requestedSessionId
    if (!sessionId) throw new Error(t('chat.startBeforeSending'))

    const entityType = ({
      project: CHAT_CONTEXT_ENTITY_TYPES.PROJECT,
      workitem: CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM,
      team: CHAT_CONTEXT_ENTITY_TYPES.TEAM,
      user: CHAT_CONTEXT_ENTITY_TYPES.USER,
    } as const)[choice.entityType]
    if (entityType === CHAT_CONTEXT_ENTITY_TYPES.PROJECT) {
      await focusProject(choice.entityId)
      return { projectId: choice.entityId }
    }
    const existing = sessionContexts.find((context) => context.entityType === entityType && context.entityId === choice.entityId)
    if (existing && entityType === CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM && existing.projectId && existing.projectId !== focusedProjectId) {
      await setChatSessionProjectFocus(sessionId, existing.projectId)
      await refreshSessionContexts(sessionId)
      return { projectId: existing.projectId }
    }
    if (!existing) {
      const context = await addChatSessionContext(sessionId, entityType, choice.entityId)
      if (entityType === CHAT_CONTEXT_ENTITY_TYPES.WORK_ITEM && context.projectId) {
        await setChatSessionProjectFocus(sessionId, context.projectId)
        await refreshSessionContexts(sessionId)
        return { projectId: context.projectId }
      } else {
        setSessionContexts((current) => current.some((item) => item.entityType === context.entityType && item.entityId === context.entityId)
          ? current
          : [...current, context])
      }
    }
    return undefined
  }

  const projectContext = (
    <div className="flex min-h-7 flex-wrap items-center gap-2 text-xs">
      {focusedProject ? (
        <Badge variant="outline" className="h-7 max-w-full gap-0 p-0 text-xs font-normal">
          <span className="ml-2 h-2 w-2 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
          <Popover open={isProjectSwitcherOpen} onOpenChange={(open) => { setIsProjectSwitcherOpen(open); if (!open) setContextQuery('') }}>
            <PopoverTrigger render={(
              <button type="button" className="flex min-w-0 items-center gap-1.5 px-2 py-1" aria-label={t('aiAgent.changeProject')}>
                <span className="shrink-0 text-muted-foreground">{t('aiAgent.workingIn')}</span>
                <span className="max-w-56 truncate">{projectTitle(focusedProject, t('common.untitledProject'))}</span>
                <ChevronDown className="h-3 w-3 shrink-0 text-muted-foreground" />
              </button>
            )} />
            <PopoverContent align="start" className="w-[22rem] gap-0 p-0">
              <PopoverHeader className="border-b px-4 py-3">
                <PopoverTitle>{t('aiAgent.changeProject')}</PopoverTitle>
              </PopoverHeader>
              <div className="border-b p-3">
                <div className="relative">
                  <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input value={contextQuery} onChange={(event) => setContextQuery(event.target.value)} placeholder={t('common.search')} className="pl-9" autoFocus />
                </div>
              </div>
              <div className="max-h-72 overflow-y-auto p-2">
                {filteredProjects.length === 0 ? (
                  <div className="py-6 text-center text-sm text-muted-foreground">{t('aiAgent.noMatchingProjects')}</div>
                ) : filteredProjects.map((project) => (
                  <button
                    key={project.id}
                    type="button"
                    className="flex w-full items-center gap-3 rounded-md px-2 py-2 text-left text-sm hover:bg-muted"
                    onClick={() => void focusProject(project.id).catch((error) => toast.error(error instanceof Error ? error.message : t('aiAgent.failedUpdateContext')))}
                  >
                    <Check className={project.id === focusedProjectId ? 'h-4 w-4 opacity-100' : 'h-4 w-4 opacity-0'} />
                    <span className="min-w-0 flex-1 truncate">{projectTitle(project, t('common.untitledProject'))}</span>
                  </button>
                ))}
              </div>
            </PopoverContent>
          </Popover>
          <button type="button" className="mr-1 rounded-sm p-1 hover:bg-muted" onClick={() => void clearProjectFocus()} aria-label={t('aiAgent.clearProjectFocus')}>
            <X className="h-3 w-3" />
          </button>
        </Badge>
      ) : null}
      {sessionContexts.filter((context) => context.entityType !== CHAT_CONTEXT_ENTITY_TYPES.PROJECT).map((context) => (
        <Badge key={context.id} variant="outline" className="h-7 max-w-full gap-2 px-2.5 pr-1 text-xs font-normal">
          <span className="h-2 w-2 shrink-0 rounded-full bg-violet-500" aria-hidden="true" />
          <span className="shrink-0 text-muted-foreground">{context.entityType === CHAT_CONTEXT_ENTITY_TYPES.TEAM ? `${t('common.team')}:` : context.entityType === CHAT_CONTEXT_ENTITY_TYPES.USER ? `${t('common.user')}:` : `${t('common.workItem')}:`}</span>
          <span className="max-w-56 truncate">{context.label}</span>
          <button type="button" className="rounded-sm p-1 hover:bg-muted" onClick={() => removeContext(context)} aria-label={t('aiAgent.removeFromContext', { label: context.label })}>
            <X className="h-3 w-3" />
          </button>
        </Badge>
      ))}
      {referencesLoading ? <span className="text-muted-foreground">{t('aiAgent.loadingContext')}</span> : null}
    </div>
  )

  const chatContent = sessionsLoading && chatSessions.length === 0 ? (
    <div className="flex h-full min-h-0 items-center justify-center bg-background text-muted-foreground">
      <Loader2 className="h-5 w-5 animate-spin" />
    </div>
  ) : !isLlmAvailable ? (
    <div className="flex h-full min-h-0 items-center justify-center bg-background p-6">
      <Empty className="border-0">
        <EmptyHeader>
          <EmptyMedia variant="icon"><AlertTriangle /></EmptyMedia>
          <EmptyTitle>{t('aiAgent.aiUnavailable')}</EmptyTitle>
          <EmptyDescription>{t('aiAgent.aiUnavailableDescription')}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    </div>
  ) : (
    <ChatPanel
      projectId={activeChatProjectId}
      projectIds={focusedProjectId ? [focusedProjectId] : []}
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
      workspaceProposalRefreshKey={workspaceProposalRefreshKey}
      onReviewWorkspaceProposal={async (proposal) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open' })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) nextParams.set('chatSessionId', sessionId)
        const workItemId = proposalWorkItemId(proposal)
        if (workItemId) nextParams.set('workItemId', workItemId)
        if (sessionId) {
          try {
            await setChatSessionProjectFocus(sessionId, proposal.projectId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddProject'))
          }
        }
        navigate(`/app/projects/${proposal.projectId}?${nextParams.toString()}`)
      }}
      showHeader={false}
      showWelcome={showWelcome}
      welcomeName={displayName}
      allowEmptyProject
      composerFooter={projectContext}
      projectReferences={new Map(projects.map((project) => [project.id, projectTitle(project, t('common.untitledProject'))] as const))}
      workItemReferences={visibleReferences}
      teamReferences={new Map([
        ...teams.map((team) => [team.id, team.name] as const),
        ...sessionContexts.filter((context) => context.entityType === CHAT_CONTEXT_ENTITY_TYPES.TEAM).map((context) => [context.entityId, context.label] as const),
      ])}
      userReferences={new Map([
        ...users.map((user) => [user.id, userTitle(user)] as const),
        ...sessionContexts.filter((context) => context.entityType === CHAT_CONTEXT_ENTITY_TYPES.USER).map((context) => [context.entityId, context.label] as const),
      ])}
      onClarificationChoice={selectClarificationChoice}
      onOpenResultWorkItem={async (projectId, workItemId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', workItemId })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
          try {
            await focusWorkItem(sessionId, projectId, workItemId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddWorkItem'))
          }
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onOpenFullHistory={async (projectId, workItemId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', workItemId, inspector: 'history' })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
          try {
            await focusWorkItem(sessionId, projectId, workItemId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddWorkItem'))
          }
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onOpenCalendar={(teamId, from, to) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', teamId, from, to })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) nextParams.set('chatSessionId', sessionId)
        navigate(`/app/calendar?${nextParams.toString()}`)
      }}
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
            await focusWorkItem(sessionId, projectId, workItemId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddWorkItem'))
          }
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onViewAllWorkItems={() => {
        const nextParams = new URLSearchParams({ chatPanel: 'open' })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) nextParams.set('chatSessionId', sessionId)
        navigate(`/app/my-work?${nextParams.toString()}`)
      }}
      onProjectReferenceClick={async (projectId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open' })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
        }
        if (sessionId) {
          try {
            await setChatSessionProjectFocus(sessionId, projectId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddProject'))
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
            await addChatSessionContext(sessionId, CHAT_CONTEXT_ENTITY_TYPES.TEAM, teamId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddTeam'))
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
            await addChatSessionContext(sessionId, CHAT_CONTEXT_ENTITY_TYPES.USER, userId)
          } catch (error) {
            toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddUser'))
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
      <div className={showStandaloneAction
        ? 'flex min-h-12 shrink-0 items-center justify-between gap-2 border-b px-4 py-2 pr-14 md:px-5 md:pr-14'
        : 'flex min-h-12 shrink-0 items-center justify-between gap-2 border-b px-4 py-2 md:px-5'}>
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('aiAgent.title')}</h1>
        {showStandaloneAction ? (
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            className="size-7 rounded-[min(var(--radius-md),12px)] p-0 [&_svg]:size-4"
            onClick={() => openStandaloneAiAgent(selectedSession?.id ?? requestedSessionId)}
            aria-label={t('pane.expandAiAgent')}
            title={t('pane.expandAiAgent')}
          >
            <Maximize2 className="h-4 w-4" />
          </Button>
        ) : null}
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
