import { useLocation, useNavigate, useOutlet, useOutletContext, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import type { AskPageOutletContext } from './App'
import AskPage from './AskPage'
import PaneLayout from './PaneLayout'
import { addChatSessionContext, type ChatContextEntityType } from '@/lib/api'

type PageArtifactContext = {
  entityType: ChatContextEntityType
  entityId: string
  label: 'project' | 'work item' | 'team' | 'user'
}

export default function AppView() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { projectId, teamId } = useParams()
  const appContext = useOutletContext<AskPageOutletContext>()
  const outlet = useOutlet(appContext)
  const isHome = location.pathname === '/app' || location.pathname === '/app/home'
  const isAskAi = location.pathname === '/app/ask-ai' || location.pathname.startsWith('/app/ask-ai/')
  const hasChatPanel = searchParams.get('chatPanel') === 'open'
  const userId = location.pathname === '/app/users' ? searchParams.get('userId') : null
  const workItemId = projectId && location.pathname.startsWith('/app/projects/') ? searchParams.get('workItemId') : null
  const artifactContexts: PageArtifactContext[] = [
    projectId ? { entityType: 'PROJECT', entityId: projectId, label: 'project' } : null,
    workItemId ? { entityType: 'WORK_ITEM', entityId: workItemId, label: 'work item' } : null,
    teamId ? { entityType: 'TEAM', entityId: teamId, label: 'team' } : null,
    userId ? { entityType: 'USER', entityId: userId, label: 'user' } : null,
  ].filter((context): context is PageArtifactContext => Boolean(context))
  const assistantArtifact = artifactContexts.find((context) => context.entityType === 'WORK_ITEM') ?? artifactContexts[0]

  async function openAssistant() {
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
        toast.error(error instanceof Error ? error.message : 'Failed to add this page to AI context.')
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
  }

  if (isHome) {
    return <PaneLayout mode="full" content={outlet} />
  }

  if (isAskAi) {
    return <PaneLayout mode="chat" chat={<AskPage />} />
  }

  return (
    <PaneLayout
      mode={hasChatPanel ? 'split' : 'artifact'}
      chat={hasChatPanel ? <AskPage projectId={projectId} /> : undefined}
      artifact={outlet}
      onOpenAssistant={openAssistant}
      assistantLabel={assistantArtifact ? `Ask AI about this ${assistantArtifact.label}` : undefined}
    />
  )
}
