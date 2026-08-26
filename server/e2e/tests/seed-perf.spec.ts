import { APIRequestContext, test, expect } from '@playwright/test';

/**
 * Performance/realism seeding: 2 projects with ~2000 work items each
 * (hierarchy + entries + relationships), plus 50 users and 20 teams.
 *
 * Opt-in — never runs in a normal pass:
 *   SEED_PERF=1 E2E_LOGIN=... E2E_PASSWORD=... npx playwright test --project=api -g "Seed"
 *
 * Tunables: SEED_ITEMS (2000), SEED_USERS (50), SEED_TEAMS (20),
 *           SEED_PROJECTS (2), SEED_CONCURRENCY (16)
 *
 * Deterministic: uses a seeded PRNG so every run produces the same dataset.
 */

test.skip(process.env.SEED_PERF !== '1', 'Set SEED_PERF=1 to run performance seeding');

const PROJECT_COUNT = Number(process.env.SEED_PROJECTS ?? 2);
const WORK_ITEMS_PER_PROJECT = Number(process.env.SEED_ITEMS ?? 2000);
const USER_COUNT = Number(process.env.SEED_USERS ?? 50);
const TEAM_COUNT = Number(process.env.SEED_TEAMS ?? 20);
const CONCURRENCY = Number(process.env.SEED_CONCURRENCY ?? 16);

// ---------- deterministic RNG ----------
function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(42);

const pick = <T>(items: readonly T[]): T => items[Math.floor(rng() * items.length)];
const chance = (probability: number) => rng() < probability;
function weighted<T extends readonly string[]>(items: T, weights: number[]): T {
  const total = weights.reduce((sum, w) => sum + w, 0);
  let roll = rng() * total;
  for (let i = 0; i < items.length; i++) {
    roll -= weights[i];
    if (roll <= 0) return items[i];
  }
  return items[items.length - 1];
}

// ---------- realistic data pools ----------
const TYPES = ['TASK', 'TASK', 'TASK', 'QUESTION', 'APPROVAL', 'REVIEW', 'DECISION'] as const;
const STATUSES = ['OPEN', 'OPEN', 'IN_PROGRESS', 'IN_PROGRESS', 'DONE', 'DONE', 'BLOCKED'] as const;
const PRIORITIES = ['MEDIUM', 'MEDIUM', 'HIGH', 'LOW', 'URGENT'] as const;
const ENTRY_TYPES = ['COMMENT', 'COMMENT', 'INFORMATION', 'EVIDENCE', 'ANSWER', 'RESOLUTION'] as const;
const RELATIONSHIP_TYPES = ['BLOCKED_BY', 'DEPENDS_ON', 'RELATED_TO'] as const;

const FEATURES = [
  'login flow', 'billing export', 'search ranking', 'notification digest',
  'audit retention', 'team invitations', 'API rate limits', 'dashboard filters',
  'file attachments', 'mobile layout', 'data migration', 'SSO integration',
  'webhook delivery', 'report scheduling', 'permission matrix', 'cache warming',
];
const OBJECTS = [
  'service', 'endpoint', 'page', 'worker', 'migration', 'policy',
  'pipeline', 'component', 'query', 'integration',
];
const VERBS = ['Implement', 'Refactor', 'Investigate', 'Fix', 'Document', 'Ship', 'Harden'];

function workItemTitle() {
  if (chance(0.15)) {
    return `${pick(['Should we', 'Can we', 'Do we need to'])} ${pick(['support SSO', 'migrate the cache', 'split the service', 'deprecate v1'])}?`;
  }
  return `${pick(VERBS)} ${pick(FEATURES)} ${pick(OBJECTS)}`;
}

function entryBody(itemTitle: string) {
  const templates = [
    `Looked into "${itemTitle}" today. The initial approach works but needs review before we commit.`,
    'Added reproduction steps and linked the failing pipeline. Root cause is still unclear.',
    'Discussed in sync — we agree on scope, waiting on security sign-off.',
    'Benchmark results attached separately: latency is acceptable at current volume.',
    'Blocked until upstream dependency ships; revisit next sprint.',
    'Verified the fix in staging. Ready for approval.',
  ];
  return pick(templates);
}

function isoDate(daysFromNow: number) {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  return date.toISOString().slice(0, 10);
}

function randomDueDate() {
  // Spread across -30..+90 days; ~15% overdue.
  return isoDate(Math.floor((rng() * 120) - 30));
}

async function mapPool<T, R>(
  items: T[],
  size: number,
  worker: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let cursor = 0;
  async function lane() {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index], index);
    }
  }
  await Promise.all(Array.from({length: Math.min(size, items.length)}, lane));
  return results;
}

let created = {users: 0, teams: 0, projects: 0, workItems: 0, entries: 0, relationships: 0};

test('Seed Windrunner with realistic multi-project data', async ({request}, testInfo) => {
  test.setTimeout(5 * 60 * 1000);
  const startedAt = Date.now();

  // ---------- login ----------
  const login = process.env.E2E_LOGIN;
  const password = process.env.E2E_PASSWORD;
  test.skip(!login || !password, 'Set E2E_LOGIN/E2E_PASSWORD');
  const loginResponse = await request.post('/api/v1/auth/login', {
    data: {login, password},
  });
  expect(loginResponse.status()).toBe(200);
  const adminId = (await (await request.get('/api/v1/auth/me')).json()).data.id;

  let csrfToken: string | undefined;
  const state = await request.storageState();
  csrfToken = state.cookies.find(cookie => cookie.name === 'XSRF-TOKEN')?.value;

  const headers = () => (csrfToken ? { 'X-CSRF-Token': csrfToken } : {});
  const post = async (url: string, data: unknown) => {
    const response = await request.post(url, {data, headers: headers()});
    expect(response.status(), `POST ${url} → ${JSON.stringify(await response.json().catch(() => null))}`)
        .toBeLessThan(500);
    return response;
  };
  const put = async (url: string, data: unknown) => {
    const response = await request.put(url, {data, headers: headers()});
    expect(response.status(), `PUT ${url}`).toBeLessThan(500);
    return response;
  };

  // ---------- users ----------
  const FIRST = ['Ana', 'Ben', 'Chen', 'Dana', 'Elif', 'Finn', 'Gita', 'Hugo', 'Ines', 'Jonas'];
  const LAST = ['Kim', 'Reyes', 'Okafor', 'Silva', 'Novak', 'Weber', 'Tanaka', 'Costa'];
  const userBodies = Array.from({length: USER_COUNT}, (_, i) => ({
    username: `e2e_${FIRST[i % FIRST.length].toLowerCase()}.${LAST[Math.floor(i / FIRST.length)].toLowerCase()}${i}`,
    displayName: `${FIRST[i % FIRST.length]} ${LAST[Math.floor(i / FIRST.length)]} ${i}`,
    email: `e2e_user${i}@example.com`,
    password: `e2e-pass-${1000 + i}`,
    timezone: 'UTC',
    status: 'ACTIVE',
    globalRole: 'USER',
  }));
  const userResponses = await mapPool(userBodies, CONCURRENCY,
    body => post('/internal-api/v1/users', body));
  const userIds = (await Promise.all(userResponses.map(r => r.json())))
    .map(body => body.data.id)
    .filter(Boolean);
  created.users = userIds.length;

  // ---------- teams ----------
  const teamIds: string[] = [];
  for (let i = 0; i < TEAM_COUNT; i++) {
    const response = await post('/internal-api/v1/teams', {
      name: `e2e Team ${String.fromCharCode(65 + (i % 26))}${Math.floor(i / 26) || ''} — ${pick(FEATURES)} guild`,
      ownerUserIds: [adminId],
      ownerTeamIds: [],
    });
    teamIds.push((await response.json()).data.id);
  }
  created.teams = teamIds.length;

  // each team gets 3–8 members
  await mapPool(teamIds, CONCURRENCY, async teamId => {
    const memberCount = 3 + Math.floor(rng() * 6);
    const members = new Set<string>();
    while (members.size < memberCount) members.add(pick(userIds));
    await mapPool([...members], CONCURRENCY,
      userId => post(`/internal-api/v1/teams/${teamId}/members`, {userId}));
  });

  // ---------- projects ----------
  const projectIds: string[] = [];
  for (let i = 0; i < PROJECT_COUNT; i++) {
    const response = await post('/internal-api/v1/projects', {
      name: `e2e ${['Atlas', 'Beacon'][i % 2]} — ${pick(FEATURES)} program`,
      ownerUserIds: [adminId],
      ownerTeamIds: [teamIds[i % TEAM_COUNT]],
    });
    projectIds.push((await response.json()).data.id);
  }
  created.projects = projectIds.length;

  // link a handful of users to each project directly
  await mapPool(projectIds, CONCURRENCY, async projectId => {
    const members = new Set<string>();
    while (members.size < 10) members.add(pick(userIds));
    await mapPool([...members], CONCURRENCY, userId =>
      put(`/internal-api/v1/projects/${projectId}/members/${userId}`, {
        role: chance(0.8) ? 'EDITOR' : 'VIEWER',
      }));
  });

  // ---------- work items (hierarchy) ----------
  type Created = {id: string; title: string; status: string};
  const allByProject = new Map<string, Created[]>();

  for (const projectId of projectIds) {
    const base = `/internal-api/v1/projects/${projectId}/work-items`;
    const createdItems: Created[] = [];

    // Level 0: roots (~5% of total)
    const rootCount = Math.max(6, Math.round(WORK_ITEMS_PER_PROJECT * 0.04));
    const roots = await mapPool(Array.from({length: rootCount}), CONCURRENCY, async (_, i) => {
      const response = await post(base, {
        workItem: {
          title: `${pick(FEATURES)} program phase ${i + 1}`,
          type: i % 5 === 0 ? 'DECISION' : 'TASK',
          status: 'OPEN',
          priority: pick(PRIORITIES),
        },
        assignees: [],
      });
      return (await response.json()).data.workItem as Created;
    });
    createdItems.push(...roots);

    // Descendants: BFS until target reached, depth ≤ 3, 2–6 children per node
    let frontier = [...roots];
    while (createdItems.length < WORK_ITEMS_PER_PROJECT && frontier.length > 0) {
      const remaining = WORK_ITEMS_PER_PROJECT - createdItems.length;
      const parents = frontier.splice(0, Math.min(frontier.length, remaining));
      const batches = await mapPool(parents, CONCURRENCY, async parent => {
        const childTarget = Math.min(
          remaining,
          2 + Math.floor(rng() * 5),
        );
        const children: Created[] = [];
        for (let c = 0; c < childTarget && createdItems.length + children.length < WORK_ITEMS_PER_PROJECT; c++) {
          const type = weighted(TYPES, [7, 3, 1, 1, 1]);
          const status = weighted(STATUSES, [4, 2, 2, 2, 1]);
          const assignees = chance(0.35)
            ? [{assigneeType: 'USER', assigneeId: pick(userIds)}]
            : [];
          const body = {
            workItem: {
              title: workItemTitle(),
              type,
              status,
              priority: pick(PRIORITIES),
              dueDate: chance(0.55) ? randomDueDate() : null,
              parentWorkItemId: parent.id,
            },
            assignees,
          };
          const response = await post(base, body);
          if (response.status() === 201) {
            children.push(((await response.json()).data.workItem) as Created);
          }
        }
        return children;
      });
      for (const batch of batches) createdItems.push(...batch);
      frontier = batches.flat();
    }

    allByProject.set(projectId, createdItems);
    created.workItems += createdItems.length;
  }

  // ---------- entries ----------
  for (const [projectId, items] of allByProject) {
    const entryTargets = items.filter(() => chance(0.5)).flatMap(item =>
      Array.from({length: 1 + Math.floor(rng() * 3)}, () => item));
    await mapPool(entryTargets, CONCURRENCY, target =>
      post(`/internal-api/v1/projects/${projectId}/entries`, {
        workItemId: target.id,
        type: pick(ENTRY_TYPES),
        body: entryBody(target.title),
      }));
    created.entries += entryTargets.length;
  }

  // ---------- relationships ----------
  for (const [projectId, items] of allByProject) {
    const relationshipCount = Math.round(items.length * 0.15);
    const pairs = Array.from({length: relationshipCount}, () => {
      const from = pick(items);
      const to = pick(items);
      return {from, to};
    }).filter(({from, to}) => from.id !== to.id);

    await mapPool(pairs, CONCURRENCY, ({from, to}) =>
      post(`/internal-api/v1/projects/${projectId}/relationships`, {
        fromEntityType: 'WORK_ITEM',
        fromEntityId: from.id,
        toEntityType: 'WORK_ITEM',
        toEntityId: to.id,
        type: pick(RELATIONSHIP_TYPES),
        reason: chance(0.6) ? 'Discovered during planning' : null,
      }).then(r => r.status()));
    created.relationships += pairs.length;
  }

  const seconds = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`Seeded in ${seconds}s →`, JSON.stringify(created));
  expect(created.workItems).toBeGreaterThanOrEqual(WORK_ITEMS_PER_PROJECT * PROJECT_COUNT - PROJECT_COUNT);
});
