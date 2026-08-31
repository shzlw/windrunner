import type {ReactNode} from 'react';
import Head from '@docusaurus/Head';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import {
  Bot,
  Braces,
  KeyRound,
  ListTodo,
  ListTree,
  MessageSquareText,
  Plug,
  Server,
  Terminal,
  TrendingUp,
} from 'lucide-react';

const features = [
  {
    icon: MessageSquareText,
    title: 'AI that understands your work',
    description:
      "Ask what's next, what's blocked, or how work is progressing — with project context built in.",
    to: '/docs/guides/ai-assistance',
    accent: 'accent-blue',
  },
  {
    icon: ListTree,
    title: 'Structured project graph',
    description:
      'Work items, entries, and typed relationships connect tasks, decisions, answers, blockers, and supporting context.',
    to: '/docs/core-concepts/relationships',
    accent: 'accent-violet',
  },
  {
    icon: Bot,
    title: 'AI review with approval',
    description:
      'Let AI suggest updates to work items and entries. Suggested changes stay visible until a person accepts or rejects them.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-amber',
  },
  {
    icon: TrendingUp,
    title: 'AI metrics with ROI',
    description:
      'Measure token usage, acceptance rates, and estimated time saved by project, provider, and feature.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-cyan',
  },
  {
    icon: ListTodo,
    title: 'Human-controlled workspace',
    description:
      'Create, organize, assign, search, filter, and follow work directly whenever you want.',
    to: '/docs/guides/following-work',
    accent: 'accent-emerald',
  },
  {
    icon: KeyRound,
    title: 'Multiple LLM providers',
    description:
      'Use OpenAI, Gemini, or Claude with your own credentials and configurable models and limits.',
    to: '/docs/reference/configuration',
    accent: 'accent-indigo',
  },
  {
    icon: Server,
    title: 'Self-hosted',
    description:
      'Deploy with Docker Compose and PostgreSQL, keeping project data in your infrastructure.',
    to: '/docs/getting-started/installation',
    accent: 'accent-slate',
  },
  {
    icon: Braces,
    title: 'REST API',
    description:
      'Use scoped API keys and the versioned API to connect existing tools and automations.',
    to: '/api',
    accent: 'accent-violet',
  },
  {
    icon: Plug,
    title: 'MCP server for AI agents',
    description:
      'Connect Codex, Claude Desktop, or Cursor natively. Let agents search work, read context, record findings, and update status.',
    to: '/docs/reference/mcp',
    accent: 'accent-cyan',
  },
  {
    icon: Terminal,
    title: 'CLI for automation',
    description:
      'Install the Windrunner CLI to work with projects, work items, entries, and search from a terminal or automation.',
    to: '/docs/reference/cli',
    accent: 'accent-blue',
  },
];

type Feature = (typeof features)[number];

const websiteUrl = 'https://shzlw.github.io/windrunner/';
const websiteDescription =
  'AI-powered work management for teams, with connected project context, reviewable AI updates, and support for agents through MCP.';

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
              <p className="home-hero-eyebrow">AI-powered work management for teams</p>
              <h1 className="hero__title">Manage the work. Ask the project.</h1>
              <p className="hero__subtitle">
                Windrunner brings work items, decisions, blockers, and evidence
                together in one place. Teams can manage work directly, ask AI
                what’s next, review proposed updates, and connect AI agents
                through MCP.
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
                  to="/api"
                >
                  API reference
                </Link>
              </div>
            </div>
            <HeroPreview screenshot={workspaceScreenshot} />
          </div>
        </header>

        <section className="container home-features">
          <h2 className="home-features-title">Everything teams need to move work forward</h2>
          <div className="row">
            {features.map((feature) => (
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
