import {useState, type KeyboardEvent, type ReactNode} from 'react';
import Head from '@docusaurus/Head';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import {
  ArrowRight,
  Bot,
  CheckCircle2,
  KeyRound,
  ListTodo,
  ListTree,
  MessageSquareText,
  Plug,
  Server,
  Terminal,
  TrendingUp,
} from 'lucide-react';

const collaborationFeatures = [
  {
    icon: ListTree,
    title: 'Structured project work',
    description:
      'Organize hierarchical work items with entries for decisions, findings, answers, and evidence.',
    to: '/docs/core-concepts/projects-workspace',
    accent: 'accent-violet',
  },
  {
    icon: TrendingUp,
    title: 'Status and dependencies',
    description:
      'Track status, priority, due dates, assignments, blockers, and typed relationships between work.',
    to: '/docs/core-concepts/projects-workspace',
    accent: 'accent-cyan',
  },
  {
    icon: MessageSquareText,
    title: 'AI Agent with context',
    description:
      'Ask questions from Home or a project workspace and add project, team, user, or work-item context.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-blue',
  },
  {
    icon: Bot,
    title: 'Reviewable AI changes',
    description:
      'Review proposed changes to work items, entries, and relationships before applying them.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-amber',
  },
  {
    icon: MessageSquareText,
    title: 'Multiple chat sessions',
    description:
      'Create, switch between, rename, delete, and continue separate AI Agent conversations.',
    to: '/docs/guides/home-and-ai-agent',
    accent: 'accent-emerald',
  },
  {
    icon: ListTree,
    title: 'Split workspace views',
    description:
      'Keep AI Agent and the selected project or artifact visible together with adaptive workspace layouts.',
    to: '/docs/guides/home-and-ai-agent',
    accent: 'accent-indigo',
  },
  {
    icon: ListTodo,
    title: 'Search, history, and notifications',
    description:
      'Find work with full-text search, inspect item history, and follow assignments and activity.',
    to: '/docs/guides/search-and-filtering',
    accent: 'accent-emerald',
  },
  {
    icon: KeyRound,
    title: 'Teams, profiles, and access',
    description:
      'Manage teams, memberships, project roles, user titles and bios, and team descriptions.',
    to: '/docs/guides/users-and-teams',
    accent: 'accent-indigo',
  },
];

const stackFeatures = [
  {
    icon: Bot,
    title: 'Configurable AI providers',
    description:
      'Use supported providers with your own credentials, models, and limits.',
    to: '/docs/reference/ai-providers',
    accent: 'accent-indigo',
  },
  {
    icon: TrendingUp,
    title: 'AI usage analytics',
    description:
      'Review request and token usage, reliability, accepted changes, and acceptance rates.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-cyan',
  },
  {
    icon: Plug,
    title: 'MCP server and agent tools',
    description:
      'Connect AI clients to search work, read bounded context, record findings, update status, and create links.',
    to: '/docs/reference/mcp',
    accent: 'accent-violet',
  },
  {
    icon: Terminal,
    title: 'REST and OpenAPI',
    description:
      'Integrate projects, work items, entries, relationships, teams, users, search, and audit logs.',
    to: '/api',
    accent: 'accent-blue',
  },
  {
    icon: Terminal,
    title: 'Windrunner CLI',
    description:
      'Script the REST API from a terminal for local workflows, automation, and agent access.',
    to: '/docs/reference/cli',
    accent: 'accent-blue',
  },
  {
    icon: KeyRound,
    title: 'Scoped API keys',
    description:
      'Use separate keys with project-level access and resource-specific scopes for REST and MCP.',
    to: '/docs/reference/api-keys-and-scopes',
    accent: 'accent-amber',
  },
  {
    icon: Server,
    title: 'Self-hosted deployment',
    description:
      'Run Windrunner with Docker Compose and PostgreSQL in infrastructure you control.',
    to: '/docs/getting-started/installation',
    accent: 'accent-slate',
  },
];

type Feature = (typeof collaborationFeatures)[number] | (typeof stackFeatures)[number];

const websiteUrl = 'https://shzlw.github.io/windrunner/';
const websiteDescription =
  'A self-hosted, AI-native Work Hub where work becomes a structured Work Graph — chat by default, workspace when you need control.';

const websiteStructuredData = {
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'Organization',
      '@id': `${websiteUrl}#organization`,
      name: 'Windrunner',
      url: websiteUrl,
      logo: `${websiteUrl}img/favicon.svg`,
      sameAs: ['https://github.com/shzlw/windrunner'],
    },
    {
      '@type': 'WebSite',
      '@id': `${websiteUrl}#website`,
      name: 'Windrunner',
      url: websiteUrl,
      description: websiteDescription,
      publisher: {'@id': `${websiteUrl}#organization`},
    },
  ],
};

const agentScenarios = [
  {
    id: 'find-team',
    label: 'Find the right team',
    description: 'Which team can help me install a new database?',
    icon: ListTree,
    answerTitle: 'Platform Engineering',
    answerMeta: '3 matching members · 2 scheduled tasks',
    resultIcon: ListTree,
    screenshot: '/img/windrunner-ai-agent-chat.png',
    screenshotAlt: 'Windrunner AI Agent showing a structured team recommendation',
  },
  {
    id: 'attention',
    label: 'See what needs attention',
    description: 'What needs my attention today?',
    icon: ListTodo,
    answerTitle: '4 items need your attention',
    answerMeta: '1 overdue · 2 blocked · 1 new assignment',
    resultIcon: ListTodo,
    screenshot: '/img/windrunner-notifications.png',
    screenshotAlt: 'Windrunner notifications showing work that needs attention',
  },
  {
    id: 'plan-work',
    label: 'Turn a request into work',
    description: 'Turn the database request into a project plan.',
    icon: TrendingUp,
    answerTitle: 'Migration plan ready for review',
    answerMeta: '5 work items · 2 dependencies · 1 proposal',
    resultIcon: TrendingUp,
    screenshot: '/img/windrunner-ai-agent-proposal.png',
    screenshotAlt: 'Windrunner AI Agent showing a proposed project plan',
  },
  {
    id: 'understand-progress',
    label: 'Understand progress',
    description: 'What happened with the database migration task?',
    icon: MessageSquareText,
    answerTitle: 'Migration task moved to In progress',
    answerMeta: '6 timeline events · Last update 2h ago',
    resultIcon: MessageSquareText,
    screenshot: '/img/windrunner-work-item-inspector.png',
    screenshotAlt: 'Windrunner work item inspector showing task progress',
  },
  {
    id: 'review-change',
    label: 'Review an AI change',
    description: 'Should we add a blocker for the database migration?',
    icon: CheckCircle2,
    answerTitle: 'Proposal ready to review',
    answerMeta: '1 relationship · 2 affected items',
    resultIcon: CheckCircle2,
    screenshot: '/img/windrunner-ai-review-draft.png',
    screenshotAlt: 'Windrunner work item inspector showing an AI change proposal',
  },
] as const;

function FeatureCard({icon: Icon, title, description, to, accent}: Feature) {
  return (
    <Link
      to={to ?? '/docs/getting-started/installation'}
      className={`home-feature-card card ${accent}`}
    >
      <div className="card__body">
        <div className="home-feature-icon">
          <Icon size={22} strokeWidth={1.8} />
        </div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </Link>
  );
}

function AgentFlowPreview(): ReactNode {
  const [activeScenario, setActiveScenario] = useState(0);
  const scenario = agentScenarios[activeScenario];
  const ResultIcon = scenario.resultIcon;
  const screenshot = useBaseUrl(scenario.screenshot);

  function handleScenarioKeyDown(
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) {
    let nextIndex: number | undefined;

    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
      nextIndex = (index + 1) % agentScenarios.length;
    } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
      nextIndex = (index - 1 + agentScenarios.length) % agentScenarios.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = agentScenarios.length - 1;
    }

    if (nextIndex === undefined) {
      return;
    }

    event.preventDefault();
    setActiveScenario(nextIndex);
    requestAnimationFrame(() => {
      document.getElementById(`home-agent-tab-${agentScenarios[nextIndex].id}`)?.focus();
    });
  }

  return (
    <div className="home-agent-showcase" aria-label="Explore Windrunner AI Agent scenarios">
      <div
        className="home-agent-tabs"
        role="tablist"
        aria-label="Windrunner AI Agent scenarios"
        aria-orientation="vertical"
      >
        <div className="home-agent-tabs-heading">
          <span>Explore the agent</span>
          <strong>Start with a question</strong>
        </div>
        {agentScenarios.map((scenarioItem, index) => {
          const TabIcon = scenarioItem.icon;
          const isActive = index === activeScenario;

          return (
            <button
              key={scenarioItem.id}
              id={`home-agent-tab-${scenarioItem.id}`}
              className={`home-agent-tab${isActive ? ' home-agent-tab--active' : ''}`}
              type="button"
              role="tab"
              aria-selected={isActive}
              aria-controls="home-agent-scenario-panel"
              tabIndex={isActive ? 0 : -1}
              onClick={() => setActiveScenario(index)}
              onKeyDown={(event) => handleScenarioKeyDown(event, index)}
            >
              <span className="home-agent-tab-icon">
                <TabIcon size={17} strokeWidth={1.8} aria-hidden="true" />
              </span>
              <span className="home-agent-tab-copy">
                <strong>{scenarioItem.label}</strong>
                <span>{scenarioItem.description}</span>
              </span>
              <ArrowRight className="home-agent-tab-arrow" size={16} strokeWidth={1.8} aria-hidden="true" />
            </button>
          );
        })}
      </div>

      <div
        id="home-agent-scenario-panel"
        className="home-agent-scenario"
        role="tabpanel"
        aria-labelledby={`home-agent-tab-${scenario.id}`}
        aria-live="polite"
      >
        <div className="home-agent-scenario-meta">
          <span>Live preview</span>
          <span>{String(activeScenario + 1).padStart(2, '0')} / 05</span>
        </div>
        <div key={scenario.id} className="home-agent-answer-preview">
          <div className="home-agent-answer-layout">
            <article className="home-agent-answer-card">
              <div className="home-agent-answer-copy">
                <div className="home-agent-answer-heading">
                  <span className="home-agent-answer-step">Current answer</span>
                  <CheckCircle2 size={17} strokeWidth={1.8} aria-hidden="true" />
                </div>
                <div className="home-agent-answer-result">
                  <div className="home-agent-answer-result-icon">
                    <ResultIcon size={18} strokeWidth={1.8} aria-hidden="true" />
                  </div>
                  <div>
                    <strong>{scenario.answerTitle}</strong>
                    <span>{scenario.answerMeta}</span>
                  </div>
                </div>
              </div>
              <a
                className="home-agent-answer-screenshot"
                href={screenshot}
                target="_blank"
                rel="noreferrer"
                title="Open the Windrunner AI Agent screenshot in a new tab"
              >
                <img
                  src={screenshot}
                  alt={scenario.screenshotAlt}
                  loading="eager"
                  decoding="async"
                />
              </a>
            </article>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout
      title="Windrunner"
      description={websiteDescription}
    >
      <Head>
        <meta property="og:type" content="website" />
        <meta property="og:site_name" content="Windrunner" />
        <meta property="og:image" content={`${websiteUrl}img/windrunner-ai-agent-chat.png`} />
        <meta
          property="og:image:alt"
          content="Windrunner AI Agent showing a structured answer in the app"
        />
        <script type="application/ld+json">
          {JSON.stringify(websiteStructuredData)}
        </script>
      </Head>
      <main className="home-main">
        <header className="home-hero">
          <div className="container home-hero-grid">
            <div className="home-hero-copy">
              <p className="home-hero-eyebrow">Self-hosted · AI-native Work Hub</p>
              <h1 className="hero__title">Talk to your work.</h1>
              <p className="hero__subtitle">
                Windrunner turns chat, API and MCP into a structured Work Graph
                — teams, projects, work items and relationships linked so humans
                and agents share one truth. Chat moves work forward; the
                workspace lets you inspect and refine what AI built.
              </p>
              <div className="home-hero-actions">
                <Link
                  className="button button--primary button--lg"
                  to="/docs/getting-started/installation"
                >
                  Get started
                </Link>
                <Link
                  className="button button--secondary button--lg"
                  to="/docs/core-concepts/projects-workspace"
                >
                  Explore the workspace
                </Link>
              </div>
            </div>
            <AgentFlowPreview />
          </div>
        </header>

        <section className="container home-features">
          <h2 className="home-features-title">One workspace for people, progress, and AI</h2>
          <div className="row">
            {collaborationFeatures.map((feature) => (
              <div
                key={feature.title}
                className="col col--3 margin-bottom--lg"
              >
                <FeatureCard {...feature} />
              </div>
            ))}
          </div>
        </section>
        <section className="container home-features home-features--secondary">
          <h2 className="home-features-title">Built to fit your stack</h2>
          <div className="row">
            {stackFeatures.map((feature) => (
              <div
                key={feature.title}
                className="col col--3 margin-bottom--lg"
              >
                <FeatureCard {...feature} />
              </div>
            ))}
          </div>
        </section>
      </main>
    </Layout>
  );
}
