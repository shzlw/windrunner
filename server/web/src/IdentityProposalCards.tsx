import { useTranslation } from 'react-i18next'
import { ArrowRight, Check, CheckCircle2, Clock3, ListChecks, Loader2, X, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { IdentityProposal } from '@/lib/api'

const fields = ['name', 'description', 'ownerNames', 'role', 'username', 'email', 'displayName', 'title', 'bio', 'timezone', 'status', 'globalRole'] as const

function ProposalStatusBadge({ status, label }: { status: IdentityProposal['status']; label: string }) {
  const Icon = status === 'APPLIED' ? CheckCircle2 : status === 'REJECTED' ? XCircle : status === 'APPLYING' ? Loader2 : Clock3
  const iconOnly = status === 'APPLIED' || status === 'REJECTED'
  return (
    <Badge
      variant="outline"
      aria-label={iconOnly ? label : undefined}
      title={iconOnly ? label : undefined}
      className={cn(
        'gap-1.5 px-2 py-1',
        iconOnly && 'px-1.5',
        status === 'APPLIED' && 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300',
        status === 'REJECTED' && 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300',
        status === 'PENDING' && 'border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
        status === 'APPLYING' && 'border-blue-300 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300',
      )}
    >
      <Icon className={cn('h-3.5 w-3.5', status === 'APPLYING' && 'animate-spin')} aria-hidden="true" />
      {iconOnly ? null : label}
    </Badge>
  )
}

function actionBadgeClass(action: IdentityProposal['action']) {
  if (action === 'ADD') return 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
  if (action === 'REMOVE') return 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300'
  if (action === 'UPDATE') return 'border-blue-300 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300'
  return 'border-border bg-background text-foreground'
}

export function IdentityProposalCard({ proposal, busy, isStreaming, onDecide }: {
  proposal: IdentityProposal
  busy: string | null
  isStreaming: boolean
  onDecide: (proposal: IdentityProposal, decision: 'ACCEPT' | 'REJECT') => void
}) {
  const { t } = useTranslation()
  const changes = proposal.changes?.length ? proposal.changes : [{ id: proposal.id, kind: proposal.kind, action: proposal.action, status: proposal.status, before: proposal.before, after: proposal.after }]

  return (
    <article className={cn(
      'overflow-hidden rounded-xl border bg-background text-sm shadow-sm',
      proposal.status === 'APPLIED' && 'border-emerald-300 dark:border-emerald-800',
      proposal.status === 'REJECTED' && 'border-red-300 dark:border-red-800',
    )}>
      <header className="flex items-start justify-between gap-3 border-b bg-muted/20 px-4 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <div className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
              <ListChecks className="h-4 w-4" aria-hidden="true" />
            </div>
            <div className="min-w-0">
              <h3 className="truncate font-semibold">{t('identityProposals.heading')} <span className="font-normal text-muted-foreground">· {changes.length} {t('identityProposals.changes')}</span></h3>
            </div>
          </div>
        </div>
        <ProposalStatusBadge status={proposal.status} label={t('identityProposals.statuses.' + proposal.status)} />
      </header>

      <div className="space-y-3 p-4">
        {changes.map((change, index) => {
          const target = change.after.team || change.after.user || change.after.name || change.after.displayName || change.after.username || change.before.team || change.before.user || change.before.name || change.before.displayName || change.before.username
          const relatedTarget = change.kind === 'TEAM_MEMBERSHIP' ? change.after.user || change.before.user : change.after.project || change.before.project
          const targetLabel = change.kind === 'TEAM_MEMBERSHIP' ? t('common.team') : change.kind === 'PROJECT_MEMBERSHIP' ? t('common.user') : change.kind === 'TEAM' ? t('common.team') : t('common.user')
          const relatedTargetLabel = change.kind === 'TEAM_MEMBERSHIP' ? t('common.user') : t('common.project')
          const changed = fields.filter((key) => change.before[key] !== change.after[key])
          return <div key={change.id} className="space-y-2 border-b border-border/70 pb-3 last:border-b-0 last:pb-0">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              {changes.length > 1 ? <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-muted text-xs font-semibold text-muted-foreground" aria-label={t('identityProposals.changeNumber', { number: index + 1 })}>{index + 1}</span> : null}
              <Badge variant="outline" className={cn('font-semibold', actionBadgeClass(change.action))}>{t(`identityProposals.actions.${change.action}`)}</Badge>
              <Badge variant="outline" className="bg-background text-foreground">{t(`identityProposals.kinds.${change.kind}`)}</Badge>
              <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{targetLabel}:</span>
              <span className="min-w-0 break-words font-semibold">{target}</span>
              {relatedTarget ? <>
                <span className="text-muted-foreground">·</span>
                <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{relatedTargetLabel}:</span>
                <span className="min-w-0 break-words font-medium">{relatedTarget}</span>
              </> : null}
              <div className="ml-auto shrink-0">
                <ProposalStatusBadge status={change.status} label={t('identityProposals.statuses.' + change.status)} />
              </div>
            </div>

            {changed.length > 0 ? <dl className="space-y-1.5">
              {changed.map((key) => <div key={key} className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 rounded-md border bg-background/70 px-2.5 py-1.5">
                <dt className="shrink-0 text-xs font-semibold text-muted-foreground">{t(`identityProposals.fields.${key}`)}</dt>
                <dd className="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1">
                  <span className="text-[10px] font-semibold uppercase tracking-wide text-red-600/80 dark:text-red-300/80">{t('history.before')}</span>
                  <span className="min-w-0 break-words rounded bg-red-50/70 px-1.5 py-0.5 text-red-800 dark:bg-red-950/30 dark:text-red-200">{change.before[key] || t('identityProposals.notSet')}</span>
                  <ArrowRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                  <span className="text-[10px] font-semibold uppercase tracking-wide text-emerald-600/80 dark:text-emerald-300/80">{t('history.after')}</span>
                  <span className="min-w-0 break-words rounded bg-emerald-50/70 px-1.5 py-0.5 font-semibold text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200">{change.after[key] || t('identityProposals.notSet')}</span>
                </dd>
              </div>)}
            </dl> : null}

          </div>
        })}
      </div>

      {proposal.status === 'PENDING' && <footer className="flex gap-2 border-t bg-muted/10 px-4 py-3">
        <Button size="sm" disabled={busy !== null || isStreaming} onClick={() => onDecide(proposal, 'ACCEPT')}><Check className="h-4 w-4" />{t('identityProposals.accept')}</Button>
        <Button size="sm" variant="outline" disabled={busy !== null || isStreaming} onClick={() => onDecide(proposal, 'REJECT')}><X className="h-4 w-4" />{t('identityProposals.reject')}</Button>
      </footer>}
    </article>
  )
}
