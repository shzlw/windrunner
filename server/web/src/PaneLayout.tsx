import { useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Bot, ChevronLeft, ChevronRight } from 'lucide-react'
import { usePanelRef } from 'react-resizable-panels'

import { Button } from '@/components/ui/button'
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable'
import { useIsMobile } from '@/hooks/use-mobile'

type PaneLayoutMode = 'full' | 'chat' | 'artifact' | 'split'

type PaneLayoutProps = {
  mode: PaneLayoutMode
  content?: ReactNode
  chat?: ReactNode
  artifact?: ReactNode
  className?: string
}

const paneLayoutStorageKey = 'windrunner:conversation-artifact-layout'

function loadDefaultLayout() {
  try {
    const saved = window.localStorage.getItem(paneLayoutStorageKey)
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

function AssistantLauncher({ onClick }: { onClick: () => void }) {
  return (
    <Button
      type="button"
      size="icon"
      variant="outline"
      className="absolute right-4 bottom-4 z-20 rounded-full bg-background shadow-md"
      onClick={onClick}
      aria-label="Open Ask AI"
      title="Open Ask AI"
    >
      <Bot className="h-4 w-4" />
    </Button>
  )
}

export default function PaneLayout({ mode, content, chat, artifact, className }: PaneLayoutProps) {
  const isMobile = useIsMobile()
  const location = useLocation()
  const navigate = useNavigate()
  const chatPanelRef = usePanelRef()
  const [mobilePane, setMobilePane] = useState<'chat' | 'artifact'>('chat')
  const [isChatCollapsed, setIsChatCollapsed] = useState(false)
  const [defaultLayout] = useState(loadDefaultLayout)
  const rootClassName = ['flex min-h-0 min-w-0 flex-1 overflow-hidden bg-background', className ?? ''].join(' ')

  function openAssistant() {
    const params = new URLSearchParams(location.search)
    params.set('assistant', '1')
    const query = params.toString()
    navigate(`${location.pathname}${query ? `?${query}` : ''}${location.hash}`)
  }

  function toggleChatPanel() {
    const panel = chatPanelRef.current
    if (!panel) {
      return
    }
    if (panel.isCollapsed()) {
      panel.expand()
      setIsChatCollapsed(false)
    } else {
      panel.collapse()
      setIsChatCollapsed(true)
    }
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
        <AssistantLauncher onClick={openAssistant} />
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
          minSize={360}
          maxSize={440}
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
            {isChatCollapsed ? (
              <Button
                type="button"
                size="icon"
                variant="outline"
                className="absolute right-4 bottom-4 z-20 rounded-full bg-background shadow-md"
                onClick={toggleChatPanel}
                aria-label="Expand conversation"
                title="Expand conversation"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            ) : null}
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
