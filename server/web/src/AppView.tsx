import { useLocation, useOutlet, useOutletContext, useParams, useSearchParams } from 'react-router'

import type { AskPageOutletContext } from './App'
import AskPage from './AskPage'
import PaneLayout from './PaneLayout'

export default function AppView() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { projectId } = useParams()
  const appContext = useOutletContext<AskPageOutletContext>()
  const outlet = useOutlet(appContext)
  const isHome = location.pathname === '/app' || location.pathname === '/app/home'
  const isAskAi = location.pathname === '/app/ask-ai' || location.pathname.startsWith('/app/ask-ai/')
  const hasChatPanel = searchParams.get('chatPanel') === 'open'

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
    />
  )
}
