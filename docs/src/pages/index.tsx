import type {ReactNode} from 'react';
import Head from '@docusaurus/Head';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import {
  Bot,
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
    title: 'Ask AI with context',
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
      'Create, switch between, rename, delete, and continue separate Ask AI conversations.',
    to: '/docs/guides/home-and-ask-ai',
    accent: 'accent-emerald',
  },
  {
    icon: ListTree,
    title: 'Split workspace views',
    description:
      'Keep Ask AI and the selected project or artifact visible together with adaptive workspace layouts.',
    to: '/docs/guides/home-and-ask-ai',
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
  'An AI-powered project workspace where teams coordinate work, track progress, and review contextual AI updates in one place.';

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

function HeroPreview({screenshot}: {screenshot: string}): ReactNode {
  return (
    <div className="home-hero-visual">
      <a
        className="home-hero-real-shot"
        href={screenshot}
        target="_blank"
        rel="noreferrer"
        title="Open full-size workspace screenshot in a new tab"
      >
        <img
          src={screenshot}
          alt="Windrunner project workspace with work items, relationships, and the details inspector"
          loading="eager"
          decoding="async"
        />
      </a>
    </div>
  );
}

export default function Home(): ReactNode {
  const workspaceScreenshot = useBaseUrl('/img/windrunner-project-workspace.png');

  return (
    <Layout
      title="Windrunner"
      description={websiteDescription}
    >
      <Head>
        <meta property="og:type" content="website" />
        <meta property="og:site_name" content="Windrunner" />
        <meta property="og:image" content={`${websiteUrl}img/windrunner-project-workspace.png`} />
        <meta
          property="og:image:alt"
          content="Windrunner project workspace with work items, relationships, and the details inspector"
        />
        <script type="application/ld+json">
          {JSON.stringify(websiteStructuredData)}
        </script>
      </Head>
      <main className="home-main">
        <header className="home-hero">
          <div className="container home-hero-grid">
            <div className="home-hero-copy">
              <p className="home-hero-eyebrow">AI-powered project collaboration</p>
              <h1 className="hero__title">Work with your team and AI. Keep every project moving.</h1>
              <p className="hero__subtitle">
                Windrunner brings work items, decisions, blockers, evidence, and
                progress into one shared workspace — so teams can coordinate work,
                ask questions in context, and review AI-proposed updates.
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
            <HeroPreview screenshot={workspaceScreenshot} />
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
