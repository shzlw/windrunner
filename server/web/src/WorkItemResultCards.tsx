import { ArrowRight, CalendarDays, CircleAlert, UserRound, UsersRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { translatePriority, translateStatus, translateWorkItemType } from '@/i18n/labels'
import type { WorkItemAssignee } from '@/lib/api'
import { workItemTypeBadgeClass } from '@/lib/typeBadges'
import { cn } from '@/lib/utils'

const MAX_VISIBLE_RESULTS = 5

export type ChatWorkItemReference = {
  id: string
  title: string
  type: string
  status: string
  priority?: string | null
  dueDate?: string | null
  projectId?: string
  assignees?: WorkItemAssignee[]
  blocked?: boolean
}

function statusBadgeClass(status: string) {
  const normalized = status.trim().toUpperCase().replaceAll(' ', '_')
  if (normalized === 'BLOCKED') return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300'
  if (normalized === 'DONE') return 'border-green-200 bg-green-50 text-green-700 dark:border-green-900 dark:bg-green-950/40 dark:text-green-300'
  if (normalized === 'IN_PROGRESS') return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-300'
  return 'border-border bg-muted/50 text-muted-foreground'
}

function formatDueDate(value: string | null | undefined, fallback: string) {
  if (!value) return fallback
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
}

export function WorkItemResultCards({
  references,
  projectNames,
  userNames,
  teamNames,
  onOpenWorkItem,
  onOpenProject,
  onViewAll,
}: {
  references: ChatWorkItemReference[]
  projectNames: Map<string, string>
  userNames: Map<string, string>
  teamNames: Map<string, string>
  onOpenWorkItem?: (workItemId: string) => void
  onOpenProject?: (projectId: string) => void
  onViewAll?: () => void
}) {
  const { t } = useTranslation()
  const visible = references.slice(0, MAX_VISIBLE_RESULTS)
  const projectIds = [...new Set(references.map((reference) => reference.projectId).filter((id): id is string => Boolean(id)))]
  const crossesProjects = projectIds.length > 1
  const hiddenCount = Math.max(0, references.length - visible.length)
  const footerAction = crossesProjects || projectIds.length !== 1
    ? onViewAll
    : onOpenProject
      ? () => onOpenProject(projectIds[0])
      : undefined
  const footerLabel = crossesProjects || projectIds.length !== 1
    ? t('chat.viewAllMyWork')
    : t('chat.openProject')

  return (
    <section aria-label={t('chat.workItemResults')} className="overflow-hidden rounded-lg border bg-background sm:ml-11">
      <div className="divide-y">
        {visible.map((reference) => {
          const assignees = reference.assignees ?? []
          return (
            <button
              key={reference.id}
              type="button"
              className="block w-full px-3 py-2.5 text-left transition-colors hover:bg-muted/50 disabled:cursor-default disabled:hover:bg-transparent"
              disabled={!onOpenWorkItem}
              onClick={() => onOpenWorkItem?.(reference.id)}
            >
              <span className="flex min-w-0 items-center gap-2">
                <Badge variant="outline" className={cn('shrink-0 px-1.5 py-0 text-[10px] font-medium uppercase', workItemTypeBadgeClass(reference.type))}>
                  {translateWorkItemType(reference.type, t)}
                </Badge>
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{reference.title}</span>
                <Badge variant="outline" className={cn('shrink-0 px-1.5 py-0 text-[10px]', statusBadgeClass(reference.status))}>
                  {translateStatus(reference.status, t)}
                </Badge>
              </span>

              <span className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                <span>{reference.priority ? translatePriority(reference.priority, t) : t('common.noPriority')}</span>
                <span className="inline-flex items-center gap-1">
                  <CalendarDays className="size-3" aria-hidden="true" />
                  {formatDueDate(reference.dueDate, t('common.noDueDate'))}
                </span>
                {assignees.length > 0 ? assignees.slice(0, 3).map((assignee) => (
                  <span key={`${assignee.assigneeType}:${assignee.assigneeId}`} className="inline-flex min-w-0 items-center gap-1">
                    {assignee.assigneeType === 'TEAM'
                      ? <UsersRound className="size-3 shrink-0" aria-hidden="true" />
                      : <UserRound className="size-3 shrink-0" aria-hidden="true" />}
                    <span className="max-w-32 truncate">
                      {assignee.assigneeType === 'TEAM'
                        ? teamNames.get(assignee.assigneeId) ?? t('common.unknownTeam')
                        : userNames.get(assignee.assigneeId) ?? t('common.unknownUser')}
                    </span>
                  </span>
                )) : <span>{t('chat.unassigned')}</span>}
                {assignees.length > 3 ? <span>+{assignees.length - 3}</span> : null}
                {reference.blocked && reference.status.trim().toUpperCase().replaceAll(' ', '_') !== 'BLOCKED' ? (
                  <span className="inline-flex items-center gap-1 font-medium text-red-600 dark:text-red-400">
                    <CircleAlert className="size-3" aria-hidden="true" />
                    {t('status.blocked')}
                  </span>
                ) : null}
                {crossesProjects && reference.projectId ? (
                  <span className="max-w-40 truncate font-medium text-foreground">
                    {projectNames.get(reference.projectId) ?? t('common.unknownProject')}
                  </span>
                ) : null}
              </span>
            </button>
          )
        })}
      </div>

      {footerAction ? (
        <Button type="button" variant="ghost" size="sm" className="w-full justify-between rounded-none border-t px-3" onClick={footerAction}>
          <span>{footerLabel}</span>
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            {hiddenCount > 0 ? t('chat.moreWorkItems', { count: hiddenCount }) : null}
            <ArrowRight className="size-3.5" aria-hidden="true" />
          </span>
        </Button>
      ) : null}
    </section>
  )
}
