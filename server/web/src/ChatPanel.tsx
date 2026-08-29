import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent, ReactNode } from 'react'
import ReactMarkdown, { defaultUrlTransform } from 'react-markdown'
import type { Components } from 'react-markdown'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import { useParams } from 'react-router'
import { AlertTriangle, ArrowUp, Bot, FileText, Loader2, Plus, Square, User, UsersRound, X } from 'lucide-react'
import { toast } from 'sonner'

import { Bubble, BubbleContent } from '@/components/ui/bubble'
import { Button } from '@/components/ui/button'
import { Message, MessageAvatar, MessageContent } from '@/components/ui/message'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import {
  getChatSession,
  startNewChatSession,
  streamChatSession,
  type ChatSession,
  type ProjectChatContext,
  type ProjectChatMessage,
} from '@/lib/api'

type ChatMessage = ProjectChatMessage & {
  id: string
  status?: 'error'
}

type SelectedChatContext = {
  label: string
  context: ProjectChatContext
}

export type ChatWorkItemReference = {
  id: string
  title: string
  type: string
  status: string
  dueDate?: string | null
  projectId?: string
}

const artifactReferencePattern = /\[\[(workitem|team):([A-Za-z0-9_-]+)\]\]/g

function createMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `message-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function workItemStatusLabel(status: string) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase())
}

// A partially-streamed artifact marker (e.g. "[[team:team_ab" without its "]]")
// would flash as raw text before completing, so it is hidden until closed.
const incompleteTrailingMarkerPattern = /\[\[(?:workitem|team):[^\]\s]*$/

const markdownSanitizeSchema = {
  ...defaultSchema,
  protocols: {
    ...defaultSchema.protocols,
    href: [...(defaultSchema.protocols?.href ?? []), 'artifact'],
  },
}

function artifactMarkersToLinks(content: string) {
  return content.replace(
    artifactReferencePattern,
    (_marker, referenceType: 'workitem' | 'team', referenceId: string) => {
      const label = referenceType === 'team' ? 'Team' : 'Work item'
      return `[${label}](artifact:${referenceType}:${referenceId})`
    },
  )
}

function renderAssistantContent(
  content: string,
  references: Map<string, ChatWorkItemReference>,
  teamReferences: Map<string, string>,
  onWorkItemReferenceClick?: (workItemId: string) => void,
  onTeamReferenceClick?: (teamId: string) => void,
) {
  content = content.replace(incompleteTrailingMarkerPattern, '')
  const markdown = artifactMarkersToLinks(content)

  const components: Components = {
    h1: ({ children }) => <h3 className="mb-2 mt-4 text-base font-semibold first:mt-0">{children}</h3>,
    h2: ({ children }) => <h3 className="mb-2 mt-4 text-base font-semibold first:mt-0">{children}</h3>,
    h3: ({ children }) => <h4 className="mb-1.5 mt-3 text-sm font-semibold first:mt-0">{children}</h4>,
    p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
    ul: ({ children }) => <ul className="mb-2 ml-5 list-disc space-y-1 last:mb-0">{children}</ul>,
    ol: ({ children }) => <ol className="mb-2 ml-5 list-decimal space-y-1 last:mb-0">{children}</ol>,
    li: ({ children }) => <li className="pl-1">{children}</li>,
    blockquote: ({ children }) => <blockquote className="my-2 border-l-2 border-primary/30 pl-3 text-muted-foreground">{children}</blockquote>,
    strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
    code: ({ children, className }) => (
      <code className={cn('rounded bg-muted px-1 py-0.5 text-[0.85em]', className)}>{children}</code>
    ),
    pre: ({ children }) => <pre className="my-2 overflow-x-auto rounded-md bg-muted p-3 text-xs leading-relaxed">{children}</pre>,
    hr: () => <hr className="my-3 border-border" />,
    table: ({ children }) => (
      <div className="my-2 max-w-full overflow-x-auto">
        <table className="w-full border-collapse text-sm">{children}</table>
      </div>
    ),
    th: ({ children }) => <th className="border bg-muted px-2 py-1 text-left font-medium">{children}</th>,
    td: ({ children }) => <td className="border px-2 py-1 align-top">{children}</td>,
    a: ({ children, href, ...props }) => {
      const artifactMatch = href?.match(/^artifact:(workitem|team):([A-Za-z0-9_-]+)$/)
      if (!artifactMatch) {
        return (
          <a
            {...props}
            href={href}
            className="font-medium text-primary underline underline-offset-2 hover:text-primary/80"
            target="_blank"
            rel="noreferrer"
          >
            {children}
          </a>
        )
      }

      const referenceType = artifactMatch[1]
      const referenceId = artifactMatch[2]
      const reference = referenceType === 'workitem' ? references.get(referenceId) : undefined
      const teamName = referenceType === 'team' ? teamReferences.get(referenceId) : undefined
      // Unresolved IDs stay visible (truncated) so hallucinated or missing
      // references are diagnosable instead of blending in.
      const label = reference?.title
        ?? teamName
        ?? (referenceId.length > 18 ? `${referenceId.slice(0, 15)}\u2026` : referenceId)
      const isClickable = referenceType === 'team' ? Boolean(onTeamReferenceClick) : Boolean(onWorkItemReferenceClick)
      const referenceButton = (
        <button
          type="button"
          className={cn(
            'mx-0.5 inline-flex max-w-[min(100%,20rem)] items-center gap-1 rounded border px-1.5 py-0.5 align-baseline text-xs font-medium transition-colors',
            isClickable
              ? 'border-primary/30 bg-primary/5 text-primary hover:bg-primary/15'
              : 'border-border bg-muted text-muted-foreground',
          )}
          onClick={() => referenceType === 'team' ? onTeamReferenceClick?.(referenceId) : onWorkItemReferenceClick?.(referenceId)}
          disabled={!isClickable}
          aria-label={`Open ${referenceType === 'team' ? 'team' : 'work item'} ${label}`}
        >
          {referenceType === 'team' ? <UsersRound className="h-3 w-3 shrink-0" /> : <FileText className="h-3 w-3 shrink-0" />}
          <span className="truncate">{label}</span>
        </button>
      )

      return (
        <Tooltip>
          <TooltipTrigger render={referenceButton} />
          <TooltipContent>
            <span>{referenceType === 'team' ? 'Team' : reference ? `${workItemStatusLabel(reference.status)} · ${reference.type}` : 'Unresolved reference'}</span>
          </TooltipContent>
        </Tooltip>
      )
    },
  }

  return (
    <div className="min-w-0 text-sm leading-6">
      <ReactMarkdown
        components={components}
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeSanitize, markdownSanitizeSchema]]}
        skipHtml
        urlTransform={(url) => url.startsWith('artifact:') ? url : defaultUrlTransform(url)}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  )
}

export default function ChatPanel({
  projectId = '',
  projectIds,
  sessionId,
  selectedContext,
  onClearSelectedContext,
  onSessionStarted,
  onCreateSession,
  onInitialDraftSubmitted,
  onSessionActivity,
  onStreamingChange,
  onGraphChangeProposalSaved,
  workItemReferences = new Map(),
  onWorkItemReferenceClick,
  teamReferences = new Map(),
  onTeamReferenceClick,
  className,
  flush = false,
  showHeader = true,
  initialDraft,
  autoSubmitInitialDraft = false,
  composerFooter,
  onClose,
}: {
  projectId?: string
  projectIds?: string[]
  sessionId?: string
  selectedContext?: SelectedChatContext | null
  onClearSelectedContext?: () => void
  onSessionStarted?: (session: ChatSession) => void
  onCreateSession?: () => Promise<ChatSession | null>
  onInitialDraftSubmitted?: () => void
  onSessionActivity?: () => void | Promise<void>
  onStreamingChange?: (isStreaming: boolean) => void
  onGraphChangeProposalSaved?: () => void | Promise<void>
  workItemReferences?: Map<string, ChatWorkItemReference>
  onWorkItemReferenceClick?: (workItemId: string) => void | Promise<void>
  teamReferences?: Map<string, string>
  onTeamReferenceClick?: (teamId: string) => void | Promise<void>
  className?: string
  flush?: boolean
  showHeader?: boolean
  allowEmptyProject?: boolean
  initialDraft?: string
  autoSubmitInitialDraft?: boolean
  composerFooter?: ReactNode
  onClose?: () => void
}) {
  const routeProjectId = useParams().projectId
  projectId = projectId || routeProjectId || ''
  const chatProjectIds = projectIds?.length ? projectIds : projectId ? [projectId] : []
  const chatProjectId = chatProjectIds[0] ?? ''
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState(initialDraft ?? '')
  const [isLoadingSession, setIsLoadingSession] = useState(true)
  const [isStartingSession, setIsStartingSession] = useState(false)
  const [isStreaming, setIsStreaming] = useState(false)
  const viewportRef = useRef<HTMLDivElement>(null)
  const composerFormRef = useRef<HTMLFormElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const hasAutoSubmittedInitialDraftRef = useRef(false)
  const isRequestInFlightRef = useRef(false)
  const requestSessionIdRef = useRef<string | null>(null)

  useEffect(() => {
    const viewport = viewportRef.current
    if (viewport) {
      viewport.scrollTop = viewport.scrollHeight
    }
  }, [messages])

  useEffect(() => {
    return () => abortControllerRef.current?.abort()
  }, [])

  useEffect(() => {
    if (
      !autoSubmitInitialDraft
      || !initialDraft?.trim()
      || hasAutoSubmittedInitialDraftRef.current
      || !sessionId
      || isLoadingSession
      || isStreaming
    ) {
      return
    }

    hasAutoSubmittedInitialDraftRef.current = true
    onInitialDraftSubmitted?.()
    const timeoutId = window.setTimeout(() => composerFormRef.current?.requestSubmit(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [autoSubmitInitialDraft, chatProjectId, initialDraft, isLoadingSession, isStreaming, onInitialDraftSubmitted, sessionId])

  useEffect(() => {
    let isMounted = true

    async function loadSession() {
      if (!sessionId) {
        setMessages([])
        setIsLoadingSession(false)
        return
      }
      // Creating a session refreshes the sidebar and can update sessionId
      // while the first request is still streaming. Keep the optimistic
      // messages until that request has finished instead of replacing them
      // with the partially persisted session.
      if (isRequestInFlightRef.current && requestSessionIdRef.current === sessionId) {
        setIsLoadingSession(false)
        return
      }
      setIsLoadingSession(true)
      try {
        const session = await getChatSession(sessionId)
        const requestStartedDuringLoad = isRequestInFlightRef.current && requestSessionIdRef.current === sessionId
        if (isMounted && !requestStartedDuringLoad) {
          if (session) applySession(session)
          else setMessages([])
        }
      } catch (error) {
        if (isMounted) {
          toast.error(error instanceof Error ? error.message : 'Failed to load chat session.')
        }
      } finally {
        if (isMounted) {
          setIsLoadingSession(false)
        }
      }
    }

    void loadSession()

    return () => {
      isMounted = false
    }
  }, [sessionId])

  function applySession(session: ChatSession) {
    setMessages(session.messages.map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
    })))
  }

  async function handleSend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || isStreaming || isLoadingSession) {
      return
    }

    let activeSessionId = sessionId
    if (!activeSessionId) {
      const createdSession = onCreateSession ? await onCreateSession() : await startNewChatSession()
      activeSessionId = createdSession?.id
      if (createdSession) onSessionStarted?.(createdSession)
    }
    if (!activeSessionId) {
      toast.error('Start a chat session before sending a message.')
      return
    }
    const requestSessionId = activeSessionId

    const userMessage: ChatMessage = { id: createMessageId(), role: 'user', content }
    const assistantMessage: ChatMessage = { id: createMessageId(), role: 'assistant', content: '' }
    const requestMessages = [...messages, userMessage].map(({ role, content: messageContent }) => ({
      role,
      content: messageContent,
    }))
    const controller = new AbortController()
    let streamError: string | null = null
    let didFinish = false
    let recoveredPersistedResponse = false

    async function syncPersistedResponse() {
      if (controller.signal.aborted) return false
      try {
        const latestSession = await getChatSession(requestSessionId)
        const lastMessage = latestSession.messages[latestSession.messages.length - 1]
        const hasNewAssistantResponse = latestSession.messages.length > requestMessages.length
          && lastMessage?.role === 'assistant'
        if (!hasNewAssistantResponse) return false
        applySession(latestSession)
        return true
      } catch {
        return false
      }
    }

    requestSessionIdRef.current = activeSessionId
    isRequestInFlightRef.current = true
    abortControllerRef.current = controller
    setDraft('')
    setMessages((current) => [...current, userMessage, assistantMessage])
    setIsStreaming(true)
    onStreamingChange?.(true)

    try {
      await streamChatSession(
        activeSessionId,
        requestMessages,
        selectedContext?.context,
        ({ event: eventName, data }) => {
          if (eventName === 'started') {
            void onSessionActivity?.()
          }
          if (eventName === 'delta' && data.text) {
            setMessages((current) => current.map((message) => (
              message.id === assistantMessage.id
                ? { ...message, content: message.content + data.text }
                : message
            )))
          }
          if (eventName === 'error') {
            streamError = data.message ?? 'The response could not be completed.'
          }
          if (eventName === 'done') {
            didFinish = true
            setMessages((current) => current.map((message) => {
              if (message.id === userMessage.id && data.sourceMessageId) {
                return { ...message, id: data.sourceMessageId }
              }
              if (message.id === assistantMessage.id && data.assistantMessageId) {
                return { ...message, id: data.assistantMessageId }
              }
              return message
            }))
            void onGraphChangeProposalSaved?.()
          }
        },
        controller.signal,
        projectIds,
        chatProjectId,
      )

      if (streamError) {
        throw new Error(streamError)
      }
      recoveredPersistedResponse = await syncPersistedResponse()
      if (!didFinish && !recoveredPersistedResponse) {
        throw new Error('The chat stream ended before the response completed.')
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (!recoveredPersistedResponse) {
        recoveredPersistedResponse = await syncPersistedResponse()
      }
      if (recoveredPersistedResponse) {
        return
      }
      const errorMessage = error instanceof Error ? error.message : 'Failed to stream the response.'
      setMessages((current) => current.map((message) => {
        if (message.id !== assistantMessage.id) {
          return message
        }
        const prefix = message.content.trim() ? `${message.content.trim()}\n\n` : ''
        return {
          ...message,
          status: 'error',
          content: `${prefix}Response failed: ${errorMessage}`,
        }
      }))
      toast.error(errorMessage)
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null
      }
      setIsStreaming(false)
      onStreamingChange?.(false)
      void onSessionActivity?.()
      isRequestInFlightRef.current = false
      requestSessionIdRef.current = null
    }
  }

  function handleTextareaKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  function stopStreaming() {
    abortControllerRef.current?.abort()
  }

  function renderComposer() {
    return (
      <form ref={composerFormRef} className="w-full" onSubmit={handleSend}>
        {selectedContext ? (
          <div className="mb-2.5 flex h-9 items-center gap-2 rounded-md border bg-muted/30 px-3 text-xs text-muted-foreground">
            <span className="min-w-0 truncate">{selectedContext.label}</span>
            <Button
              type="button"
              size="icon-xs"
              variant="ghost"
              className="-mr-1 ml-auto"
              onClick={onClearSelectedContext}
              aria-label="Clear selected context"
              title="Clear selected context"
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        ) : null}
        {messages.length > 0 && composerFooter ? <div className="mb-2.5">{composerFooter}</div> : null}
        <div className="rounded-md border bg-background p-2 transition-colors focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500/20">
          <Textarea
            rows={2}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleTextareaKeyDown}
            placeholder="Ask anything..."
            disabled={isLoadingSession}
            className="max-h-32 min-h-16 resize-none border-0 px-1 py-1 shadow-none focus-visible:border-0 focus-visible:ring-0"
          />
          <div className="flex justify-end pt-1">
            {isStreaming ? (
              <Button type="button" size="icon" variant="outline" onClick={stopStreaming} aria-label="Stop response">
                <Square className="h-4 w-4" />
              </Button>
            ) : (
              <Button type="submit" size="icon" disabled={isLoadingSession || !draft.trim()} aria-label="Send message">
                <ArrowUp className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
        {messages.length === 0 && composerFooter ? <div className="mt-2.5">{composerFooter}</div> : null}
      </form>
    )
  }

  async function handleStartNewSession() {
    if (isStreaming || isStartingSession) {
      return
    }

    setIsStartingSession(true)
    try {
      const session = onCreateSession ? await onCreateSession() : await startNewChatSession()
      if (!session) return
      applySession(session)
      setDraft('')
      onSessionStarted?.(session)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to start a new chat session.')
    } finally {
      setIsStartingSession(false)
    }
  }

  return (
    <div
      className={cn(
        'flex min-w-0 flex-col overflow-hidden bg-background',
        flush ? 'h-full min-h-0 border-0' : 'min-h-[560px] rounded-md border xl:h-full xl:min-h-0',
        className,
      )}
    >
      {showHeader ? (
        <div className="flex h-10 shrink-0 items-center border-b px-3">
          <div className="flex w-full items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2">
              <Bot className="h-4 w-4 shrink-0 text-primary" />
              <h2 className="truncate text-sm font-semibold leading-none tracking-normal">Chat with AI</h2>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <Button
                type="button"
                size="icon-sm"
                variant="ghost"
                onClick={() => void handleStartNewSession()}
                disabled={isLoadingSession || isStreaming || isStartingSession}
                aria-label="Start new chat"
              >
                {isStartingSession ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              </Button>
              {onClose ? (
                <Button
                  type="button"
                  size="icon-sm"
                  variant="ghost"
                  onClick={onClose}
                  aria-label="Close AI chat"
                >
                  <X className="h-4 w-4" />
                </Button>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1 flex-col p-0">
        <div ref={viewportRef} className="min-h-0 flex-1 space-y-3 overflow-y-auto p-3">
          {isLoadingSession ? (
            <div className="flex h-full min-h-56 items-center justify-center text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex h-full min-h-56 flex-col items-center justify-center gap-5 p-6">
              <h2 className="text-center text-xl font-semibold tracking-tight">How can I help you today?</h2>
              <div className="w-full max-w-2xl">
                {renderComposer()}
              </div>
            </div>
          ) : (
            messages.map((message) => (
              <Message key={message.id} align={message.role === 'user' ? 'end' : 'start'}>
                <MessageAvatar className="h-8 w-8 border bg-background">
                  {message.role === 'user' ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
                </MessageAvatar>
                <MessageContent>
                  <Bubble
                    align={message.role === 'user' ? 'end' : 'start'}
                    variant={message.status === 'error' ? 'destructive' : message.role === 'user' ? 'default' : 'muted'}
                  >
                    <BubbleContent className="min-w-0">
                      {message.status === 'error' ? (
                        <span className="flex min-w-0 items-start gap-2">
                          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                          <span className="min-w-0">{message.content}</span>
                        </span>
                      ) : (
                        message.content
                          ? message.role === 'assistant'
                            ? renderAssistantContent(message.content, workItemReferences, teamReferences, onWorkItemReferenceClick, onTeamReferenceClick)
                            : <span className="whitespace-pre-wrap">{message.content}</span>
                          : (
                            <span className="inline-flex items-center gap-2 text-sm text-muted-foreground">
                              <Loader2 className="h-4 w-4 animate-spin" />
                              <span>Thinking…</span>
                            </span>
                          )
                      )}
                    </BubbleContent>
                  </Bubble>
                </MessageContent>
              </Message>
            ))
          )}
        </div>

        {messages.length > 0 ? <div className="shrink-0 border-t px-3 py-3">{renderComposer()}</div> : null}
      </div>
    </div>
  )
}
