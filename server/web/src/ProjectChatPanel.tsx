import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent, ReactNode } from 'react'
import { useParams } from 'react-router'
import { AlertTriangle, Bot, FileText, Loader2, Plus, SendHorizontal, Square, User, X } from 'lucide-react'
import { toast } from 'sonner'

import { Bubble, BubbleContent } from '@/components/ui/bubble'
import { Button } from '@/components/ui/button'
import { Message, MessageAvatar, MessageContent } from '@/components/ui/message'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import {
  getActiveChatSession,
  getChatSession,
  startNewChatSession,
  streamProjectChat,
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

const workItemReferencePattern = /\[\[workitem:([A-Za-z0-9_-]+)\]\]/g

function createMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `message-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function workItemStatusLabel(status: string) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase())
}

// A partially-streamed marker (e.g. "[[workitem:witm_ab" without its "]]")
// would flash as raw text before completing, so it is hidden until closed.
const incompleteTrailingMarkerPattern = /\[\[workitem:[^\]\s]*$/

function renderAssistantContent(
  content: string,
  references: Map<string, ChatWorkItemReference>,
  onReferenceClick?: (workItemId: string) => void,
) {
  const rendered: ReactNode[] = []
  content = content.replace(incompleteTrailingMarkerPattern, '')
  let lastIndex = 0
  let match: RegExpExecArray | null
  let referenceIndex = 0

  workItemReferencePattern.lastIndex = 0
  while ((match = workItemReferencePattern.exec(content)) !== null) {
    if (match.index > lastIndex) {
      rendered.push(<span key={`text-${match.index}`} className="whitespace-pre-wrap">{content.slice(lastIndex, match.index)}</span>)
    }

    const workItemId = match[1]
    const reference = references.get(workItemId)
    // Unresolved IDs stay visible (truncated) so hallucinated or missing
    // references are diagnosable instead of blending in.
    const label = reference?.title
      ?? (workItemId.length > 18 ? `${workItemId.slice(0, 15)}\u2026` : workItemId)
    const isClickable = Boolean(onReferenceClick)
    const referenceButton = (
      <button
        type="button"
        className={cn(
          'mx-0.5 inline-flex max-w-full items-center gap-1 rounded border px-1.5 py-0.5 align-baseline text-xs font-medium transition-colors',
          isClickable
            ? 'border-primary/30 bg-primary/5 text-primary hover:bg-primary/15'
            : 'border-border bg-muted text-muted-foreground',
        )}
        onClick={() => onReferenceClick?.(workItemId)}
        disabled={!isClickable}
        aria-label={`Select work item ${label}`}
      >
        <FileText className="h-3 w-3 shrink-0" />
        <span className="truncate">{label}</span>
      </button>
    )

    rendered.push(
      <Tooltip key={`reference-${referenceIndex}`}>
        <TooltipTrigger render={referenceButton} />
        <TooltipContent>
          <span>{reference ? `${workItemStatusLabel(reference.status)} · ${reference.type}` : 'Unresolved reference'}</span>
        </TooltipContent>
      </Tooltip>,
    )
    referenceIndex += 1
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.length) {
    rendered.push(<span key={`text-${lastIndex}`} className="whitespace-pre-wrap">{content.slice(lastIndex)}</span>)
  }

  return rendered.length > 0 ? rendered : <span className="whitespace-pre-wrap">{content}</span>
}

export default function ProjectChatPanel({
  projectId = '',
  projectIds,
  sessionId,
  readOnly = false,
  selectedContext,
  onClearSelectedContext,
  onSessionStarted,
  onSessionActivity,
  onStreamingChange,
  onGraphChangeProposalSaved,
  workItemReferences = new Map(),
  onWorkItemReferenceClick,
  className,
  flush = false,
  showHeader = true,
  onClose,
}: {
  projectId?: string
  projectIds?: string[]
  sessionId?: string
  readOnly?: boolean
  selectedContext?: SelectedChatContext | null
  onClearSelectedContext?: () => void
  onSessionStarted?: (session: ChatSession) => void
  onSessionActivity?: () => void | Promise<void>
  onStreamingChange?: (isStreaming: boolean) => void
  onGraphChangeProposalSaved?: () => void | Promise<void>
  workItemReferences?: Map<string, ChatWorkItemReference>
  onWorkItemReferenceClick?: (workItemId: string) => void | Promise<void>
  className?: string
  flush?: boolean
  showHeader?: boolean
  onClose?: () => void
}) {
  const routeProjectId = useParams().projectId
  projectId = projectId || routeProjectId || ''
  const chatProjectIds = projectIds?.length ? projectIds : projectId ? [projectId] : []
  const chatProjectId = chatProjectIds[0] ?? ''
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [isLoadingSession, setIsLoadingSession] = useState(true)
  const [isStartingSession, setIsStartingSession] = useState(false)
  const [isStreaming, setIsStreaming] = useState(false)
  const viewportRef = useRef<HTMLDivElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

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
    let isMounted = true

    async function loadSession() {
      setIsLoadingSession(true)
      try {
        const session = sessionId
          ? await getChatSession(chatProjectId, sessionId)
          : await getActiveChatSession(chatProjectId)
        if (isMounted) {
          applySession(session)
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
  }, [chatProjectId, sessionId])

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
    if (!content || isStreaming || isLoadingSession || readOnly) {
      return
    }

    const userMessage: ChatMessage = { id: createMessageId(), role: 'user', content }
    const assistantMessage: ChatMessage = { id: createMessageId(), role: 'assistant', content: '' }
    const requestMessages = [...messages, userMessage].map(({ role, content: messageContent }) => ({
      role,
      content: messageContent,
    }))
    const controller = new AbortController()
    let streamError: string | null = null
    let didFinish = false

    abortControllerRef.current = controller
    setDraft('')
    setMessages((current) => [...current, userMessage, assistantMessage])
    setIsStreaming(true)
    onStreamingChange?.(true)

    try {
      await streamProjectChat(
        chatProjectId,
        requestMessages,
        selectedContext?.context,
        ({ event: eventName, data }) => {
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
            void onSessionActivity?.()
            void onGraphChangeProposalSaved?.()
          }
        },
        controller.signal,
        projectIds,
      )

      if (streamError) {
        throw new Error(streamError)
      }
      if (!didFinish) {
        throw new Error('The chat stream ended before the response completed.')
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
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
      <form className="w-full" onSubmit={handleSend}>
        {readOnly ? (
          <div className="mb-2.5 rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
            This session is read-only. Start a new chat to continue.
          </div>
        ) : null}
        {selectedContext ? (
          <div className="mb-2.5 flex h-9 items-center gap-2 rounded-md border bg-muted/30 px-3 text-xs text-muted-foreground">
            <span className="shrink-0 font-medium text-foreground">Context</span>
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
        <div className="flex items-end gap-2">
          <Textarea
            rows={2}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleTextareaKeyDown}
            placeholder="Ask anything..."
            disabled={isLoadingSession || isStreaming || readOnly}
            className="max-h-32 min-h-16 resize-none"
          />
          {isStreaming ? (
            <Button type="button" size="icon" variant="outline" onClick={stopStreaming} aria-label="Stop response">
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button type="submit" size="icon" disabled={isLoadingSession || readOnly || !draft.trim()} aria-label="Send message">
              <SendHorizontal className="h-4 w-4" />
            </Button>
          )}
        </div>
      </form>
    )
  }

  async function handleStartNewSession() {
    if (isStreaming || isStartingSession) {
      return
    }

    setIsStartingSession(true)
    try {
      const session = await startNewChatSession(chatProjectId)
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
                    <BubbleContent className="whitespace-pre-wrap">
                      {message.status === 'error' ? (
                        <span className="flex min-w-0 items-start gap-2">
                          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                          <span className="min-w-0">{message.content}</span>
                        </span>
                      ) : (
                        message.content
                          ? renderAssistantContent(message.content, workItemReferences, onWorkItemReferenceClick)
                          : <Loader2 className="h-4 w-4 animate-spin" />
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
