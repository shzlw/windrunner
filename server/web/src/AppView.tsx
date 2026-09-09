import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useOutlet, useOutletContext, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import type { AiAgentPageOutletContext } from './App'
import AiAgentPage from './AiAgentPage'
import PaneLayout from './PaneLayout'
import { addChatSessionContext, type ChatContextEntityType } from '@/lib/api'

type PageArtifactContext = {
  entityType: ChatContextEntityType
  entityId: string
  label: 'project' | 'work item' | 'team' | 'user'
}

export default function AppView() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { projectId, teamId } = useParams()
  const appContext = useOutletContext<AiAgentPageOutletContext>()
  const [artifactRefreshKey, setArtifactRefreshKey] = useState(0)
  const [workspaceProposalRefreshKey, setWorkspaceProposalRefreshKey] = useState(0)
  const notifyArtifactChange = useCallback(() => {
    setArtifactRefreshKey((current) => current + 1)
  }, [])
  const notifyWorkspaceProposalChange = useCallback(() => {
    setWorkspaceProposalRefreshKey((current) => current + 1)
  }, [])
  const outlet = useOutlet({ ...appContext, artifactRefreshKey, notifyWorkspaceProposalChange })
  const isAiAgent = location.pathname === '/app/ai-agent' || location.pathname.startsWith('/app/ai-agent/')
  const hasChatPanel = searchParams.get('chatPanel') === 'open'
  const userId = location.pathname === '/app/users' ? searchParams.get('userId') : null
  const workItemId = projectId && location.pathname.startsWith('/app/projects/') ? searchParams.get('workItemId') : null
  const artifactContexts: PageArtifactContext[] = [
    projectId ? { entityType: 'PROJECT', entityId: projectId, label: 'project' } : null,
    workItemId ? { entityType: 'WORK_ITEM', entityId: workItemId, label: 'work item' } : null,
    teamId ? { entityType: 'TEAM', entityId: teamId, label: 'team' } : null,
    userId ? { entityType: 'USER', entityId: userId, label: 'user' } : null,
  ].filter((context): context is PageArtifactContext => Boolean(context))
  const openAssistant = useCallback(async () => {
    let sessionId = searchParams.get('chatSessionId')
    if (artifactContexts.length > 0) {
      if (!sessionId) {
        const session = await appContext.createChatSession()
        sessionId = session?.id ?? null
      }
      if (!sessionId) {
        return
      }
      const activeSessionId = sessionId
      try {
        await Promise.all(artifactContexts.map((context) => addChatSessionContext(activeSessionId, context.entityType, context.entityId)))
        await appContext.refreshChatSessions(activeSessionId)
      } catch (error) {
        toast.error(error instanceof Error ? error.message : t('aiAgent.failedAddPage'))
        return
      }
    }

    const nextParams = new URLSearchParams(searchParams)
    nextParams.set('chatPanel', 'open')
    if (sessionId) {
      nextParams.set('chatSessionId', sessionId)
    }
    const query = nextParams.toString()
    navigate(`${location.pathname}${query ? `?${query}` : ''}${location.hash}`)
  }, [appContext, artifactContexts, location.hash, location.pathname, navigate, searchParams, t])

  useEffect(() => {
    if (isAiAgent) {
      return
    }
    return appContext.registerOpenAssistant(openAssistant)
  }, [appContext, isAiAgent, openAssistant])

  if (isAiAgent) {
    return <PaneLayout mode="chat" chat={<AiAgentPage showWelcome={true} />} />
  }

  return (
    <PaneLayout
      mode={hasChatPanel ? 'split' : 'artifact'}
      chat={hasChatPanel ? <AiAgentPage projectId={projectId} showWelcome={false} showStandaloneAction onGraphChangeProposalSaved={notifyArtifactChange} workspaceProposalRefreshKey={workspaceProposalRefreshKey} /> : undefined}
      artifact={outlet}
    />
  )
}
