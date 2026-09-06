import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { decideIdentityProposal, listIdentityProposals, type IdentityProposal, type IdentityProposalPage } from '@/lib/api'

type ProposalPageState = {
  sessionId: string
  page: IdentityProposalPage
}

type ProposalErrorState = {
  sessionId: string
  message: string
}

export function useIdentityProposals(sessionId: string | undefined, isStreaming: boolean, onApplied?: () => void) {
  const { t } = useTranslation()
  const [pageState, setPageState] = useState<ProposalPageState | null>(null)
  const [errorState, setErrorState] = useState<ProposalErrorState | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const page = pageState && pageState.sessionId === sessionId ? pageState.page : null
  const error = errorState && errorState.sessionId === sessionId ? errorState.message : ''

  useEffect(() => {
    if (!sessionId || isStreaming) return
    let active = true
    listIdentityProposals(sessionId).then((result) => {
      if (active) {
        setPageState({ sessionId, page: result })
        setErrorState(null)
      }
    }).catch((cause: unknown) => {
      if (active) {
        setErrorState({ sessionId, message: cause instanceof Error ? cause.message : t('identityProposals.loadError') })
      }
    })
    return () => { active = false }
  }, [sessionId, isStreaming, t])

  async function decide(proposal: IdentityProposal, decision: 'ACCEPT' | 'REJECT') {
    if (!sessionId) return
    setBusy(proposal.id)
    try {
      const updated = await decideIdentityProposal(sessionId, proposal.id, decision)
      setPageState((current) => {
        if (!current || current.sessionId !== sessionId) return current
        return { ...current, page: { ...current.page, items: current.page.items.map((item) => item.id === updated.id ? updated : item) } }
      })
      setErrorState(null)
      if (updated.status === 'APPLIED') { toast.success(t('identityProposals.appliedToast')); onApplied?.() }
    } catch (cause) {
      toast.error(cause instanceof Error ? cause.message : t('identityProposals.decisionError'))
    } finally { setBusy(null) }
  }

  async function loadMore() {
    if (!page || loadingMore || !sessionId) return
    setLoadingMore(true)
    try {
      const next = await listIdentityProposals(sessionId, page.offset + page.limit)
      setPageState((current) => {
        if (!current || current.sessionId !== sessionId) return current
        return { ...current, page: { ...next, items: [...current.page.items, ...next.items] } }
      })
      setErrorState(null)
    } catch (cause) { toast.error(cause instanceof Error ? cause.message : t('identityProposals.loadError')) }
    finally { setLoadingMore(false) }
  }

  return { page, error, busy, loadingMore, decide, loadMore }
}
