import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { ArrowUp, FolderOpen, ListTodo, UsersRound } from 'lucide-react'
import { useOutletContext, useSearchParams } from 'react-router'

import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import type { AskPageOutletContext } from './App'

type HomePageProps = {
  displayName?: string | null
}

const quickActions = [
  { label: 'What needs my attention?', prompt: 'What needs my attention today?', icon: ListTodo },
  { label: 'Which team can help?', prompt: 'Which team can help with an issue?', icon: UsersRound },
  { label: 'Summarize my projects', prompt: 'Summarize my projects and highlight anything important.', icon: FolderOpen },
]

export default function HomePage({ displayName }: HomePageProps) {
  const [searchParams] = useSearchParams()
  const { onSubmitHomeCommand } = useOutletContext<AskPageOutletContext>()
  const [command, setCommand] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const commandInputRef = useRef<HTMLTextAreaElement>(null)
  const greetingName = displayName?.trim() || 'there'
  const shouldFocusCommand = searchParams.get('focus') === 'search'

  useEffect(() => {
    if (shouldFocusCommand) {
      commandInputRef.current?.focus()
    }
  }, [shouldFocusCommand])

  async function submitCommand(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const prompt = command.trim()
    if (!prompt) {
      commandInputRef.current?.focus()
      return
    }
    if (isSubmitting) {
      return
    }
    setIsSubmitting(true)
    try {
      await onSubmitHomeCommand(prompt)
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleCommandKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  function selectSuggestedPrompt(prompt: string) {
    setCommand(prompt)
    commandInputRef.current?.focus()
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-auto bg-background">
      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-10 px-6 py-10 md:px-10 md:py-14">
        <section className="mx-auto w-full max-w-3xl space-y-2 text-center">
          <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">Good morning, {greetingName}</h1>
          <p className="text-muted-foreground">What would you like to accomplish?</p>
        </section>

        <form className="mx-auto w-full max-w-3xl" onSubmit={submitCommand}>
          <div className="rounded-md border bg-background p-2 transition-colors focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500/20">
            <Textarea
              ref={commandInputRef}
              value={command}
              onChange={(event) => setCommand(event.target.value)}
              rows={2}
              onKeyDown={handleCommandKeyDown}
              placeholder="Ask anything…"
              aria-label="Ask anything"
              disabled={isSubmitting}
              className="max-h-32 min-h-16 resize-none border-0 px-2.5 py-2 shadow-none focus-visible:border-0 focus-visible:ring-0"
            />
            <div className="flex justify-end pt-2">
              <Button type="submit" size="icon" aria-label="Submit command" disabled={!command.trim() || isSubmitting}>
                <ArrowUp className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </form>

        <section className="mx-auto w-full max-w-3xl space-y-3">
          <h2 className="text-sm font-medium text-muted-foreground">Try asking</h2>
          <div className="flex flex-wrap gap-2">
            {quickActions.map((action) => (
              <Button key={action.label} type="button" variant="outline" className="gap-2" onClick={() => selectSuggestedPrompt(action.prompt)}>
                <action.icon className="h-4 w-4 text-muted-foreground" />
                {action.label}
              </Button>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}
