import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { decideWorkspaceProposal, listGraphChangeProposals, type GraphChangeProposal } from '@/lib/api'

type WorkspaceProposalState = {
  requestKey: string
  proposals: GraphChangeProposal[]
}

type WorkspaceProposalError = {
  requestKey: string
  message: string
}

export function useWorkspaceProposals(
  sessionId: string | undefined,
  projectIds: string[],
  isStreaming: boolean,
  refreshKey = 0,
  onDecided?: () => void | Promise<void>,
) {
  const { t } = useTranslation()
  const projectIdsKey = [...new Set(projectIds.filter(Boolean))].sort().join(',')
  const requestKey = `${sessionId ?? ''}:${projectIdsKey}`
  const [state, setState] = useState<WorkspaceProposalState | null>(null)
  const [errorState, setErrorState] = useState<WorkspaceProposalError | null>(null)
  const [busy, setBusy] = useState<{ proposalId: string; decision: 'ACCEPT' | 'REJECT' } | null>(null)
  const [decisionRevision, setDecisionRevision] = useState(0)

  useEffect(() => {
    const normalizedProjectIds = projectIdsKey ? projectIdsKey.split(',') : []
    if (!sessionId || normalizedProjectIds.length === 0 || isStreaming) return
    let active = true

    Promise.all(normalizedProjectIds.map((projectId) => listGraphChangeProposals(projectId, sessionId)))
      .then((proposalLists) => {
        if (!active) return
        const proposals = proposalLists
          .flat()
          .filter((proposal) => proposal.chatSessionId === sessionId)
          .sort((left, right) => (left.createdAt ?? left.id).localeCompare(right.createdAt ?? right.id))
        setState({ requestKey, proposals })
        setErrorState(null)
      })
      .catch((cause: unknown) => {
        if (active) {
          setErrorState({
            requestKey,
            message: cause instanceof Error ? cause.message : t('workspaceProposals.loadError'),
          })
        }
      })

    return () => { active = false }
  }, [decisionRevision, isStreaming, projectIdsKey, refreshKey, requestKey, sessionId, t])

  async function decide(proposal: GraphChangeProposal, decision: 'ACCEPT' | 'REJECT') {
    setBusy({ proposalId: proposal.id, decision })
    try {
      await decideWorkspaceProposal(proposal.projectId, proposal.id, decision)
      const nextStatus = decision === 'ACCEPT' ? 'APPLIED' : 'REJECTED'
      setState((current) => current?.requestKey === requestKey
        ? {
            ...current,
            proposals: current.proposals.map((item) => item.id === proposal.id
              ? {
                  ...item,
                  status: 'COMPLETED',
                  changes: item.changes.map((change) => ['PENDING', 'NEEDS_UPDATE'].includes(change.status)
                    ? { ...change, status: nextStatus }
                    : change),
                }
              : item),
          }
        : current)
      setDecisionRevision((current) => current + 1)
      toast.success(t(decision === 'ACCEPT' ? 'workspaceProposals.appliedToast' : 'workspaceProposals.rejectedToast'))
      await onDecided?.()
    } catch (cause) {
      toast.error(cause instanceof Error ? cause.message : t('workspaceProposals.decisionError'))
    } finally {
      setBusy(null)
    }
  }

  return {
    proposals: state?.requestKey === requestKey ? state.proposals : [],
    error: errorState?.requestKey === requestKey ? errorState.message : '',
    busy,
    decide,
  }
}
