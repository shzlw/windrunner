import { useEffect, useState } from 'react'
import { AlertTriangle, ArrowRight, Bell, CalendarClock, CircleAlert, Clock3, ListTodo, Loader2, Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getAttentionSummary, type AttentionSummary, type AttentionWorkItem, type UserNotification } from '@/lib/api'
import { translateStatus } from '@/i18n/labels'

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`)) : null
}

function WorkItemRow({ item, onOpen }: { item: AttentionWorkItem; onOpen?: (projectId: string, workItemId: string) => void }) {
  const { t } = useTranslation()
  return (
    <button type="button" className="block w-full px-3 py-2 text-left transition-colors hover:bg-muted/50 disabled:cursor-default" disabled={!onOpen} onClick={() => onOpen?.(item.projectId, item.workItemId)}>
      <span className="flex min-w-0 items-center gap-2">
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{item.title}</span>
        <Badge variant="outline" className="shrink-0 px-1.5 py-0 text-[10px]">{translateStatus(item.status, t)}</Badge>
      </span>
      <span className="mt-1 flex min-w-0 flex-wrap items-center gap-x-2 text-[11px] text-muted-foreground">
        <span className="max-w-48 truncate">{item.projectName}</span>
        {item.dueDate ? <span>{formatDate(item.dueDate)}</span> : null}
      </span>
    </button>
  )
}

function NotificationRow({ notification, onOpenWorkItem, onOpenProject }: {
  notification: UserNotification
  onOpenWorkItem?: (projectId: string, workItemId: string) => void
  onOpenProject?: (projectId: string) => void
}) {
  const canOpen = Boolean(notification.projectId && (notification.workItemId ? onOpenWorkItem : onOpenProject))
  return (
    <button
      type="button"
      className="block w-full px-3 py-2 text-left transition-colors hover:bg-muted/50 disabled:cursor-default"
      disabled={!canOpen}
      onClick={() => {
        if (!notification.projectId) return
        if (notification.workItemId) onOpenWorkItem?.(notification.projectId, notification.workItemId)
        else onOpenProject?.(notification.projectId)
      }}
    >
      <span className="block truncate text-sm font-medium">{notification.title}</span>
      <span className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{notification.message}</span>
    </button>
  )
}

export default function AttentionResults({ onOpenWorkItem, onOpenProject, onOpenMyWork }: {
  onOpenWorkItem?: (projectId: string, workItemId: string) => void
  onOpenProject?: (projectId: string) => void
  onOpenMyWork?: () => void
}) {
  const { t } = useTranslation()
  const [summary, setSummary] = useState<AttentionSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void getAttentionSummary()
      .then((result) => { if (!cancelled) setSummary(result) })
      .catch((loadError) => { if (!cancelled) setError(loadError instanceof Error ? loadError.message : t('attention.failedLoad')) })
    return () => { cancelled = true }
  }, [t])

  if (!summary && !error) {
    return <div className="flex min-h-20 items-center justify-center rounded-lg border bg-background sm:ml-11"><Loader2 className="size-4 animate-spin text-muted-foreground" /></div>
  }
  if (error) {
    return <div role="alert" className="flex items-center gap-2 rounded-lg border border-destructive/30 px-3 py-3 text-sm text-destructive sm:ml-11"><AlertTriangle className="size-4" />{error}</div>
  }
  if (!summary) return null

  const sections = [
    { key: 'overdue', label: t('attention.overdue'), icon: AlertTriangle, items: summary.overdueAssignments, more: summary.hasMoreOverdueAssignments },
    { key: 'blocked', label: t('attention.blocked'), icon: CircleAlert, items: summary.blockedAssignments, more: summary.hasMoreBlockedAssignments },
    { key: 'dueSoon', label: t('attention.dueSoon'), icon: CalendarClock, items: summary.dueSoonWork, more: summary.hasMoreDueSoonWork },
    { key: 'new', label: t('attention.newAssignments'), icon: Sparkles, items: summary.newAssignments, more: summary.hasMoreNewAssignments },
  ]
  const isEmpty = sections.every((section) => section.items.length === 0) && summary.importantUnreadNotifications.length === 0

  return (
    <section aria-label={t('attention.title')} className="overflow-hidden rounded-lg border bg-background sm:ml-11">
      <header className="flex items-center gap-2 border-b px-3 py-2.5">
        <ListTodo className="size-4 text-primary" />
        <h3 className="text-sm font-semibold">{t('attention.title')}</h3>
      </header>
      {isEmpty ? (
        <div className="px-3 py-6 text-center text-sm text-muted-foreground">{t('attention.caughtUp')}</div>
      ) : (
        <div className="divide-y">
          {sections.filter((section) => section.items.length > 0).map((section) => (
            <section key={section.key}>
              <h4 className="flex items-center gap-1.5 bg-muted/30 px-3 py-1.5 text-xs font-medium text-muted-foreground">
                <section.icon className="size-3.5" />{section.label}
                {section.more ? <span className="ml-auto">{t('attention.more')}</span> : null}
              </h4>
              <div className="divide-y">{section.items.map((item) => <WorkItemRow key={item.workItemId} item={item} onOpen={onOpenWorkItem} />)}</div>
            </section>
          ))}
          {summary.importantUnreadNotifications.length > 0 ? (
            <section>
              <h4 className="flex items-center gap-1.5 bg-muted/30 px-3 py-1.5 text-xs font-medium text-muted-foreground">
                <Bell className="size-3.5" />{t('attention.unreadNotifications')}
                {summary.hasMoreImportantUnreadNotifications ? <span className="ml-auto">{t('attention.more')}</span> : null}
              </h4>
              <div className="divide-y">{summary.importantUnreadNotifications.map((notification) => <NotificationRow key={notification.id} notification={notification} onOpenWorkItem={onOpenWorkItem} onOpenProject={onOpenProject} />)}</div>
            </section>
          ) : null}
        </div>
      )}
      {onOpenMyWork ? (
        <Button type="button" variant="ghost" size="sm" className="w-full justify-between rounded-none border-t px-3" onClick={onOpenMyWork}>
          <span className="inline-flex items-center gap-1.5"><Clock3 className="size-3.5" />{t('attention.openMyWork')}</span><ArrowRight className="size-3.5" />
        </Button>
      ) : null}
    </section>
  )
}
