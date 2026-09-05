import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { decideIdentityProposal, listIdentityProposals, type IdentityProposal, type IdentityProposalPage } from '@/lib/api'

const fields = ['name', 'description', 'ownerNames', 'role', 'username', 'email', 'displayName', 'title', 'bio', 'timezone', 'status', 'globalRole'] as const

export default function IdentityProposalCards({ sessionId, isStreaming, onApplied }: {
  sessionId: string
  isStreaming: boolean
  onApplied?: () => void
}) {
  const { t } = useTranslation()
  const [page, setPage] = useState<IdentityProposalPage | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    if (isStreaming) return
    let active = true
    listIdentityProposals(sessionId).then((result) => {
      if (active) { setPage(result); setError('') }
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : t('identityProposals.loadError'))
    })
    return () => { active = false }
  }, [sessionId, isStreaming, t])

  async function decide(proposal: IdentityProposal, decision: 'ACCEPT' | 'REJECT') {
    setBusy(proposal.id)
    try {
      const updated = await decideIdentityProposal(sessionId, proposal.id, decision)
      setPage((current) => current ? { ...current, items: current.items.map((item) => item.id === updated.id ? updated : item) } : current)
      if (updated.status === 'APPLIED') { toast.success(t('identityProposals.appliedToast')); onApplied?.() }
    } catch (cause) {
      toast.error(cause instanceof Error ? cause.message : t('identityProposals.decisionError'))
    } finally { setBusy(null) }
  }

  async function loadMore() {
    if (!page || loadingMore) return
    setLoadingMore(true)
    try {
      const next = await listIdentityProposals(sessionId, page.offset + page.limit)
      setPage((current) => ({ ...next, items: [...(current?.items ?? []), ...next.items] }))
    } catch (cause) { toast.error(cause instanceof Error ? cause.message : t('identityProposals.loadError')) }
    finally { setLoadingMore(false) }
  }

  if (error) return <p role="alert" className="px-3 text-sm text-destructive">{error}</p>
  if (!page?.items.length && !page?.hasMore) return null
  return (
    <section aria-label={t('identityProposals.heading')} className="space-y-3 px-3 pb-3">
      {page?.items.map((proposal) => {
        const changes = proposal.changes?.length ? proposal.changes : [{ id: proposal.id, kind: proposal.kind, action: proposal.action, status: proposal.status, before: proposal.before, after: proposal.after }]
        return (
          <article key={proposal.id} className="space-y-3 rounded-lg border bg-background p-3 text-sm">
            <div className="font-medium">{t('identityProposals.statuses.' + proposal.status)} · {changes.length} {t('identityProposals.changes')}</div>
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
              <Button size="sm" disabled={busy !== null || isStreaming} onClick={() => void decide(proposal, 'ACCEPT')}><Check className="h-4 w-4" />{t('identityProposals.accept')}</Button>
              <Button size="sm" variant="outline" disabled={busy !== null || isStreaming} onClick={() => void decide(proposal, 'REJECT')}><X className="h-4 w-4" />{t('identityProposals.reject')}</Button>
            </div>}
          </article>
        )
      })}
      {page?.hasMore && <Button variant="outline" size="sm" disabled={loadingMore} onClick={() => void loadMore()}>{t('identityProposals.loadMore')}</Button>}
    </section>
  )
}
