import { useState, type ReactNode } from 'react'

type PaneLayoutMode = 'full' | 'chat' | 'artifact' | 'split'

type PaneLayoutProps = {
  mode: PaneLayoutMode
  content?: ReactNode
  chat?: ReactNode
  artifact?: ReactNode
  className?: string
}

export default function PaneLayout({ mode, content, chat, artifact, className }: PaneLayoutProps) {
  const [mobilePane, setMobilePane] = useState<'chat' | 'artifact'>('chat')
  const rootClassName = ['flex min-h-0 min-w-0 flex-1 overflow-hidden bg-background', className ?? ''].join(' ')

  if (mode === 'full') {
    return <div className={rootClassName} data-layout="full">{content}</div>
  }

  const isSplit = mode === 'split'
  const chatClassName = mode === 'chat'
    ? 'mx-auto flex min-h-0 min-w-0 w-full max-w-4xl flex-1 flex-col overflow-hidden'
    : [mobilePane === 'chat' ? 'flex' : 'hidden', 'min-h-0 min-w-0 shrink-0 flex-col overflow-hidden border-b bg-background md:flex md:w-[38%] md:max-w-[32rem] md:border-r md:border-b-0'].join(' ')
  const artifactClassName = mode === 'artifact'
    ? 'min-h-0 min-w-0 flex-1 overflow-hidden'
    : [mobilePane === 'artifact' ? 'flex' : 'hidden', 'min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background md:flex'].join(' ')

  return (
    <div className={[rootClassName, isSplit ? 'flex-col' : ''].join(' ')} data-layout={isSplit ? 'chat-artifact' : `${mode}-only`}>
      {isSplit ? (
        <div className="flex shrink-0 items-center gap-1 border-b bg-background p-1.5 md:hidden" role="tablist" aria-label="Workspace panes">
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
      ) : null}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden md:flex-row">
        <aside className={chatClassName}>
          {chat}
        </aside>
        <main className={artifactClassName}>
          {artifact}
        </main>
      </div>
    </div>
  )
}
