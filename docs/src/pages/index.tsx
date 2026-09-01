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
    title: 'Shared project workspace',
    description:
      'Keep work items, decisions, answers, blockers, and supporting context together in one shared workspace.',
    to: '/docs/core-concepts/projects-workspace',
    accent: 'accent-violet',
  },
  {
    icon: TrendingUp,
    title: 'Progress at a glance',
    description:
      'See status, priority, due dates, assignments, blockers, and activity without losing the project context behind them.',
    to: '/docs/core-concepts/projects-workspace',
    accent: 'accent-cyan',
  },
  {
    icon: MessageSquareText,
    title: 'AI that understands the project',
    description:
      "Ask what's next, what's blocked, or how work is progressing with the relevant project context built in.",
    to: '/docs/guides/ai-assistance',
    accent: 'accent-blue',
  },
  {
    icon: Bot,
    title: 'Reviewable AI updates',
    description:
      'AI can suggest updates to work items and entries, while people stay in control of what gets accepted.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-amber',
  },
  {
    icon: ListTodo,
    title: 'Stay aligned',
    description:
      'Create, organize, assign, search, filter, and follow work so everyone knows what needs attention.',
    to: '/docs/guides/following-work',
    accent: 'accent-emerald',
  },
  {
    icon: KeyRound,
    title: 'Teams and access',
    description:
      'Give the right people access to projects and keep collaboration focused on the work that matters.',
    to: '/docs/guides/users-and-teams',
    accent: 'accent-indigo',
  },
];

const stackFeatures = [
  {
    icon: Bot,
    title: 'Bring your own AI provider',
    description:
      'Use supported providers with your own credentials, models, and limits.',
    to: '/docs/reference/configuration',
    accent: 'accent-indigo',
  },
  {
    icon: TrendingUp,
    title: 'Measure AI impact',
    description:
      'Track usage, acceptance rates, and estimated time saved by project, provider, and feature.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-cyan',
  },
  {
    icon: Plug,
    title: 'Connect agents through MCP',
    description:
      'Let Codex, Claude Desktop, Cursor, and other agents search work, read context, record findings, and update status.',
    to: '/docs/reference/mcp',
    accent: 'accent-violet',
  },
  {
    icon: Terminal,
    title: 'Automate with API and CLI',
    description:
      'Connect existing tools and automate projects, work items, entries, and search with REST and the CLI.',
    to: '/api',
    accent: 'accent-blue',
  },
  {
    icon: Server,
    title: 'Self-host your workspace',
    description:
      'Deploy with Docker Compose and PostgreSQL, keeping project data in your infrastructure.',
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
