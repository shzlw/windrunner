export type SeedTeam = {
  name: string;
  description: string;
  titles: readonly string[];
};

export type SeedWorkstream = {
  name: string;
  objective: string;
  team: string;
  subjects: readonly string[];
  contexts: readonly string[];
  blockerReasons: readonly string[];
};

export type SeedScenario = {
  name: string;
  ownerTeam: string;
  teamAccess: readonly {team: string; role: 'OWNER' | 'EDITOR' | 'VIEWER'}[];
  workstreams: readonly SeedWorkstream[];
};

export const SEED_TEAMS: readonly SeedTeam[] = [
  {name: 'Identity Platform', description: 'Owns authentication, authorization, account linking, and enterprise identity integrations.', titles: ['Identity engineer', 'Backend engineer', 'Engineering manager', 'Security engineer']},
  {name: 'Application Engineering', description: 'Builds customer-facing workflows and the shared product application.', titles: ['Frontend engineer', 'Backend engineer', 'Staff engineer', 'Engineering manager']},
  {name: 'Site Reliability', description: 'Owns reliability, observability, incident response, and production readiness.', titles: ['Site reliability engineer', 'Platform engineer', 'Incident manager', 'Engineering manager']},
  {name: 'Product Management', description: 'Owns product direction, discovery, prioritization, and measurable customer outcomes.', titles: ['Product manager', 'Senior product manager', 'Product operations manager', 'Director of product']},
  {name: 'Security & Compliance', description: 'Maintains security controls, risk reviews, privacy practices, and audit readiness.', titles: ['Security engineer', 'Compliance analyst', 'Privacy counsel', 'Security program manager']},
  {name: 'Customer Support', description: 'Resolves customer issues and turns recurring support signals into product improvements.', titles: ['Support engineer', 'Technical support specialist', 'Support operations manager', 'Customer advocate']},
  {name: 'Quality Engineering', description: 'Owns test strategy, release confidence, regression coverage, and quality signals.', titles: ['QA engineer', 'Automation engineer', 'Test lead', 'Quality engineering manager']},
  {name: 'Data Platform', description: 'Owns analytics pipelines, data quality, reporting infrastructure, and operational insights.', titles: ['Data engineer', 'Analytics engineer', 'Data analyst', 'Data platform manager']},
  {name: 'Finance Operations', description: 'Owns billing operations, reconciliation, revenue controls, and financial reporting.', titles: ['Billing operations analyst', 'Revenue accountant', 'Finance systems manager', 'Financial controller']},
  {name: 'Customer Success', description: 'Guides customer adoption, rollout planning, and measurable business outcomes.', titles: ['Customer success manager', 'Implementation manager', 'Solutions consultant', 'Customer programs lead']},
  {name: 'Developer Experience', description: 'Improves build systems, local development, CI performance, and engineering productivity.', titles: ['Developer experience engineer', 'Build engineer', 'Tools engineer', 'Engineering productivity lead']},
  {name: 'API Platform', description: 'Owns public APIs, integrations, rate limits, webhooks, and developer documentation.', titles: ['API engineer', 'Integration engineer', 'Technical writer', 'API product manager']},
  {name: 'Mobile Experience', description: 'Builds and operates the mobile customer experience across supported platforms.', titles: ['iOS engineer', 'Android engineer', 'Mobile QA engineer', 'Mobile engineering manager']},
  {name: 'Design Systems', description: 'Maintains accessible interface patterns, reusable components, and product design standards.', titles: ['Product designer', 'Design systems engineer', 'Accessibility specialist', 'Design lead']},
  {name: 'Growth Engineering', description: 'Builds acquisition, activation, lifecycle, and experimentation capabilities.', titles: ['Growth engineer', 'Lifecycle product manager', 'Experimentation analyst', 'Growth design lead']},
  {name: 'Infrastructure', description: 'Owns cloud foundations, networking, compute platforms, and infrastructure automation.', titles: ['Infrastructure engineer', 'Cloud architect', 'Network engineer', 'Infrastructure manager']},
  {name: 'Database Reliability', description: 'Owns database performance, backup strategy, schema operations, and data resilience.', titles: ['Database reliability engineer', 'Database administrator', 'Performance engineer', 'Data reliability manager']},
  {name: 'Release Management', description: 'Coordinates release readiness, change windows, rollout controls, and launch communication.', titles: ['Release manager', 'Technical program manager', 'Change manager', 'Release engineer']},
  {name: 'Legal Operations', description: 'Coordinates contracts, policy reviews, regulatory obligations, and legal operations.', titles: ['Legal operations manager', 'Commercial counsel', 'Privacy counsel', 'Contract specialist']},
  {name: 'Sales Engineering', description: 'Supports technical evaluations, solution design, and enterprise buying decisions.', titles: ['Sales engineer', 'Solutions architect', 'Technical account manager', 'Sales engineering manager']},
] as const;

export const SEED_SCENARIOS: readonly SeedScenario[] = [
  {
    name: 'Enterprise SSO Launch',
    ownerTeam: 'Identity Platform',
    teamAccess: [
      {team: 'Identity Platform', role: 'OWNER'},
      {team: 'Security & Compliance', role: 'EDITOR'},
      {team: 'Quality Engineering', role: 'EDITOR'},
      {team: 'Customer Success', role: 'EDITOR'},
      {team: 'Customer Support', role: 'VIEWER'},
    ],
    workstreams: [
      {name: 'Identity provider integration', objective: 'Deliver secure SAML and OIDC connectivity for enterprise identity providers.', team: 'Identity Platform', subjects: ['SAML metadata exchange', 'OIDC discovery', 'certificate rotation', 'account linking', 'just-in-time provisioning'], contexts: ['Okta pilot', 'Microsoft Entra ID pilot', 'Google Workspace tenant', 'multi-domain customer', 'staging environment'], blockerReasons: ['Waiting for customer metadata', 'Security review has not completed', 'Provider test tenant is unavailable']},
      {name: 'Security and privacy review', objective: 'Validate the authentication design, audit trail, and data-handling controls.', team: 'Security & Compliance', subjects: ['threat model', 'session policy', 'audit event coverage', 'domain verification', 'privacy review'], contexts: ['enterprise pilot', 'production rollout', 'administrator workflow', 'break-glass access', 'regional deployment'], blockerReasons: ['Threat model requires revision', 'Control evidence is incomplete', 'Privacy guidance is pending']},
      {name: 'Pilot customer rollout', objective: 'Prepare selected customers and internal teams for a controlled SSO launch.', team: 'Customer Success', subjects: ['pilot tenant setup', 'administrator training', 'migration checklist', 'support handoff', 'launch communication'], contexts: ['Acme Health', 'Northstar Bank', 'Lumen Retail', 'EMEA administrators', 'customer success team'], blockerReasons: ['Customer administrator has not confirmed the window', 'Training material needs approval', 'Migration owner is not assigned']},
      {name: 'Release confidence', objective: 'Prove compatibility, failure recovery, and safe rollout behavior before general availability.', team: 'Quality Engineering', subjects: ['login regression suite', 'fallback authentication', 'certificate expiry behavior', 'role mapping', 'rollback exercise'], contexts: ['staging', 'pilot production', 'mobile login', 'administrator console', 'high-availability region'], blockerReasons: ['Regression failure is unresolved', 'Test data is incomplete', 'Release candidate is not deployed']},
    ],
  },
  {
    name: 'Billing Accuracy and Invoice Recovery',
    ownerTeam: 'Finance Operations',
    teamAccess: [
      {team: 'Finance Operations', role: 'OWNER'},
      {team: 'Application Engineering', role: 'EDITOR'},
      {team: 'Data Platform', role: 'EDITOR'},
      {team: 'Customer Support', role: 'EDITOR'},
      {team: 'Quality Engineering', role: 'VIEWER'},
    ],
    workstreams: [
      {name: 'Invoice calculation integrity', objective: 'Correct invoice calculations and make pricing outcomes reproducible.', team: 'Application Engineering', subjects: ['proration calculation', 'tax rounding', 'discount application', 'usage aggregation', 'credit balance handling'], contexts: ['annual plan renewal', 'mid-cycle upgrade', 'EU invoice', 'high-volume account', 'multi-currency customer'], blockerReasons: ['Pricing rule is ambiguous', 'Reproduction case is incomplete', 'Tax provider response is inconsistent']},
      {name: 'Revenue reconciliation', objective: 'Reconcile billing events, invoices, payments, and ledger records.', team: 'Finance Operations', subjects: ['payment reconciliation', 'credit memo review', 'ledger variance', 'failed collection', 'revenue recognition check'], contexts: ['month-end close', 'enterprise accounts', 'USD ledger', 'EUR ledger', 'reseller channel'], blockerReasons: ['Ledger export is delayed', 'Finance approval is pending', 'Source transaction cannot be matched']},
      {name: 'Billing data quality', objective: 'Detect and prevent missing, duplicated, or delayed billing events.', team: 'Data Platform', subjects: ['usage event completeness', 'duplicate event detection', 'invoice dataset freshness', 'reconciliation dashboard', 'billing anomaly alert'], contexts: ['production pipeline', 'daily close', 'month-end volume', 'regional warehouse', 'backfill job'], blockerReasons: ['Warehouse backfill is still running', 'Data contract change is not deployed', 'Source event ownership is unclear']},
      {name: 'Customer recovery', objective: 'Resolve affected accounts and communicate accurate billing outcomes.', team: 'Customer Support', subjects: ['affected account review', 'customer credit', 'support response', 'invoice correction', 'root-cause communication'], contexts: ['Acme Health', 'Northstar Bank', 'Lumen Retail', 'strategic accounts', 'open support cases'], blockerReasons: ['Corrected invoice is not available', 'Customer approval is pending', 'Legal wording needs review']},
    ],
  },
  {
    name: 'Customer Notification Reliability',
    ownerTeam: 'Site Reliability',
    teamAccess: [
      {team: 'Site Reliability', role: 'OWNER'},
      {team: 'Application Engineering', role: 'EDITOR'},
      {team: 'Data Platform', role: 'EDITOR'},
      {team: 'Customer Support', role: 'EDITOR'},
      {team: 'Release Management', role: 'VIEWER'},
    ],
    workstreams: [
      {name: 'Delivery pipeline resilience', objective: 'Keep email, push, and webhook notifications flowing during load and partial failure.', team: 'Site Reliability', subjects: ['queue saturation', 'worker autoscaling', 'retry policy', 'dead-letter recovery', 'regional failover'], contexts: ['email delivery', 'mobile push', 'webhook delivery', 'EU region', 'peak traffic'], blockerReasons: ['Capacity test has not completed', 'Infrastructure change awaits approval', 'Provider incident remains active']},
      {name: 'Notification correctness', objective: 'Send the right content to the right recipients exactly once.', team: 'Application Engineering', subjects: ['recipient selection', 'template rendering', 'preference enforcement', 'deduplication', 'schedule calculation'], contexts: ['security alerts', 'weekly digest', 'billing reminders', 'workflow assignments', 'incident updates'], blockerReasons: ['Product behavior is not decided', 'Template copy needs approval', 'Preference migration is incomplete']},
      {name: 'Delivery observability', objective: 'Provide actionable delivery signals from request through provider outcome.', team: 'Data Platform', subjects: ['delivery dashboard', 'latency percentile', 'provider error taxonomy', 'customer-level trace', 'alert threshold'], contexts: ['production', 'provider failover', 'enterprise tenant', 'daily operations', 'incident response'], blockerReasons: ['Telemetry field is missing', 'Dashboard query is too expensive', 'Alert ownership is not confirmed']},
      {name: 'Customer incident response', objective: 'Reduce time to detect, explain, and resolve customer notification failures.', team: 'Customer Support', subjects: ['support runbook', 'incident communication', 'affected tenant search', 'delivery replay', 'post-incident follow-up'], contexts: ['priority customer', 'regional outage', 'provider degradation', 'missed digest', 'failed webhook'], blockerReasons: ['Impact list is incomplete', 'Replay safety has not been verified', 'Incident commander approval is pending']},
    ],
  },
  {
    name: 'SOC 2 Audit Readiness',
    ownerTeam: 'Security & Compliance',
    teamAccess: [
      {team: 'Security & Compliance', role: 'OWNER'},
      {team: 'Infrastructure', role: 'EDITOR'},
      {team: 'Identity Platform', role: 'EDITOR'},
      {team: 'Release Management', role: 'EDITOR'},
      {team: 'Legal Operations', role: 'VIEWER'},
    ],
    workstreams: [
      {name: 'Access control evidence', objective: 'Demonstrate consistent provisioning, review, and removal of production access.', team: 'Identity Platform', subjects: ['quarterly access review', 'privileged role inventory', 'termination workflow', 'service account ownership', 'break-glass access'], contexts: ['production systems', 'cloud console', 'customer database', 'CI environment', 'support tooling'], blockerReasons: ['Evidence owner is not assigned', 'Access export is incomplete', 'Reviewer sign-off is pending']},
      {name: 'Change management controls', objective: 'Show that production changes are reviewed, tested, approved, and traceable.', team: 'Release Management', subjects: ['release approval', 'change ticket linkage', 'rollback evidence', 'emergency change review', 'deployment history'], contexts: ['weekly release', 'infrastructure change', 'database migration', 'security patch', 'hotfix process'], blockerReasons: ['Deployment record is missing', 'Approval evidence needs correction', 'Change owner has not responded']},
      {name: 'Infrastructure control testing', objective: 'Validate backups, monitoring, vulnerability management, and resilience controls.', team: 'Infrastructure', subjects: ['backup restoration', 'vulnerability remediation', 'monitoring coverage', 'disaster recovery exercise', 'encryption configuration'], contexts: ['primary region', 'recovery region', 'production database', 'object storage', 'container platform'], blockerReasons: ['Control test failed', 'Remediation date is not agreed', 'Recovery environment is unavailable']},
      {name: 'Audit coordination', objective: 'Keep evidence requests, owner responses, and auditor follow-ups on schedule.', team: 'Security & Compliance', subjects: ['evidence request', 'control narrative', 'auditor sample', 'management response', 'exception review'], contexts: ['access controls', 'change controls', 'availability controls', 'vendor management', 'risk assessment'], blockerReasons: ['Auditor clarification is pending', 'Control owner response is overdue', 'Exception needs management approval']},
    ],
  },
] as const;

export const PERSON_FIRST_NAMES = [
  'Ana', 'Ben', 'Chen', 'Dana', 'Elif', 'Finn', 'Gita', 'Hugo', 'Ines', 'Jonas',
  'Kira', 'Luis', 'Maya', 'Noah', 'Priya',
] as const;

export const PERSON_LAST_NAMES = [
  'Kim', 'Reyes', 'Okafor', 'Silva', 'Novak', 'Weber', 'Tanaka', 'Costa',
  'Patel', 'Morgan', 'Ibrahim', 'Nguyen',
] as const;
