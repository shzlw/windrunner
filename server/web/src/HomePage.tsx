import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { ArrowUp, FolderOpen, ListTodo, Loader2, Mic, Square, UsersRound, X } from 'lucide-react'
import { useOutletContext, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import VoiceWaveform from '@/components/VoiceWaveform'
import useVoiceTranscription, { formatRecordingTime } from '@/hooks/use-voice-transcription'
import type { AskPageOutletContext } from './App'

type HomePageProps = {
  displayName?: string | null
}

const quickActions = [
  { labelKey: 'home.attention', promptKey: 'home.attentionPrompt', icon: ListTodo },
  { labelKey: 'home.teamHelp', promptKey: 'home.teamHelpPrompt', icon: UsersRound },
  { labelKey: 'home.summarizeProjects', promptKey: 'home.summarizeProjectsPrompt', icon: FolderOpen },
]

export default function HomePage({ displayName }: HomePageProps) {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const { onSubmitHomeCommand } = useOutletContext<AskPageOutletContext>()
  const [command, setCommand] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const commandInputRef = useRef<HTMLTextAreaElement>(null)
  const greetingName = displayName?.trim() || t('common.there')
  const shouldFocusCommand = searchParams.get('focus') === 'search'
  const voiceTranscription = useVoiceTranscription({
    value: command,
    onValueChange: setCommand,
    inputRef: commandInputRef,
    disabled: isSubmitting,
  })

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
          <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">{t('home.greeting', { name: greetingName })}</h1>
          <p className="text-muted-foreground">{t('home.subtitle')}</p>
        </section>

        <form className="mx-auto w-full max-w-3xl" onSubmit={submitCommand}>
          <div className="rounded-md border bg-background p-2 transition-colors focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500/20">
            <Textarea
              ref={commandInputRef}
              value={command}
              onChange={(event) => setCommand(event.target.value)}
              rows={2}
              onKeyDown={handleCommandKeyDown}
              placeholder={t('home.askAnything')}
              aria-label={t('home.askAnythingLabel')}
              disabled={isSubmitting || voiceTranscription.isBusy}
              className="max-h-32 min-h-16 resize-none border-0 px-2.5 py-2 shadow-none focus-visible:border-0 focus-visible:ring-0"
            />
            <div className="flex items-center justify-between gap-2 pt-2">
              <div className="flex min-w-0 items-center gap-1">
                {voiceTranscription.isRecording ? (
                  <div className="flex items-center gap-2 px-1 text-xs text-destructive" aria-live="polite">
                    <VoiceWaveform levels={voiceTranscription.waveformLevels} />
                    <span>{formatRecordingTime(voiceTranscription.recordingSeconds)}</span>
                  </div>
                ) : voiceTranscription.isTranscribing ? (
                  <div className="flex items-center gap-2 px-1 text-xs text-muted-foreground" aria-live="polite">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span>{t('chat.transcribing')}</span>
                  </div>
                ) : null}
              </div>
              <div className="flex items-center gap-1">
                {voiceTranscription.isRecording ? (
                  <>
                    <Button type="button" size="icon" variant="ghost" onClick={voiceTranscription.cancelRecording} aria-label={t('chat.cancelRecording')} title={t('chat.cancelRecording')}>
                      <X className="h-4 w-4" />
                    </Button>
                    <Button type="button" size="icon" variant="outline" onClick={voiceTranscription.stopRecording} aria-label={t('chat.stopRecording')} title={t('chat.stopRecording')}>
                      <Square className="h-4 w-4" />
                    </Button>
                  </>
                ) : (
                  <>
                    {voiceTranscription.available && voiceTranscription.voiceInputSupported && !voiceTranscription.isTranscribing ? (
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        onClick={() => void voiceTranscription.startRecording()}
                        disabled={isSubmitting || voiceTranscription.isBusy}
                        aria-label={t('chat.recordVoice')}
                        title={t('chat.recordVoice')}
                      >
                        <Mic className="h-4 w-4" />
                      </Button>
                    ) : null}
                    <Button type="submit" size="icon" aria-label={t('home.submitCommand')} disabled={!command.trim() || isSubmitting || voiceTranscription.isBusy}>
                      <ArrowUp className="h-4 w-4" />
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
          {voiceTranscription.transcriptionError ? <div className="mt-2 text-xs text-destructive" role="alert">{voiceTranscription.transcriptionError}</div> : null}
          {voiceTranscription.microphonePermission === 'denied' && !voiceTranscription.transcriptionError ? <div className="mt-2 text-xs text-destructive" role="alert">{t('chat.microphonePermissionDenied')}</div> : null}
        </form>

        <section className="mx-auto w-full max-w-3xl space-y-3">
          <h2 className="text-sm font-medium text-muted-foreground">{t('home.tryAsking')}</h2>
          <div className="flex flex-wrap gap-2">
            {quickActions.map((action) => (
              <Button key={action.labelKey} type="button" variant="outline" className="gap-2" onClick={() => selectSuggestedPrompt(t(action.promptKey))}>
                <action.icon className="h-4 w-4 text-muted-foreground" />
                {t(action.labelKey)}
              </Button>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}
