import { useEffect, useState } from 'react'
import { ArrowRight, CalendarClock, CheckCircle2, CircleAlert, CircleDot, Loader2, PauseCircle, PlayCircle, RotateCcw, UsersRound, XCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { translateStatus } from '@/i18n/labels'
import { listWorkItemTimeline, type WorkItemTimelineEvent } from '@/lib/api'
import { cn } from '@/lib/utils'

const MAX_EVENTS = 6

function eventIcon(kind: WorkItemTimelineEvent['kind']) {
  if (kind === 'CREATED') return CircleDot
  if (kind === 'ASSIGNMENT_CHANGED') return UsersRound
  if (kind === 'STARTED' || kind === 'RESUMED') return PlayCircle
  if (kind === 'PAUSED') return PauseCircle
  if (kind === 'COMPLETED') return CheckCircle2
  if (kind === 'REOPENED') return RotateCcw
  if (kind === 'DUE_DATE_CHANGED') return CalendarClock
  if (kind === 'DELETED' || kind === 'CLOSED') return XCircle
  return CircleAlert
}

function eventTone(kind: WorkItemTimelineEvent['kind']) {
  if (kind === 'COMPLETED') return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
  if (kind === 'PAUSED') return 'bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'
  if (kind === 'DELETED' || kind === 'CLOSED') return 'bg-red-100 text-red-700 dark:bg-red-950/40 dark:text-red-300'
  return 'bg-muted text-muted-foreground'
}

export default function WorkItemTimelineResult({ projectId, workItemId, userNames, teamNames, onOpenFullHistory }: {
  projectId: string
  workItemId: string
  userNames: Map<string, string>
  teamNames: Map<string, string>
  onOpenFullHistory?: (projectId: string, workItemId: string) => void
}) {
  const { t } = useTranslation()
  const [events, setEvents] = useState<WorkItemTimelineEvent[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void listWorkItemTimeline(projectId, workItemId)
      .then((result) => { if (!cancelled) setEvents(result.slice(-MAX_EVENTS).reverse()) })
      .catch((loadError) => { if (!cancelled) setError(loadError instanceof Error ? loadError.message : t('history.failedLoad')) })
    return () => { cancelled = true }
  }, [projectId, t, workItemId])

  function assigneeLabels(event: WorkItemTimelineEvent, field: 'addedAssignees' | 'removedAssignees') {
    return event[field].map((assignee) => assignee.assigneeType === 'TEAM'
      ? teamNames.get(assignee.assigneeId) ?? t('common.unknownTeam')
      : userNames.get(assignee.assigneeId) ?? t('common.unknownUser')).join(', ')
  }

  function description(event: WorkItemTimelineEvent) {
    const status = event.toStatus ? translateStatus(event.toStatus, t) : ''
    if (event.kind === 'CREATED') return t('history.timelineCreated')
    if (event.kind === 'ASSIGNMENT_CHANGED') return t('history.timelineAssignmentChanged')
    if (event.kind === 'STARTED') return t('history.timelineStarted')
    if (event.kind === 'PAUSED') return t('history.timelinePaused', { status })
    if (event.kind === 'RESUMED') return t('history.timelineResumed')
    if (event.kind === 'COMPLETED') return t('history.timelineCompleted', { status })
    if (event.kind === 'CLOSED') return t('history.timelineClosed', { status })
    if (event.kind === 'REOPENED') return t('history.timelineReopened')
    if (event.kind === 'DUE_DATE_CHANGED') return t('history.timelineDueDateChanged')
    if (event.kind === 'DELETED') return t('history.timelineDeleted')
    return t('history.timelineStatusChanged', { status })
  }

  return (
    <section aria-label={t('history.timeline')} className="overflow-hidden rounded-lg border bg-background sm:ml-11">
      <header className="border-b px-3 py-2.5 text-sm font-semibold">{t('timelineResult.title')}</header>
      {!events && !error ? <div className="flex min-h-20 items-center justify-center"><Loader2 className="size-4 animate-spin text-muted-foreground" /></div> : null}
      {error ? <div role="alert" className="px-3 py-3 text-sm text-destructive">{error}</div> : null}
      {events?.length === 0 ? <div className="px-3 py-5 text-center text-sm text-muted-foreground">{t('history.timelineEmpty')}</div> : null}
      {events && events.length > 0 ? (
        <ol className="px-3 py-2">
          {events.map((event, index) => {
            const Icon = eventIcon(event.kind)
            const actor = event.actorUserId ? userNames.get(event.actorUserId) ?? t('common.unknownUser') : t('audit.system')
            const added = assigneeLabels(event, 'addedAssignees')
            const removed = assigneeLabels(event, 'removedAssignees')
            return (
              <li key={event.id} className="relative flex gap-3 pb-3 last:pb-1">
                {index < events.length - 1 ? <span className="absolute bottom-0 left-3 top-6 border-l" /> : null}
                <span className={cn('relative z-10 flex size-6 shrink-0 items-center justify-center rounded-full', eventTone(event.kind))}><Icon className="size-3.5" /></span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm"><span className="font-medium">{actor}</span> <span className="text-muted-foreground">{description(event)}</span></p>
                  {added ? <p className="mt-0.5 text-xs text-muted-foreground">{t('history.timelineAssigned')} {added}</p> : null}
                  {removed ? <p className="mt-0.5 text-xs text-muted-foreground">{t('history.timelineUnassigned')} {removed}</p> : null}
                  {event.kind === 'DUE_DATE_CHANGED' ? <p className="mt-0.5 text-xs text-muted-foreground">{event.toDueDate ? t('history.timelineDueDateSet', { date: event.toDueDate }) : t('history.timelineDueDateCleared')}</p> : null}
                  <time className="text-[11px] text-muted-foreground" dateTime={event.occurredAt}>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(event.occurredAt))}</time>
                </div>
              </li>
            )
          })}
        </ol>
      ) : null}
      {onOpenFullHistory ? (
        <Button type="button" variant="ghost" size="sm" className="w-full justify-between rounded-none border-t px-3" onClick={() => onOpenFullHistory(projectId, workItemId)}>
          {t('timelineResult.openFullHistory')}<ArrowRight className="size-3.5" />
        </Button>
      ) : null}
    </section>
  )
}
