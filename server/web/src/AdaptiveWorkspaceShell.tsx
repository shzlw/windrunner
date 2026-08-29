import type { ReactNode } from 'react'

type AdaptiveWorkspaceShellProps = {
  chat?: ReactNode
  artifact?: ReactNode
  className?: string
}

export default function AdaptiveWorkspaceShell({ chat, artifact, className }: AdaptiveWorkspaceShellProps) {
  const hasChat = chat !== undefined && chat !== null
  const hasArtifact = artifact !== undefined && artifact !== null

  if (hasChat && hasArtifact) {
    return (
      <div className={['flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background md:flex-row', className ?? ''].join(' ')} data-layout="chat-artifact">
        <aside className="min-h-0 min-w-0 shrink-0 overflow-hidden border-b bg-background md:w-[38%] md:max-w-[32rem] md:border-r md:border-b-0">
          {chat}
        </aside>
        <main className="min-h-0 min-w-0 flex-1 overflow-hidden bg-background">
          {artifact}
        </main>
      </div>
    )
  }

  if (hasChat) {
    return (
      <div className={['flex min-h-0 min-w-0 flex-1 overflow-hidden bg-background', className ?? ''].join(' ')} data-layout="chat-only">
        <main className="mx-auto flex min-h-0 min-w-0 w-full max-w-4xl flex-1 flex-col overflow-hidden">
          {chat}
        </main>
      </div>
    )
  }

  if (hasArtifact) {
    return (
      <div className={['flex min-h-0 min-w-0 flex-1 overflow-hidden bg-background', className ?? ''].join(' ')} data-layout="artifact-only">
        <main className="min-h-0 min-w-0 flex-1 overflow-hidden">
          {artifact}
        </main>
      </div>
    )
  }

  return null
}
