import { useLocation, useNavigate, useOutlet, useOutletContext, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import type { AskPageOutletContext } from './App'
import AskPage from './AskPage'
import PaneLayout from './PaneLayout'
import { addChatSessionContext } from '@/lib/api'

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

  async function openAssistant() {
    let sessionId = searchParams.get('chatSessionId')
    if (teamId) {
      if (!sessionId) {
        const session = await appContext.createChatSession()
        sessionId = session?.id ?? null
      }
      if (!sessionId) {
        return
      }
      try {
        await addChatSessionContext(sessionId, 'TEAM', teamId)
        await appContext.refreshChatSessions(sessionId)
      } catch (error) {
        toast.error(error instanceof Error ? error.message : 'Failed to add this team to AI context.')
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
      assistantLabel={teamId ? 'Ask AI about this team' : undefined}
    />
  )
}
