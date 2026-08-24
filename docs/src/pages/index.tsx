import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import {
  Bot,
  Braces,
  KeyRound,
  ListTodo,
  ListTree,
  MessageSquareText,
  Server,
  TrendingUp,
} from 'lucide-react';

const features = [
  {
    icon: MessageSquareText,
    title: 'Project-level AI chat',
    description:
      "Ask what's next, what's blocked, or how a project is going — and let AI propose changes for you to review.",
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
      'Review work items and entry drafts with AI. Suggested changes stay visible until a user accepts or rejects them.',
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
    title: 'Full manual workspace',
    description:
      'Create, organize, assign, search, filter, and follow work without relying on AI.',
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

function HeroPreview(): ReactNode {
  return (
    <div
      className="home-hero-visual"
      role="img"
      aria-label="Example of asking a project about its blockers"
    >
      <div className="home-hero-glow" />
      <div className="home-hero-window">
        <div className="home-hero-window-bar">
          <div className="home-hero-window-dots" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <span className="home-hero-window-label">Project workspace</span>
          <span className="home-hero-window-status">AI enabled</span>
        </div>

        <div className="home-hero-question">
          <div className="home-hero-question-icon">
            <MessageSquareText size={17} strokeWidth={2} />
          </div>
          <div>
            <span>Ask the project</span>
            <strong>What is blocking the launch?</strong>
          </div>
        </div>

        <div className="home-hero-answer">
          <div className="home-hero-answer-heading">
            <Bot size={17} strokeWidth={2} />
            <span>Project answer</span>
          </div>
          <p>Two items are blocked by the API decision. One owner is waiting for an accepted answer.</p>
          <div className="home-hero-answer-tags">
            <span>2 blockers</span>
            <span>1 decision</span>
            <span>Review context</span>
          </div>
        </div>

        <div className="home-hero-graph">
          <div className="home-hero-graph-heading">
            <span>Structured project graph</span>
            <ListTree size={16} strokeWidth={2} />
          </div>
          <div className="home-hero-graph-map" aria-hidden="true">
            <div className="home-hero-graph-node home-hero-graph-node--primary">Launch project</div>
            <div className="home-hero-graph-node">API decision</div>
            <div className="home-hero-graph-node">Blocked task</div>
            <div className="home-hero-graph-node">Accepted answer</div>
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
      description="A self-hosted project workspace for clearer progress, faster decisions, and less status chasing."
    >
      <main className="home-main">
        <header className="home-hero">
          <div className="container home-hero-grid">
            <div className="home-hero-copy">
              <p className="home-hero-eyebrow">Structured work · Project AI</p>
              <h1 className="hero__title">Manage the work. Ask the project.</h1>
              <p className="hero__subtitle">
                Windrunner combines a structured project workspace with
                project-level AI. Teams can manage work directly or use natural
                language to share updates, find blockers, understand progress,
                and make decisions with less back-and-forth.
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
            <HeroPreview />
          </div>
        </header>

        <section className="container home-features">
          <h2 className="home-features-title">What Windrunner provides today</h2>
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
