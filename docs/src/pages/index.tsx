import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import {
  Bot,
  Braces,
  Feather,
  KeyRound,
  ListTree,
  Plug,
  Server,
  TrendingUp,
} from 'lucide-react';

const features = [
  {
    icon: KeyRound,
    title: 'Bring your own key',
    description:
      'Plug in OpenAI, Gemini, or Claude with your own API key. You choose the provider — and keep control of cost and data.',
    accent: 'accent-blue',
  },
  {
    icon: ListTree,
    title: 'Tree-based structure',
    description:
      'Work items nest into sub-items, mirroring how projects actually break down — not another flat ticket list.',
    to: '/docs/core-concepts/work-items',
    accent: 'accent-violet',
  },
  {
    icon: Server,
    title: 'Self-hosted',
    description:
      'One JAR, one PostgreSQL database, your infrastructure. No data leaves your network unless you enable an LLM provider.',
    to: '/docs/getting-started/installation',
    accent: 'accent-slate',
  },
  {
    icon: Feather,
    title: 'Great without AI',
    description:
      'Every AI feature is optional. Plan, discuss, link, and track work entirely by hand — the graph stays just as useful.',
    to: '/docs/core-concepts/work-items',
    accent: 'accent-emerald',
  },
  {
    icon: Bot,
    title: 'AI where it helps',
    description:
      'Conservative reviews of work items and entry drafts, plus change proposals you accept or reject — never silent edits.',
    to: '/docs/guides/ai-assistance',
    accent: 'accent-amber',
  },
  {
    icon: TrendingUp,
    title: 'AI metrics with ROI',
    description:
      'See exactly how much time AI assistance saves your team, per feature — so you know what the investment returns.',
    accent: 'accent-cyan',
  },
  {
    icon: Braces,
    title: 'REST API',
    description:
      'A versioned external REST API with scoped API keys. Automate workflows and integrate with the tools you already run.',
    to: '/api',
    accent: 'accent-blue',
  },
  {
    icon: Plug,
    title: 'MCP API & task backend',
    description:
      'Connect AI agents over MCP, or use Windrunner as the task-management backend for your own tools and scripts.',
    to: '/api',
    accent: 'accent-violet',
  },
];

type Feature = (typeof features)[number];

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

export default function Home(): ReactNode {
  return (
    <Layout
      title="Windrunner"
      description="A collaborative outliner that maintains structured knowledge about your team's work."
    >
      <main className="home-main">
        <header className="home-hero">
          <div className="container">
            <h1 className="hero__title">Work that documents itself.</h1>
            <p className="hero__subtitle">
              Windrunner is a collaborative outliner over a structured work
              graph. You write naturally; it maintains the knowledge — who is
              blocked by what, which answer was accepted, and why.
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
        </header>

        <section className="container home-features">
          <h2 className="home-features-title">Why Windrunner</h2>
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
