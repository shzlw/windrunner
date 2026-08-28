import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { AlertTriangle, Clock3, Loader2, MessageSquareText, Plus, Search, WandSparkles, X } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable'
import ProjectChatPanel, { type ChatWorkItemReference } from '@/ProjectChatPanel'
import { getLlmStatus, listChatSessions, listNodes, listProjects, type ChatSession, type ChatSessionSummary, type Project, type ProjectNode } from '@/lib/api'
import { cn } from '@/lib/utils'

const maxSelectedProjects = 10
const askPanelLayoutStorageKey = 'windrunner.ask.panel-layout'
const defaultAskPanelLayout = { 'ask-sessions': 28, 'ask-chat': 72 }

function readAskPanelLayout() {
  if (typeof window === 'undefined') {
    return defaultAskPanelLayout
  }
  try {
    const storedLayout = JSON.parse(window.localStorage.getItem(askPanelLayoutStorageKey) ?? '') as Record<string, unknown>
    const sessionsSize = Number(storedLayout['ask-sessions'])
    const chatSize = Number(storedLayout['ask-chat'])
    if (Number.isFinite(sessionsSize) && Number.isFinite(chatSize) && sessionsSize >= 20 && sessionsSize <= 42 && chatSize >= 58 && chatSize <= 80 && Math.abs(sessionsSize + chatSize - 100) < 0.1) {
      return { 'ask-sessions': sessionsSize, 'ask-chat': chatSize }
    }
  } catch {
    // Ignore missing or malformed persisted layouts.
  }
  return defaultAskPanelLayout
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

function formatSessionDate(timestamp?: string) {
  if (!timestamp) {
    return 'New conversation'
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(timestamp))
}

export default function AskPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectIds, setSelectedProjectIds] = useState<string[]>([])
  const [projectQuery, setProjectQuery] = useState('')
  const [references, setReferences] = useState<Map<string, ChatWorkItemReference>>(new Map())
  const [chatSessions, setChatSessions] = useState<ChatSessionSummary[]>([])
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [sessionsProjectId, setSessionsProjectId] = useState<string | null>(null)
  const [isLoadingReferences, setIsLoadingReferences] = useState(false)
  const [isLlmAvailable, setIsLlmAvailable] = useState(false)

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
  const primaryProjectId = selectedProjectIds[0] ?? ''

  const selectedSession = useMemo(
    () => chatSessions.find((session) => session.id === selectedSessionId && session.projectId === primaryProjectId),
    [chatSessions, primaryProjectId, selectedSessionId],
  )
  const visibleChatSessions = useMemo(
    () => chatSessions.filter((session) => session.projectId === primaryProjectId),
    [chatSessions, primaryProjectId],
  )

  const refreshChatSessions = useCallback(async (projectId: string, preferredSessionId?: string) => {
    const nextSessions = await listChatSessions(projectId)
    setChatSessions(nextSessions)
    setSelectedSessionId((current) => {
      if (preferredSessionId && nextSessions.some((session) => session.id === preferredSessionId)) {
        return preferredSessionId
      }
      if (current && nextSessions.some((session) => session.id === current)) {
        return current
      }
      return nextSessions.find((session) => session.status === 'ACTIVE')?.id ?? nextSessions[0]?.id ?? null
    })
  }, [])

  useEffect(() => {
    let isMounted = true
    if (!primaryProjectId) {
      return () => {
        isMounted = false
      }
    }

    listChatSessions(primaryProjectId)
      .then((nextSessions) => {
        if (!isMounted) {
          return
        }
        setChatSessions(nextSessions)
        setSessionsProjectId(primaryProjectId)
        setSelectedSessionId(nextSessions.find((session) => session.status === 'ACTIVE')?.id ?? nextSessions[0]?.id ?? null)
      })
      .catch((error) => {
        if (isMounted) {
          setSessionsProjectId(primaryProjectId)
          toast.error(error instanceof Error ? error.message : 'Failed to load chat sessions.')
        }
      })

    return () => {
      isMounted = false
    }
  }, [primaryProjectId])

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
        setSelectedProjectIds((current) => {
          const stillAvailable = current.filter((projectId) => nextProjects.some((project) => project.id === projectId))
          return stillAvailable.length > 0 ? stillAvailable : nextProjects[0] ? [nextProjects[0].id] : []
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
  }, [])

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
  }

  function removeProject(projectId: string) {
    setSelectedProjectIds((current) => current.filter((currentProjectId) => currentProjectId !== projectId))
  }

  function clearProjects() {
    setSelectedProjectIds([])
  }

  const visibleReferences = useMemo(() => {
    if (selectedProjectIds.length === 0) {
      return new Map<string, ChatWorkItemReference>()
    }
    return new Map(
      [...references].filter(([, reference]) => !reference.projectId || selectedProjectIds.includes(reference.projectId)),
    )
  }, [references, selectedProjectIds])
  const isLoadingSessions = Boolean(primaryProjectId) && sessionsProjectId !== primaryProjectId
  const referencesLoading = selectedProjectIds.length > 0 && isLoadingReferences

  function showSessionError(error: unknown) {
    toast.error(error instanceof Error ? error.message : 'Failed to refresh chat sessions.')
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Ask</h1>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {isLoading ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-background">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <ResizablePanelGroup
            orientation="horizontal"
            defaultLayout={readAskPanelLayout()}
            onLayoutChanged={(layout, meta) => {
              if (!meta.isUserInteraction) return
              try {
                window.localStorage.setItem(askPanelLayoutStorageKey, JSON.stringify(layout))
              } catch {
                // Layout persistence is optional when browser storage is unavailable.
              }
            }}
            className="min-h-0 min-w-0 flex-1 overflow-hidden border-b bg-background"
          >
            <ResizablePanel id="ask-sessions" defaultSize="28" minSize="20" maxSize="42">
              <aside className="flex h-full min-h-0 flex-col overflow-hidden bg-background">
                <div className="flex min-h-12 shrink-0 items-center gap-2 border-b px-3 py-2">
                  <div className="flex min-w-0 items-center gap-2">
                    <MessageSquareText className="h-4 w-4 shrink-0 text-primary" />
                    <h2 className="text-sm font-semibold">Sessions</h2>
                  </div>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto p-2">
                  {!primaryProjectId ? (
                    <div className="px-2 py-8 text-center text-sm text-muted-foreground">Select a project to see sessions</div>
                  ) : isLoadingSessions ? (
                    <div className="flex items-center justify-center py-8 text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" />
                    </div>
                  ) : visibleChatSessions.length === 0 ? (
                    <div className="px-2 py-8 text-center text-sm text-muted-foreground">No chat sessions yet</div>
                  ) : (
                    <div className="space-y-1">
                      {visibleChatSessions.map((session) => {
                        const isSelected = session.id === selectedSessionId
                        return (
                          <button
                            key={session.id}
                            type="button"
                            className={cn(
                              'flex w-full min-w-0 items-start gap-2 rounded-md px-2.5 py-2 text-left transition-colors hover:bg-muted',
                              isSelected && 'bg-muted',
                            )}
                            onClick={() => setSelectedSessionId(session.id)}
                            aria-current={isSelected ? 'page' : undefined}
                          >
                            <MessageSquareText className={cn('mt-0.5 h-4 w-4 shrink-0', isSelected ? 'text-primary' : 'text-muted-foreground')} />
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-sm">{session.title}</span>
                              <span className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                                <Clock3 className="h-3 w-3 shrink-0" />
                                {session.status === 'ACTIVE' ? 'Current' : formatSessionDate(session.updatedAt ?? session.createdAt)}
                              </span>
                            </span>
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              </aside>
            </ResizablePanel>

            <ResizableHandle withHandle />

            <ResizablePanel id="ask-chat" defaultSize="72" minSize="58">
              <section className="flex h-full min-h-0 flex-col overflow-hidden bg-background">
                <div className="flex shrink-0 flex-col gap-3 border-b p-3 md:p-4">
                  <div className="flex items-center gap-2">
                    {projects.length > 0 ? (
                      <Popover onOpenChange={(open) => { if (!open) setProjectQuery('') }}>
                        <PopoverTrigger
                          render={(
                            <Button type="button" variant="outline" className="w-full justify-start gap-2 sm:w-auto">
                              <Plus className="h-4 w-4" />
                              Add project
                              <span className="text-muted-foreground">({selectedProjects.length})</span>
                            </Button>
                          )}
                        />
                        <PopoverContent align="end" className="w-[22rem] gap-0 p-0">
                          <PopoverHeader className="border-b px-4 py-3">
                            <PopoverTitle>Add project</PopoverTitle>
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
                            <Button type="button" size="xs" variant="ghost" onClick={clearProjects} disabled={selectedProjects.length === 0}>Clear all</Button>
                          </div>
                        </PopoverContent>
                      </Popover>
                    ) : null}
                    {referencesLoading ? <span className="text-xs text-muted-foreground">Loading context…</span> : null}
                  </div>

                  {selectedProjects.length > 0 ? (
                    <div className="flex flex-wrap items-center gap-2">
                      {selectedProjects.map((project) => (
                        <Badge key={project.id} variant="outline" className="h-8 max-w-full gap-2 px-3 pr-1.5 text-sm font-normal">
                          <span className="max-w-56 truncate">{projectTitle(project)}</span>
                          <button type="button" className="rounded-sm p-1 hover:bg-muted" onClick={() => removeProject(project.id)} aria-label={`Remove ${projectTitle(project)} from context`}>
                            <X className="h-3.5 w-3.5" />
                          </button>
                        </Badge>
                      ))}
                      <Button type="button" size="xs" variant="ghost" className="text-muted-foreground" onClick={clearProjects}>Clear all</Button>
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground">Add at least one project to give the AI context.</p>
                  )}
                </div>

                <div className="min-h-0 flex-1 overflow-hidden">
                  {isLoadingSessions ? (
                    <div className="flex h-full min-h-0 items-center justify-center bg-background text-muted-foreground">
                      <Loader2 className="h-5 w-5 animate-spin" />
                    </div>
                  ) : !primaryProjectId ? (
                    <div className="flex h-full min-h-0 items-center justify-center bg-background p-6">
                      <Empty className="border-0">
                        <EmptyHeader>
                          <EmptyMedia variant="icon"><WandSparkles /></EmptyMedia>
                          <EmptyTitle>Select project context</EmptyTitle>
                          <EmptyDescription>Choose one or more projects above to start asking Windrunner for help.</EmptyDescription>
                        </EmptyHeader>
                      </Empty>
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
                      projectId={primaryProjectId}
                      projectIds={selectedProjectIds}
                      sessionId={selectedSession?.id}
                      readOnly={Boolean(selectedSession && selectedSession.status !== 'ACTIVE')}
                      onSessionStarted={(session: ChatSession) => {
                        setSelectedSessionId(session.id)
                        void refreshChatSessions(primaryProjectId, session.id).catch(showSessionError)
                      }}
                      onSessionActivity={() => refreshChatSessions(primaryProjectId).catch(showSessionError)}
                      workItemReferences={visibleReferences}
                      onWorkItemReferenceClick={(workItemId) => {
                        const projectId = visibleReferences.get(workItemId)?.projectId ?? primaryProjectId
                        navigate(`/app/projects/${projectId}?workItemId=${encodeURIComponent(workItemId)}`)
                      }}
                      flush
                      className="h-full min-h-0"
                    />
                  )}
                </div>
              </section>
            </ResizablePanel>
          </ResizablePanelGroup>
        )}
      </div>
    </div>
  )
}
