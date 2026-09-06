import { useTranslation } from 'react-i18next'
import { Check, CheckCircle2, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import type { IdentityProposal } from '@/lib/api'

const fields = ['name', 'description', 'ownerNames', 'role', 'username', 'email', 'displayName', 'title', 'bio', 'timezone', 'status', 'globalRole'] as const

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
      'space-y-3 rounded-lg border bg-background p-3 text-sm',
      proposal.status === 'APPLIED' && 'border-emerald-300 bg-emerald-50/40 dark:border-emerald-800 dark:bg-emerald-950/20',
    )}>
      <div className={cn('flex items-center gap-2 font-medium', proposal.status === 'APPLIED' && 'text-emerald-700 dark:text-emerald-300')}>
        {proposal.status === 'APPLIED' && <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />}
        <span>{t('identityProposals.statuses.' + proposal.status)} · {changes.length} {t('identityProposals.changes')}</span>
      </div>
      {proposal.status === 'APPLIED' && <div role="status" className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-100/70 px-2.5 py-2 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-200">
        <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="font-medium">{t('identityProposals.appliedMessage')}</span>
      </div>}
      {changes.map((change) => {
        const target = change.after.team || change.after.user || change.after.name || change.after.displayName || change.after.username
        const changed = fields.filter((key) => change.before[key] !== change.after[key])
        return <div key={change.id} className="space-y-2 rounded-md bg-muted/30 p-2">
          <div className="font-medium">{t(`identityProposals.kinds.${change.kind}`)} · {t(`identityProposals.actions.${change.action}`)}</div>
          <p className="break-words">{target}{change.after.project ? ` → ${change.after.project}` : ''}{change.kind === 'TEAM_MEMBERSHIP' ? ` · ${change.after.user}` : ''}</p>
          <dl className="space-y-2">
            {changed.map((key) => <div key={key}>
              <dt className="font-medium">{t(`identityProposals.fields.${key}`)}</dt>
              <dd className="whitespace-pre-wrap break-words text-muted-foreground">{change.before[key] || t('identityProposals.notSet')} → {change.after[key] || t('identityProposals.notSet')}</dd>
            </div>)}
          </dl>
          {change.kind === 'TEAM_MEMBERSHIP' && <p className="text-muted-foreground">{t('identityProposals.teamImpact')}</p>}
          {change.kind === 'PROJECT_MEMBERSHIP' && <p className="text-muted-foreground">{t('identityProposals.projectImpact')}</p>}
        </div>
      })}
      {proposal.status === 'PENDING' && <div className="flex gap-2">
        <Button size="sm" disabled={busy !== null || isStreaming} onClick={() => onDecide(proposal, 'ACCEPT')}><Check className="h-4 w-4" />{t('identityProposals.accept')}</Button>
        <Button size="sm" variant="outline" disabled={busy !== null || isStreaming} onClick={() => onDecide(proposal, 'REJECT')}><X className="h-4 w-4" />{t('identityProposals.reject')}</Button>
      </div>}
    </article>
  )
}
