import { Check, CheckCircle2, Clock3, ExternalLink, FilePenLine, Loader2, RefreshCw, X, XCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { cn } from '@/lib/utils'
import type { GraphChangeProposal, GraphChangeProposalChange } from '@/lib/api'

const MAX_VISIBLE_CHANGES = 4

type ProposalStatus = 'PENDING' | 'NEEDS_UPDATE' | 'APPLIED' | 'REJECTED' | 'COMPLETED'

function displayStatus(proposal: GraphChangeProposal): ProposalStatus {
  const statuses = proposal.changes.map((change) => change.status)
  if (statuses.includes('NEEDS_UPDATE')) return 'NEEDS_UPDATE'
  if (statuses.includes('PENDING')) return 'PENDING'
  if (statuses.length > 0 && statuses.every((status) => status === 'APPLIED')) return 'APPLIED'
  if (statuses.length > 0 && statuses.every((status) => status === 'REJECTED')) return 'REJECTED'
  return 'COMPLETED'
}

function ProposalStatusBadge({ status }: { status: ProposalStatus }) {
  const { t } = useTranslation()
  const Icon = status === 'APPLIED'
    ? CheckCircle2
    : status === 'REJECTED'
      ? XCircle
      : status === 'NEEDS_UPDATE'
        ? RefreshCw
        : Clock3

  return (
    <Badge
      variant="outline"
      className={cn(
        'shrink-0 gap-1 px-1.5 py-0.5 text-[10px]',
        status === 'APPLIED' && 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300',
        status === 'REJECTED' && 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300',
        status === 'PENDING' && 'border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
        status === 'NEEDS_UPDATE' && 'border-blue-300 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300',
      )}
    >
      <Icon className="size-3" aria-hidden="true" />
      {t(`workspaceProposals.statuses.${status}`)}
    </Badge>
  )
}

function actionBadgeClass(action: GraphChangeProposalChange['action']) {
  if (action === 'ADD') return 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
  if (action === 'DELETE') return 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300'
  return 'border-blue-300 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300'
}

function changeTitle(change: GraphChangeProposalChange) {
  if (change.entityType === 'NODE') {
    return change.node?.title || change.previousNode?.title || change.summary
  }
  return change.summary
}

export function WorkspaceProposalCard({
  proposal,
  projectName,
  busy,
  isStreaming,
  onDecide,
  onReview,
}: {
  proposal: GraphChangeProposal
  projectName: string
  busy: { proposalId: string; decision: 'ACCEPT' | 'REJECT' } | null
  isStreaming: boolean
  onDecide: (proposal: GraphChangeProposal, decision: 'ACCEPT' | 'REJECT') => void
  onReview: (proposal: GraphChangeProposal) => void
}) {
  const { t } = useTranslation()
  const status = displayStatus(proposal)
  const visibleChanges = proposal.changes.slice(0, MAX_VISIBLE_CHANGES)
  const remainingChanges = proposal.changes.length - visibleChanges.length
  const openChanges = proposal.changes.filter((change) => ['PENDING', 'NEEDS_UPDATE'].includes(change.status))
  const isPending = openChanges.length > 0
  const containsDelete = openChanges.some((change) => change.action === 'DELETE')
  const isAccepting = busy?.proposalId === proposal.id && busy.decision === 'ACCEPT'
  const isRejecting = busy?.proposalId === proposal.id && busy.decision === 'REJECT'
  const actionsDisabled = busy !== null || isStreaming
  const acceptButton = (
    <Button
      type="button"
      size="sm"
      variant={containsDelete ? 'destructive' : 'default'}
      className="gap-1.5"
      disabled={actionsDisabled}
      onClick={containsDelete ? undefined : () => onDecide(proposal, 'ACCEPT')}
    >
      {isAccepting ? <Loader2 className="size-3.5 animate-spin" /> : <Check className="size-3.5" />}
      {t('workspaceProposals.accept')}
    </Button>
  )

  return (
    <article className="overflow-hidden rounded-lg border bg-background text-sm">
      <header className="flex items-start justify-between gap-3 border-b bg-muted/20 px-3 py-2.5">
        <div className="flex min-w-0 items-center gap-2">
          <span className="grid size-7 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <FilePenLine className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 className="truncate text-sm font-semibold">{t('workspaceProposals.heading')}</h3>
            <p className="truncate text-xs text-muted-foreground">
              {projectName} · {t('workspaceProposals.changeCount', { count: proposal.changes.length })}
            </p>
          </div>
        </div>
        <ProposalStatusBadge status={status} />
      </header>

      <div className="divide-y px-3">
        {isPending ? (
          <p className="py-2 text-xs text-muted-foreground">{t('workspaceProposals.pendingDescription')}</p>
        ) : null}
        {visibleChanges.map((change) => (
          <div key={change.id} className="flex min-w-0 items-start gap-2 py-2.5">
            <div className="flex shrink-0 flex-wrap gap-1">
              <Badge variant="outline" className={cn('px-1.5 py-0 text-[10px] font-semibold', actionBadgeClass(change.action))}>
                {t(`workspaceProposals.actions.${change.action}`)}
              </Badge>
              <Badge variant="outline" className="px-1.5 py-0 text-[10px] text-muted-foreground">
                {t(`workspaceProposals.entityTypes.${change.entityType}`)}
              </Badge>
            </div>
            <div className="min-w-0 flex-1 text-xs leading-5">
              <p className={cn('break-words font-medium', change.action === 'DELETE' && 'line-through decoration-destructive')}>
                {changeTitle(change)}
              </p>
              {change.entityType === 'NODE' && change.summary && change.summary !== changeTitle(change) ? (
                <p className="break-words text-muted-foreground">{change.summary}</p>
              ) : null}
            </div>
          </div>
        ))}
        {remainingChanges > 0 ? (
          <p className="py-2 text-xs text-muted-foreground">{t('workspaceProposals.moreChanges', { count: remainingChanges })}</p>
        ) : null}
      </div>

      <footer className="flex flex-wrap gap-2 border-t bg-muted/10 px-3 py-2.5">
        {isPending ? (
          containsDelete ? (
            <DeleteConfirmPopover
              trigger={acceptButton}
              title={t('workspaceProposals.confirmDeleteTitle')}
              description={t('workspaceProposals.confirmDeleteDescription')}
              confirmLabel={t('workspaceProposals.acceptDelete')}
              disabled={actionsDisabled}
              onConfirm={() => onDecide(proposal, 'ACCEPT')}
            />
          ) : acceptButton
        ) : null}
        {isPending ? (
          <Button type="button" size="sm" variant="outline" className="gap-1.5" disabled={actionsDisabled} onClick={() => onDecide(proposal, 'REJECT')}>
            {isRejecting ? <Loader2 className="size-3.5 animate-spin" /> : <X className="size-3.5" />}
            {t('workspaceProposals.reject')}
          </Button>
        ) : null}
        <Button type="button" size="sm" variant="ghost" className="gap-1.5" disabled={actionsDisabled} onClick={() => onReview(proposal)}>
          <ExternalLink className="size-3.5" />
          {t('workspaceProposals.review')}
        </Button>
      </footer>
    </article>
  )
}
