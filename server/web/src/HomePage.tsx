import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowUp, CheckCircle2, FolderOpen, ListTodo, Search, UsersRound } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

type HomePageProps = {
  displayName?: string | null
}

const quickActions = [
  { label: 'What needs my attention?', path: '/app/my-work', icon: ListTodo },
  { label: 'Find a team', path: '/app/teams', icon: UsersRound },
  { label: 'Browse projects', path: '/app/projects', icon: FolderOpen },
]

export default function HomePage({ displayName }: HomePageProps) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [command, setCommand] = useState('')
  const commandInputRef = useRef<HTMLInputElement>(null)
  const greetingName = displayName?.trim() || 'there'
  const shouldFocusCommand = searchParams.get('focus') === 'search'

  useEffect(() => {
    if (shouldFocusCommand) {
      commandInputRef.current?.focus()
    }
  }, [shouldFocusCommand])

  function submitCommand(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const prompt = command.trim()
    if (!prompt) {
      commandInputRef.current?.focus()
      return
    }
    navigate(`/app/ask-ai?prompt=${encodeURIComponent(prompt)}`)
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-auto bg-background">
      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-10 px-6 py-10 md:px-10 md:py-14">
        <section className="mx-auto w-full max-w-3xl space-y-2 text-center">
          <Badge variant="outline" className="gap-1.5 font-normal text-muted-foreground">
            <CheckCircle2 className="h-3.5 w-3.5 text-primary" />
            AI workspace
          </Badge>
          <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">Good morning, {greetingName}</h1>
          <p className="text-muted-foreground">What would you like to accomplish?</p>
        </section>

        <form className="mx-auto w-full max-w-3xl" onSubmit={submitCommand}>
          <div className="flex items-center gap-2 rounded-xl border bg-background p-2 shadow-sm focus-within:ring-2 focus-within:ring-ring/30">
            <Search className="ml-3 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <Input
              ref={commandInputRef}
              value={command}
              onChange={(event) => setCommand(event.target.value)}
              placeholder="Ask, find, or create anything…"
              aria-label="Ask, find, or create anything"
              className="h-12 border-0 px-2 text-base shadow-none focus-visible:ring-0"
            />
            <Button type="submit" size="icon" aria-label="Submit command" disabled={!command.trim()}>
              <ArrowUp className="h-4 w-4" />
            </Button>
          </div>
        </form>

        <section className="mx-auto w-full max-w-3xl space-y-3">
          <h2 className="text-sm font-medium text-muted-foreground">Suggested</h2>
          <div className="flex flex-wrap gap-2">
            {quickActions.map((action) => (
              <Button key={action.label} type="button" variant="outline" className="gap-2" onClick={() => navigate(action.path)}>
                <action.icon className="h-4 w-4 text-muted-foreground" />
                {action.label}
              </Button>
            ))}
          </div>
        </section>

        <div className="grid gap-8 lg:grid-cols-2">
          <section className="min-w-0 space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">Needs your attention</h2>
              <Button type="button" variant="ghost" size="sm" onClick={() => navigate('/app/my-work')}>View My Work</Button>
            </div>
            <div className="divide-y rounded-md border bg-background">
              <button type="button" className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50" onClick={() => navigate('/app/my-work')}>
                <span className="h-2 w-2 shrink-0 rounded-full bg-destructive" aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">Authentication timeout</span>
                  <span className="block truncate text-xs text-muted-foreground">Project Alpha · overdue</span>
                </span>
              </button>
              <button type="button" className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50" onClick={() => navigate('/app/my-work')}>
                <span className="h-2 w-2 shrink-0 rounded-full bg-orange-500" aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">API migration</span>
                  <span className="block truncate text-xs text-muted-foreground">Platform project · blocked</span>
                </span>
              </button>
            </div>
          </section>

          <section className="min-w-0 space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">Recent</h2>
              <Button type="button" variant="ghost" size="sm" onClick={() => navigate('/app/projects')}>Browse</Button>
            </div>
            <div className="divide-y rounded-md border bg-background">
              <button type="button" className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50" onClick={() => navigate('/app/projects')}>
                <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">Project Alpha</span>
                  <span className="block truncate text-xs text-muted-foreground">Updated 12 minutes ago</span>
                </span>
              </button>
              <button type="button" className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50" onClick={() => navigate('/app/teams')}>
                <UsersRound className="h-4 w-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">Platform Team</span>
                  <span className="block truncate text-xs text-muted-foreground">Viewed yesterday</span>
                </span>
              </button>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
