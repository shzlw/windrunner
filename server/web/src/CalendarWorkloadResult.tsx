import { useEffect, useState } from 'react'
import { AlertTriangle, ArrowRight, CalendarDays, CircleAlert, CircleDashed, ClipboardList, Loader2, UserRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getCalendarWorkload, type CalendarWorkload, type CalendarWorkloadWorkItem } from '@/lib/api'

const MAX_VISIBLE_MEMBERS = 10

function formatHours(minutes: number) {
  const hours = minutes / 60
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(hours)
}

function WorkItemLink({ item, onOpen }: { item: CalendarWorkloadWorkItem; onOpen?: (projectId: string, workItemId: string) => void }) {
  return (
    <button type="button" className="max-w-48 truncate rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground hover:text-foreground disabled:cursor-default" disabled={!onOpen} onClick={() => onOpen?.(item.projectId, item.workItemId)} title={`${item.projectName}: ${item.title}`}>
      {item.title}
    </button>
  )
}

export default function CalendarWorkloadResult({ teamId, from, to, onOpenWorkItem, onOpenCalendar }: {
  teamId: string
  from: string
  to: string
  onOpenWorkItem?: (projectId: string, workItemId: string) => void
  onOpenCalendar?: (teamId: string, from: string, to: string) => void
}) {
  const { t } = useTranslation()
  const [workload, setWorkload] = useState<CalendarWorkload | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void getCalendarWorkload(teamId, from, to)
      .then((result) => { if (!cancelled) setWorkload(result) })
      .catch((loadError) => { if (!cancelled) setError(loadError instanceof Error ? loadError.message : t('calendarWorkload.failedLoad')) })
    return () => { cancelled = true }
  }, [from, t, teamId, to])

  if (!workload && !error) {
    return <div className="flex min-h-20 items-center justify-center rounded-lg border bg-background sm:ml-11"><Loader2 className="size-4 animate-spin text-muted-foreground" /></div>
  }
  if (error) {
    return <div role="alert" className="flex items-center gap-2 rounded-lg border border-destructive/30 px-3 py-3 text-sm text-destructive sm:ml-11"><AlertTriangle className="size-4" />{error}</div>
  }
  if (!workload) return null

  const visibleMembers = workload.members.slice(0, MAX_VISIBLE_MEMBERS)
  return (
    <section aria-label={t('calendarWorkload.title')} className="overflow-hidden rounded-lg border bg-background sm:ml-11">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b px-3 py-2.5">
        <span className="inline-flex min-w-0 items-center gap-2"><CalendarDays className="size-4 text-primary" /><span className="truncate text-sm font-semibold">{workload.teamName}</span></span>
        <span className="text-xs text-muted-foreground">{workload.from} – {workload.to}</span>
      </header>
      {visibleMembers.length === 0 ? <div className="px-3 py-5 text-center text-sm text-muted-foreground">{t('calendarWorkload.noMembers')}</div> : (
        <div className="divide-y">
          {visibleMembers.map((member) => (
            <div key={member.userId} className="px-3 py-2.5">
              <div className="flex min-w-0 items-center gap-2">
                <UserRound className="size-3.5 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{member.displayName}</span>
              </div>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                <Badge variant="outline" className="gap-1 px-1.5 py-0 text-[10px]"><ClipboardList className="size-3" />{t('calendarWorkload.scheduled', { count: member.scheduledWorkItemCount })}</Badge>
                <Badge variant="outline" className="gap-1 px-1.5 py-0 text-[10px]"><CalendarDays className="size-3" />{t('calendarWorkload.events', { count: member.calendarEventCount, hours: formatHours(member.calendarEventMinutes) })}</Badge>
                {member.conflictCount > 0 ? <Badge variant="outline" className="gap-1 border-amber-300 px-1.5 py-0 text-[10px] text-amber-700 dark:text-amber-300"><CircleAlert className="size-3" />{t('calendarWorkload.conflicts', { count: member.conflictCount })}</Badge> : null}
                <Badge variant="outline" className="gap-1 px-1.5 py-0 text-[10px]"><CircleDashed className="size-3" />{t('calendarWorkload.unplanned', { count: member.unplannedWorkItemCount })}</Badge>
              </div>
              {(member.scheduledWorkItems.length > 0 || member.unplannedWorkItems.length > 0) ? (
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {[...member.scheduledWorkItems, ...member.unplannedWorkItems].slice(0, 3).map((item) => <WorkItemLink key={item.workItemId} item={item} onOpen={onOpenWorkItem} />)}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      )}
      {workload.teamAssignedWorkItemCount > 0 ? (
        <div className="border-t bg-muted/20 px-3 py-2.5">
          <p className="text-xs font-medium">{t('calendarWorkload.teamAssigned', { count: workload.teamAssignedWorkItemCount })}</p>
          <div className="mt-1.5 flex flex-wrap gap-1">{workload.teamAssignedWorkItems.map((item) => <WorkItemLink key={item.workItemId} item={item} onOpen={onOpenWorkItem} />)}</div>
        </div>
      ) : null}
      {workload.workItemsTruncated ? (
        <div className="flex items-start gap-2 border-t bg-amber-50/60 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950/20 dark:text-amber-200">
          <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
          <span>{t('calendarWorkload.workItemsTruncated')}</span>
        </div>
      ) : null}
      {(onOpenCalendar || workload.hasMoreMembers || workload.members.length > visibleMembers.length) ? (
        <Button type="button" variant="ghost" size="sm" className="w-full justify-between rounded-none border-t px-3" disabled={!onOpenCalendar} onClick={() => onOpenCalendar?.(teamId, from, to)}>
          <span>{t('calendarWorkload.openCalendar')}</span>
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            {workload.hasMoreMembers || workload.members.length > visibleMembers.length ? t('calendarWorkload.morePeople') : null}<ArrowRight className="size-3.5" />
          </span>
        </Button>
      ) : null}
    </section>
  )
}
