import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { useParams } from 'react-router'
import { AlertTriangle, Bot, Loader2, Plus, SendHorizontal, Square, User, X } from 'lucide-react'
import { toast } from 'sonner'

import { Bubble, BubbleContent } from '@/components/ui/bubble'
import { Button } from '@/components/ui/button'
import { Message, MessageAvatar, MessageContent } from '@/components/ui/message'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import {
  getActiveChatSession,
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

function createMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `message-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export default function ProjectChatPanel({
  projectId = '',
  selectedContext,
  onClearSelectedContext,
  onGraphChangeProposalSaved,
  className,
  flush = false,
  onClose,
}: {
  projectId?: string
  selectedContext?: SelectedChatContext | null
  onClearSelectedContext?: () => void
  onGraphChangeProposalSaved?: () => void | Promise<void>
  className?: string
  flush?: boolean
  onClose?: () => void
}) {
  const routeProjectId = useParams().projectId
  projectId = projectId || routeProjectId || ''
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
        const session = await getActiveChatSession(projectId)
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
  }, [projectId])

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

    try {
      await streamProjectChat(
        projectId,
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
            void onGraphChangeProposalSaved?.()
          }
        },
        controller.signal,
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

  async function handleStartNewSession() {
    if (isStreaming || isStartingSession) {
      return
    }

    setIsStartingSession(true)
    try {
      const session = await startNewChatSession(projectId)
      applySession(session)
      setDraft('')
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
              aria-label="Start new AI edit"
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

      <div className="flex min-h-0 flex-1 flex-col p-0">
        <div ref={viewportRef} className="min-h-0 flex-1 space-y-3 overflow-y-auto p-3">
          {isLoadingSession ? (
            <div className="flex h-full min-h-56 items-center justify-center text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex h-full min-h-56 items-center justify-center text-sm text-muted-foreground">
              No messages yet
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
                        message.content || <Loader2 className="h-4 w-4 animate-spin" />
                      )}
                    </BubbleContent>
                  </Bubble>
                </MessageContent>
              </Message>
            ))
          )}
        </div>

        <form className="shrink-0 border-t px-3 py-3" onSubmit={handleSend}>
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
              disabled={isLoadingSession || isStreaming}
              className="max-h-32 min-h-16 resize-none"
            />
            {isStreaming ? (
              <Button type="button" size="icon" variant="outline" onClick={stopStreaming} aria-label="Stop response">
                <Square className="h-4 w-4" />
              </Button>
            ) : (
              <Button type="submit" size="icon" disabled={isLoadingSession || !draft.trim()} aria-label="Send message">
                <SendHorizontal className="h-4 w-4" />
              </Button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}
