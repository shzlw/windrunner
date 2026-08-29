import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useOutletContext, useSearchParams } from 'react-router'
import { AlertTriangle, Loader2, Plus, Search, X } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import ProjectChatPanel, { type ChatWorkItemReference } from '@/ProjectChatPanel'
import { addChatSessionContext, deleteChatSessionContext, getLlmStatus, listChatSessionContext, listNodes, listProjects, type ChatSessionContext, type Project, type ProjectNode } from '@/lib/api'
import type { AskPageOutletContext } from './App'

const maxSelectedProjects = 10

type AskPageProps = {
  projectId?: string
}

function projectTitle(project: Project) {
  return project.title?.trim() || project.name?.trim() || 'Untitled project'
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

export default function AskPage({ projectId: routeProjectId }: AskPageProps = {}) {
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
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectIds, setSelectedProjectIds] = useState<string[]>([])
  const [projectQuery, setProjectQuery] = useState('')
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
    const query = projectQuery.trim().toLowerCase()
    if (!query) {
      return projects
    }
    return projects.filter((project) => projectTitle(project).toLowerCase().includes(query))
  }, [projectQuery, projects])
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
        setProjectQuery('')
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
        const [nextProjects, llmStatus] = await Promise.all([
          listProjects(),
          getLlmStatus().catch(() => ({ provider: 'none', available: false })),
        ])
        if (!isMounted) {
          return
        }
        setProjects(nextProjects)
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
      {projects.length > 0 ? (
        <Popover onOpenChange={(open) => { if (!open) setProjectQuery('') }}>
          <PopoverTrigger
            render={(
              <Button type="button" size="icon-xs" variant="ghost" className="text-muted-foreground" aria-label="Add project context" title="Add project context">
                <Plus className="h-3.5 w-3.5" />
              </Button>
            )}
          />
          <PopoverContent align="start" className="w-[22rem] gap-0 p-0">
            <PopoverHeader className="border-b px-4 py-3">
              <PopoverTitle>Add project context</PopoverTitle>
            </PopoverHeader>
            <div className="border-b p-3">
              <div className="relative">
                <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={projectQuery}
                  onChange={(event) => setProjectQuery(event.target.value)}
                  placeholder="Search projects…"
                  className="pl-9"
                  autoFocus
                />
              </div>
            </div>
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
            <div className="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground">
              <span>{selectedProjects.length} selected</span>
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
      {!hasContext ? <span className="text-muted-foreground">Add context</span> : null}
      {hasContext ? <Button type="button" size="xs" variant="ghost" className="text-muted-foreground" onClick={clearContexts}>Clear all</Button> : null}
      {referencesLoading ? <span className="text-muted-foreground">Loading context…</span> : null}
    </div>
  )

  const chatContent = sessionsLoading ? (
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
    <ProjectChatPanel
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
      showHeader={false}
      allowEmptyProject
      composerFooter={projectContext}
      workItemReferences={visibleReferences}
      teamReferences={new Map(sessionContexts.filter((context) => context.entityType === 'TEAM').map((context) => [context.entityId, context.label]))}
      onWorkItemReferenceClick={(workItemId) => {
        const projectId = visibleReferences.get(workItemId)?.projectId ?? activeChatProjectId
        const nextParams = new URLSearchParams({
          chatPanel: 'open',
          workItemId,
        })
        const sessionId = selectedSession?.id ?? requestedSessionId
        if (sessionId) {
          nextParams.set('chatSessionId', sessionId)
        }
        navigate(`/app/projects/${projectId}?${nextParams.toString()}`)
      }}
      onTeamReferenceClick={(teamId) => {
        const nextParams = new URLSearchParams({ chatPanel: 'open', chatSessionId: selectedSession?.id ?? requestedSessionId ?? '' })
        if (!nextParams.get('chatSessionId')) nextParams.delete('chatSessionId')
        navigate(`/app/teams/${teamId}?${nextParams.toString()}`)
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
