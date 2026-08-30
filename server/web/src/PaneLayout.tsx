import { useMemo, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Bot, ChevronLeft } from 'lucide-react'
import { useGroupRef, usePanelRef } from 'react-resizable-panels'

import { Button } from '@/components/ui/button'
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable'
import { useIsMobile } from '@/hooks/use-mobile'

type PaneLayoutMode = 'full' | 'chat' | 'artifact' | 'split'

type PaneLayoutProps = {
  mode: PaneLayoutMode
  content?: ReactNode
  chat?: ReactNode
  artifact?: ReactNode
  onOpenAssistant?: () => void | Promise<void>
  assistantLabel?: string
  className?: string
}

const paneLayoutStorageKey = 'windrunner:conversation-artifact-layout'

function loadDefaultLayout(storageKey: string) {
  try {
    const saved = window.localStorage.getItem(storageKey)
    if (saved) {
      const parsed = JSON.parse(saved) as Record<string, number>
      if (Number.isFinite(parsed['conversation-pane']) && Number.isFinite(parsed['artifact-pane'])) {
        return parsed
      }
    }
  } catch {
    // Layout persistence is optional when browser storage is unavailable.
  }
  return { 'conversation-pane': 35, 'artifact-pane': 65 }
}

function AssistantLauncher({ onClick, label = 'Open Ask AI' }: { onClick: () => void | Promise<void>; label?: string }) {
  return (
    <Button
      type="button"
      size="icon-lg"
      variant="outline"
      className="absolute right-5 bottom-5 z-20 h-14 w-14 rounded-full bg-background shadow-md"
      onClick={() => void onClick()}
      aria-label={label}
      title={label}
    >
      <Bot className="h-7 w-7" size={48} />
    </Button>
  )
}

export default function PaneLayout({ mode, content, chat, artifact, onOpenAssistant, assistantLabel, className }: PaneLayoutProps) {
  const isMobile = useIsMobile()
  const location = useLocation()
  const navigate = useNavigate()
  const groupRef = useGroupRef()
  const chatPanelRef = usePanelRef()
  const [mobilePane, setMobilePane] = useState<'chat' | 'artifact'>('chat')
  const [isChatCollapsed, setIsChatCollapsed] = useState(false)
  const defaultLayout = useMemo(() => loadDefaultLayout(paneLayoutStorageKey), [])
  const rootClassName = ['flex min-h-0 min-w-0 flex-1 overflow-hidden bg-background', className ?? ''].join(' ')

  function openAssistant() {
    const params = new URLSearchParams(location.search)
    params.set('chatPanel', 'open')
    const query = params.toString()
    navigate(`${location.pathname}${query ? `?${query}` : ''}${location.hash}`)
  }

  const handleOpenAssistant = onOpenAssistant ?? openAssistant

  function toggleChatPanel() {
    const panel = chatPanelRef.current
    if (!panel) {
      return
    }
    const shouldOpen = panel.isCollapsed()
    if (shouldOpen) {
      panel.expand()
      setIsChatCollapsed(false)
    } else {
      panel.collapse()
      setIsChatCollapsed(true)
    }

    const params = new URLSearchParams(location.search)
    params.set('chatPanel', shouldOpen ? 'open' : 'closed')
    const query = params.toString()
    navigate(`${location.pathname}${query ? `?${query}` : ''}${location.hash}`, { replace: true })
  }

  async function expandAssistant() {
    await handleOpenAssistant()
    toggleChatPanel()
  }

  if (mode === 'full') {
    return <div className={rootClassName} data-layout="full">{content}</div>
  }

  if (mode === 'chat') {
    return (
      <div className={rootClassName} data-layout="chat-only">
        <main className="mx-auto flex min-h-0 min-w-0 w-full max-w-4xl flex-1 flex-col overflow-hidden">
          {chat}
        </main>
      </div>
    )
  }

  if (mode === 'artifact') {
    return (
      <div className={[rootClassName, 'relative'].join(' ')} data-layout="artifact-only">
        <main className="min-h-0 min-w-0 flex-1 overflow-hidden">
          {artifact}
        </main>
        <AssistantLauncher onClick={handleOpenAssistant} label={assistantLabel ?? 'Open Ask AI'} />
      </div>
    )
  }

  if (isMobile) {
    return (
      <div className={[rootClassName, 'flex-col'].join(' ')} data-layout="chat-artifact">
        <div className="flex shrink-0 items-center gap-1 border-b bg-background p-1.5" role="tablist" aria-label="Workspace panes">
          <button
            type="button"
            role="tab"
            aria-selected={mobilePane === 'chat'}
            className={['flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors', mobilePane === 'chat' ? 'bg-muted text-foreground' : 'text-muted-foreground hover:text-foreground'].join(' ')}
            onClick={() => setMobilePane('chat')}
          >
            Chat
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mobilePane === 'artifact'}
            className={['flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors', mobilePane === 'artifact' ? 'bg-muted text-foreground' : 'text-muted-foreground hover:text-foreground'].join(' ')}
            onClick={() => setMobilePane('artifact')}
          >
            Artifact
          </button>
        </div>
        <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <aside className={[mobilePane === 'chat' ? 'flex' : 'hidden', 'min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background'].join(' ')}>
            {chat}
          </aside>
          <main className={[mobilePane === 'artifact' ? 'flex' : 'hidden', 'min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background'].join(' ')}>
            {artifact}
          </main>
        </div>
      </div>
    )
  }

  return (
    <div className={rootClassName} data-layout="chat-artifact">
      <ResizablePanelGroup
        id="conversation-artifact-layout"
        groupRef={groupRef}
        orientation="horizontal"
        defaultLayout={defaultLayout}
        onLayoutChanged={(layout, meta) => {
          if (!meta.isUserInteraction) {
            return
          }
          try {
            window.localStorage.setItem(paneLayoutStorageKey, JSON.stringify(layout))
          } catch {
            // Layout persistence is optional when browser storage is unavailable.
          }
        }}
        className="min-h-0 min-w-0 flex-1 overflow-hidden"
      >
        <ResizablePanel
          id="conversation-pane"
          defaultSize="35%"
          minSize={280}
          collapsible
          collapsedSize={0}
          panelRef={chatPanelRef}
          onResize={(size) => setIsChatCollapsed(size.inPixels < 10)}
        >
          <div className="relative flex h-full min-h-0 min-w-0 flex-col overflow-hidden border-r bg-background">
            {chat}
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              className="absolute top-2 right-2 z-10 hidden bg-background/90 shadow-sm md:flex"
              onClick={toggleChatPanel}
              aria-label="Collapse conversation"
              title="Collapse conversation"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
          </div>
        </ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel id="artifact-pane" defaultSize="65%" minSize={320}>
          <div className="relative flex h-full min-h-0 min-w-0 flex-col overflow-hidden bg-background">
            {artifact}
            {isChatCollapsed ? <AssistantLauncher onClick={expandAssistant} label={assistantLabel ?? 'Expand Ask AI'} /> : null}
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
