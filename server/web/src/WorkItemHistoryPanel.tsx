import { useCallback, useEffect, useState } from 'react'
import { CalendarClock, CheckCircle2, ChevronDown, ChevronRight, CircleAlert, CircleDot, FileClock, History, Loader2, PauseCircle, PlayCircle, RotateCcw, UsersRound, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { listWorkItemAuditLogs, listWorkItemTimeline, type AuditLog, type WorkItemTimelineEvent } from '@/lib/api'
import { cn } from '@/lib/utils'
import { translateAuditAction } from '@/i18n/labels'

type ChangeMap = Record<string, { from?: unknown; to?: unknown }>

const entityTypeKeys: Record<string, string> = {
  WORK_ITEM: 'common.workItem',
  ENTRY: 'common.entry',
  RELATIONSHIP: 'common.relationship',
}

function actionDotClass(action: string) {
  if (action === 'DELETE') {
    return 'bg-red-500'
  }
  if (action === 'CREATE') {
    return 'bg-emerald-500'
  }
  return 'bg-sky-500'
}

function timeAgo(value: string, t: TFunction) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 60) return t('history.justNow')
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return t('history.minutesAgo', { count: minutes })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('history.hoursAgo', { count: hours })
  const days = Math.floor(hours / 24)
  if (days < 30) return t('history.daysAgo', { count: days })
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
}

function formatTimestamp(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}

function formatStatus(value: string | null) {
  return value ? value.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, (character) => character.toUpperCase()) : ''
}

function formatChangeValue(value: unknown, t: TFunction) {
  if (value === null || value === undefined || value === '') {
    return t('common.notSet')
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function parseChanges(changesJson: string | null): ChangeMap | null {
  if (!changesJson) {
    return null
  }

  try {
    const parsed = JSON.parse(changesJson)
    if (parsed && typeof parsed === 'object' && Object.keys(parsed).length > 0) {
      return parsed as ChangeMap
    }
    return null
  } catch {
    return null
  }
}

function formatJson(value: string | null) {
  if (!value) {
    return null
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const pageSize = 20

export default function WorkItemHistoryPanel({
  projectId,
  workItemId,
  userLabels,
  teamLabels,
}: {
  projectId: string
  workItemId: string
  userLabels: Map<string, string>
  teamLabels: Map<string, string>
}) {
  const { t } = useTranslation()
  const [historyView, setHistoryView] = useState<'timeline' | 'activity'>('timeline')
  const [items, setItems] = useState<AuditLog[]>([])
  const [timeline, setTimeline] = useState<WorkItemTimelineEvent[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [isTimelineLoading, setIsTimelineLoading] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set())

  const loadPage = useCallback(
    async (nextPage: number, append: boolean) => {
      if (append) {
        setIsLoadingMore(true)
      } else {
        setIsLoading(true)
      }

      try {
        const data = await listWorkItemAuditLogs(projectId, workItemId, nextPage, pageSize)
        setItems((current) => (append ? [...current, ...data.items] : data.items))
        setPage(data.page)
        setTotalPages(data.totalPages)
      } catch (loadError) {
        toast.error(loadError instanceof Error ? loadError.message : t('history.failedLoad'))
        if (!append) {
          setItems([])
          setTotalPages(0)
        }
      } finally {
        setIsLoading(false)
        setIsLoadingMore(false)
      }
    },
    [projectId, workItemId],
  )

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage(0, false)
    })
  }, [loadPage])

  useEffect(() => {
    let cancelled = false
    setIsTimelineLoading(true)
    void listWorkItemTimeline(projectId, workItemId)
      .then((data) => {
        if (!cancelled) setTimeline(data)
      })
      .catch((loadError) => {
        if (!cancelled) {
          setTimeline([])
          toast.error(loadError instanceof Error ? loadError.message : t('history.failedLoad'))
        }
      })
      .finally(() => {
        if (!cancelled) setIsTimelineLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [projectId, workItemId, t])

  function toggleExpanded(id: string) {
    setExpandedIds((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function actorLabel(entry: AuditLog) {
    if (!entry.actorUserId) {
      return t('audit.system')
    }
    return entry.actorDisplayName?.trim() || userLabels.get(entry.actorUserId) || entry.actorUserId
  }

  function entityLabel(entry: AuditLog) {
    return entityTypeKeys[entry.entityType] ? t(entityTypeKeys[entry.entityType]) : entry.entityType
  }

  function timelineActorLabel(event: WorkItemTimelineEvent) {
    return event.actorUserId ? userLabels.get(event.actorUserId) || event.actorUserId : t('audit.system')
  }

  function timelineAssigneeLabel(assignee: WorkItemTimelineEvent['addedAssignees'][number]) {
    const labels = assignee.assigneeType === 'TEAM' ? teamLabels : userLabels
    return labels.get(assignee.assigneeId) || assignee.assigneeId
  }

  function timelineAssigneeList(assignees: WorkItemTimelineEvent['addedAssignees']) {
    return assignees.map(timelineAssigneeLabel).join(', ')
  }

  function timelineIcon(kind: WorkItemTimelineEvent['kind']) {
    if (kind === 'CREATED') return <CircleDot className="h-4 w-4" />
    if (kind === 'ASSIGNMENT_CHANGED') return <UsersRound className="h-4 w-4" />
    if (kind === 'STARTED' || kind === 'RESUMED') return <PlayCircle className="h-4 w-4" />
    if (kind === 'PAUSED') return <PauseCircle className="h-4 w-4" />
    if (kind === 'COMPLETED') return <CheckCircle2 className="h-4 w-4" />
    if (kind === 'REOPENED') return <RotateCcw className="h-4 w-4" />
    if (kind === 'DUE_DATE_CHANGED') return <CalendarClock className="h-4 w-4" />
    if (kind === 'DELETED' || kind === 'CLOSED') return <XCircle className="h-4 w-4" />
    return <CircleAlert className="h-4 w-4" />
  }

  function timelineIconClass(kind: WorkItemTimelineEvent['kind']) {
    if (kind === 'COMPLETED') return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
    if (kind === 'PAUSED') return 'bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'
    if (kind === 'DELETED' || kind === 'CLOSED') return 'bg-red-100 text-red-700 dark:bg-red-950/40 dark:text-red-300'
    return 'bg-muted text-muted-foreground'
  }

  function renderTimelineEvent(event: WorkItemTimelineEvent) {
    const actor = timelineActorLabel(event)
    const added = timelineAssigneeList(event.addedAssignees)
    const removed = timelineAssigneeList(event.removedAssignees)
    const status = formatStatus(event.toStatus)
    return (
      <li key={event.id} className="relative pl-8 pb-4 last:pb-0">
        <span className={cn('absolute left-0 top-0 flex h-6 w-6 items-center justify-center rounded-full', timelineIconClass(event.kind))}>
          {timelineIcon(event.kind)}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
            <p className="text-sm">
              <span className="font-medium">{actor}</span>{' '}
              <span className="text-muted-foreground">
                {event.kind === 'CREATED' ? t('history.timelineCreated') : null}
                {event.kind === 'ASSIGNMENT_CHANGED' ? t('history.timelineAssignmentChanged') : null}
                {event.kind === 'STARTED' ? t('history.timelineStarted') : null}
                {event.kind === 'PAUSED' ? t('history.timelinePaused', { status }) : null}
                {event.kind === 'RESUMED' ? t('history.timelineResumed') : null}
                {event.kind === 'COMPLETED' ? t('history.timelineCompleted', { status }) : null}
                {event.kind === 'CLOSED' ? t('history.timelineClosed', { status }) : null}
                {event.kind === 'REOPENED' ? t('history.timelineReopened') : null}
                {event.kind === 'STATUS_CHANGED' ? t('history.timelineStatusChanged', { status }) : null}
                {event.kind === 'DUE_DATE_CHANGED' ? t('history.timelineDueDateChanged') : null}
                {event.kind === 'DELETED' ? t('history.timelineDeleted') : null}
              </span>
            </p>
            <time className="shrink-0 text-xs text-muted-foreground" title={formatTimestamp(event.occurredAt)}>
              {timeAgo(event.occurredAt, t)}
            </time>
          </div>
          {event.kind === 'CREATED' && event.toStatus ? (
            <p className="mt-1 text-xs text-muted-foreground">{t('history.timelineInitialStatus', { status: formatStatus(event.toStatus) })}</p>
          ) : null}
          {((event.kind === 'ASSIGNMENT_CHANGED' || event.kind === 'CREATED') && (added || removed)) ? (
            <div className="mt-1 space-y-0.5 text-xs text-muted-foreground">
              {added ? <p><span className="font-medium text-emerald-700 dark:text-emerald-300">{t('history.timelineAssigned')}</span> {added}</p> : null}
              {removed ? <p><span className="font-medium text-rose-700 dark:text-rose-300">{t('history.timelineUnassigned')}</span> {removed}</p> : null}
            </div>
          ) : null}
          {event.kind === 'DUE_DATE_CHANGED' ? (
            <p className="mt-1 text-xs text-muted-foreground">
              {event.toDueDate ? t('history.timelineDueDateSet', { date: formatDateOnly(event.toDueDate) }) : t('history.timelineDueDateCleared')}
            </p>
          ) : null}
        </div>
      </li>
    )
  }

  function renderActivity() {
    if (isLoading) {
      return <div className="flex min-h-40 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
    }
    if (items.length === 0) {
      return (
        <Empty className="border-0">
          <EmptyHeader>
            <EmptyMedia variant="icon"><FileClock /></EmptyMedia>
            <EmptyTitle>{t('history.noHistory')}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      )
    }
    return (
      <div className="px-3 py-2">
        <ol className="relative space-y-1 border-l pl-4">
          {items.map((entry) => {
            const changes = parseChanges(entry.changesJson)
            const isExpanded = expandedIds.has(entry.id)
            const hasDetails = Boolean(changes) || Boolean(formatJson(entry.metadataJson)) || Boolean(formatJson(entry.beforeJson)) || Boolean(formatJson(entry.afterJson))
            return (
              <li key={entry.id} className="relative">
                <span
                  className={cn('absolute top-[7px] -left-[21px] h-2 w-2 rounded-full border border-background', actionDotClass(entry.action))}
                  title={`${translateAuditAction(entry.action, t)} · ${entityLabel(entry)}`}
                />
                <div
                  className={cn('group flex items-baseline justify-between gap-2 rounded-sm py-1', hasDetails && 'cursor-pointer')}
                  onClick={hasDetails ? () => toggleExpanded(entry.id) : undefined}
                >
                  <p className="min-w-0 flex-1 truncate text-xs" title={entry.summary}>
                    <span className="font-medium">{actorLabel(entry)}</span>{' '}
                    <span className="text-muted-foreground">{entry.summary}</span>
                  </p>
                  {hasDetails ? (
                    isExpanded ? <ChevronDown className="h-3.5 w-3.5 shrink-0 self-center text-muted-foreground" /> : <ChevronRight className="h-3.5 w-3.5 shrink-0 self-center text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
                  ) : null}
                  <time className="shrink-0 text-xs whitespace-nowrap text-muted-foreground" title={formatTimestamp(entry.occurredAt)}>
                    {timeAgo(entry.occurredAt, t)}
                  </time>
                </div>
                {hasDetails && isExpanded ? (
                  <div className="mt-1 mb-2 space-y-2 rounded-md border bg-muted/30 p-2">
                    {changes ? (
                      <div className="space-y-1">
                        {Object.entries(changes).map(([field, change]) => (
                          <div key={field} className="text-xs">
                            <span className="font-medium">{field}</span>{' '}
                            <span className="text-muted-foreground">{formatChangeValue(change.from, t)} → {formatChangeValue(change.to, t)}</span>
                          </div>
                        ))}
                      </div>
                    ) : null}
                    {[
                      [t('history.metadata'), formatJson(entry.metadataJson)],
                      [t('history.before'), formatJson(entry.beforeJson)],
                      [t('history.after'), formatJson(entry.afterJson)],
                    ]
                      .filter((section): section is [string, string] => Boolean(section[1]))
                      .map(([label, json]) => (
                        <div key={label} className="space-y-1">
                          <span className="text-xs font-medium text-muted-foreground">{label}</span>
                          <pre className="max-h-48 overflow-auto rounded-sm bg-background p-2 font-mono text-[11px] leading-relaxed">{json}</pre>
                        </div>
                      ))}
                  </div>
                ) : null}
              </li>
            )
          })}
        </ol>
        {page < totalPages - 1 ? (
          <div className="flex justify-center pt-3 pb-1">
            <Button type="button" variant="outline" size="sm" disabled={isLoadingMore} onClick={() => void loadPage(page + 1, true)}>
              {isLoadingMore ? <Loader2 className="h-4 w-4 animate-spin" /> : <History className="h-4 w-4" />}
              {t('common.loadMore')}
            </Button>
          </div>
        ) : null}
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <Tabs value={historyView} onValueChange={(value) => setHistoryView(value as 'timeline' | 'activity')} className="h-full min-h-0 gap-0">
        <TabsList variant="line" className="w-full shrink-0 justify-start border-b px-3">
          <TabsTrigger value="timeline" className="flex-none px-2">{t('history.timeline')}</TabsTrigger>
          <TabsTrigger value="activity" className="flex-none px-2">{t('history.activity')}</TabsTrigger>
        </TabsList>
        <TabsContent value="timeline" className="mt-0 min-h-0 overflow-auto">
          {isTimelineLoading ? (
            <div className="flex min-h-40 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
          ) : timeline.length === 0 ? (
            <Empty className="border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon"><History /></EmptyMedia>
                <EmptyTitle>{t('history.timelineEmpty')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="px-4 py-4">
              <ol className="relative border-l pl-3">{timeline.map(renderTimelineEvent)}</ol>
            </div>
          )}
        </TabsContent>
        <TabsContent value="activity" className="mt-0 min-h-0 overflow-auto">
          {renderActivity()}
        </TabsContent>
      </Tabs>
    </div>
  )
}
