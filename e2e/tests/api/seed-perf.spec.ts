import {expect, test} from '@playwright/test';
import type {APIResponse} from '@playwright/test';
import {
  PERSON_FIRST_NAMES,
  PERSON_LAST_NAMES,
  SEED_SCENARIOS,
  SEED_TEAMS,
  type SeedScenario,
  type SeedTeam,
  type SeedWorkstream,
} from '../support/seed-scenarios';

/**
 * Scenario-driven performance seeding. It creates a coherent workspace that is
 * useful for demos and Ask AI while retaining enough volume for pagination and
 * performance testing.
 *
 * Opt-in — never runs in a normal pass:
 *   SEED_PERF=1 E2E_LOGIN=... E2E_PASSWORD=... npx playwright test --project=api -g "Seed"
 *
 * Visible records use stable, human names. Reset the database before a normal
 * run. Set SEED_NAME_SUFFIX explicitly only when multiple seed sets must coexist.
 */

test.skip(process.env.SEED_PERF !== '1', 'Set SEED_PERF=1 to run performance seeding');

function positiveInteger(name: string, fallback: number) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer`);
  return value;
}

const PROJECT_COUNT = positiveInteger('SEED_PROJECTS', 2);
const WORK_ITEMS_PER_PROJECT = positiveInteger('SEED_ITEMS', 2000);
const USER_COUNT = positiveInteger('SEED_USERS', 50);
const REQUESTED_TEAM_COUNT = positiveInteger('SEED_TEAMS', 20);
const CONCURRENCY = positiveInteger('SEED_CONCURRENCY', 4);
const NAME_SUFFIX = (process.env.SEED_NAME_SUFFIX ?? '').trim();

const TASK_VERBS = ['Implement', 'Validate', 'Document', 'Harden', 'Test', 'Prepare', 'Reconcile', 'Automate'] as const;
const TITLE_SCOPES = [
  'ahead of the planned rollout', 'to confirm delivery readiness', 'as part of the operating runbook',
  'to support production adoption', 'before the next release', 'for stakeholder validation',
] as const;
const PRIORITIES = ['MEDIUM', 'HIGH', 'MEDIUM', 'LOW', 'HIGH', 'URGENT'] as const;

type WorkItemType = 'TASK' | 'QUESTION' | 'APPROVAL' | 'REVIEW' | 'DECISION';
type WorkItemStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED' | 'ANSWERED' | 'PENDING' | 'APPROVED';
type Assignee = {assigneeType: 'USER' | 'TEAM'; assigneeId: string};
type CreatedItem = {
  id: string;
  title: string;
  type: WorkItemType;
  status: WorkItemStatus;
  streamIndex: number;
  sequence: number;
  depth: number;
};
type PlannedItem = Omit<CreatedItem, 'id'> & {
  parentId: string;
  priority: string;
  dueDate: string | null;
  assignees: Assignee[];
};

function visibleName(name: string) {
  return NAME_SUFFIX ? `${name} — ${NAME_SUFFIX}` : name;
}

function identifierSuffix() {
  if (!NAME_SUFFIX) return '';
  const normalized = NAME_SUFFIX.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '');
  return normalized ? `.${normalized}` : '';
}

function isoDate(daysFromNow: number) {
  const date = new Date();
  date.setUTCHours(12, 0, 0, 0);
  date.setUTCDate(date.getUTCDate() + daysFromNow);
  return date.toISOString().slice(0, 10);
}

function workItemType(sequence: number): WorkItemType {
  if (sequence % 19 === 0) return 'DECISION';
  if (sequence % 13 === 0) return 'APPROVAL';
  if (sequence % 11 === 0) return 'REVIEW';
  if (sequence % 7 === 0) return 'QUESTION';
  return 'TASK';
}

function workItemStatus(type: WorkItemType, sequence: number): WorkItemStatus {
  if (type === 'QUESTION') return sequence % 2 === 0 ? 'ANSWERED' : 'OPEN';
  if (type === 'APPROVAL') return sequence % 3 === 0 ? 'APPROVED' : 'PENDING';
  if (type === 'DECISION') return sequence % 4 === 0 ? 'APPROVED' : 'PENDING';
  if (type === 'REVIEW') return sequence % 4 === 0 ? 'DONE' : sequence % 3 === 0 ? 'IN_PROGRESS' : 'OPEN';
  if (sequence % 17 === 0) return 'BLOCKED';
  if (sequence % 5 === 0) return 'DONE';
  if (sequence % 3 === 0) return 'IN_PROGRESS';
  return 'OPEN';
}

function workItemTitle(stream: SeedWorkstream, type: WorkItemType, sequence: number) {
  const subject = stream.subjects[sequence % stream.subjects.length];
  const context = stream.contexts[Math.floor(sequence / stream.subjects.length) % stream.contexts.length];
  const scope = TITLE_SCOPES[Math.floor(sequence / (stream.subjects.length * stream.contexts.length)) % TITLE_SCOPES.length];
  if (type === 'QUESTION') return `Confirm whether ${subject} is ready for ${context} ${scope}`;
  if (type === 'APPROVAL') return `Approve ${subject} for ${context} ${scope}`;
  if (type === 'REVIEW') return `Review ${subject} in ${context} ${scope}`;
  if (type === 'DECISION') return `Decide the ${subject} approach for ${context} ${scope}`;
  return `${TASK_VERBS[sequence % TASK_VERBS.length]} ${subject} for ${context} ${scope}`;
}

function dueDate(status: WorkItemStatus, sequence: number) {
  if (sequence % 4 === 1 && !['BLOCKED', 'PENDING'].includes(status)) return null;
  if (['DONE', 'ANSWERED', 'APPROVED'].includes(status)) return isoDate(-1 - (sequence % 45));
  if (status === 'BLOCKED') return isoDate(-1 - (sequence % 20));
  return isoDate(3 + (sequence % 75));
}

function entryFor(item: CreatedItem, stream: SeedWorkstream) {
  const subject = stream.subjects[item.sequence % stream.subjects.length];
  if (item.status === 'BLOCKED') {
    return {type: 'COMMENT', body: `Delivery is paused because ${stream.blockerReasons[item.sequence % stream.blockerReasons.length].toLowerCase()}. The ${stream.team} team is coordinating the next step.`};
  }
  if (item.status === 'DONE') {
    return {type: 'RESOLUTION', body: `Completed the ${subject} work and verified the expected behavior in the target environment. No follow-up defects remain open.`};
  }
  if (item.status === 'ANSWERED') {
    return {type: 'ANSWER', body: `Confirmed the expected behavior with ${stream.team}. The current approach meets the program requirement and can proceed as documented.`};
  }
  if (item.type === 'APPROVAL' || item.type === 'DECISION') {
    return item.status === 'APPROVED'
      ? {type: 'RESOLUTION', body: 'Approved after reviewing scope, operational impact, and rollback expectations. Record the outcome in the decision log.'}
      : {type: 'PROPOSAL', body: 'Recommendation is ready for review. The proposal covers customer impact, operating ownership, and rollback expectations.'};
  }
  if (item.type === 'REVIEW') {
    return {type: 'EVIDENCE', body: `Review evidence includes the latest test results, owner checklist, and unresolved findings for ${subject}.`};
  }
  if (item.status === 'IN_PROGRESS') {
    return {type: 'COMMENT', body: 'Implementation is underway. The primary path is working; remaining effort is focused on edge cases, observability, and handoff documentation.'};
  }
  return {type: 'INFORMATION', body: `${stream.objective} This item is scoped and ready for an owner to begin.`};
}

async function mapPool<T, R>(items: T[], size: number, worker: (item: T, index: number) => Promise<R>): Promise<R[]> {
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

async function responseData<T>(response: APIResponse, operation: string): Promise<T> {
  const body = await response.json() as {data?: T | null; errors?: unknown};
  if (body.data == null) throw new Error(`${operation} returned no data: ${JSON.stringify(body.errors ?? body)}`);
  return body.data;
}

async function assertSuccessful(response: APIResponse, operation: string): Promise<APIResponse> {
  if (!response.ok()) throw new Error(`${operation} failed with HTTP ${response.status()}: ${await response.text()}`);
  return response;
}

function selectedTeams(scenarios: readonly SeedScenario[]): SeedTeam[] {
  const required = new Set(scenarios.flatMap(scenario => scenario.teamAccess.map(access => access.team)));
  const requiredProfiles = SEED_TEAMS.filter(team => required.has(team.name));
  const targetCount = Math.max(REQUESTED_TEAM_COUNT, requiredProfiles.length);
  return [
    ...requiredProfiles,
    ...SEED_TEAMS.filter(team => !required.has(team.name)).slice(0, targetCount - requiredProfiles.length),
  ];
}

test('Seed Windrunner with realistic multi-project data', async ({request}) => {
  test.setTimeout(15 * 60 * 1000);
  const startedAt = Date.now();
  if (PROJECT_COUNT > SEED_SCENARIOS.length) {
    throw new Error(`SEED_PROJECTS cannot exceed the ${SEED_SCENARIOS.length} defined real-world scenarios`);
  }
  if (USER_COUNT > PERSON_FIRST_NAMES.length * PERSON_LAST_NAMES.length) {
    throw new Error('SEED_USERS exceeds the available unique realistic names');
  }
  const scenarios = SEED_SCENARIOS.slice(0, PROJECT_COUNT);
  if (REQUESTED_TEAM_COUNT > SEED_TEAMS.length) {
    throw new Error(`SEED_TEAMS cannot exceed the ${SEED_TEAMS.length} defined team profiles`);
  }
  const teamProfiles = selectedTeams(scenarios);
  const created = {users: 0, teams: 0, projects: 0, workItems: 0, entries: 0, relationships: 0};

  const login = process.env.E2E_LOGIN;
  const password = process.env.E2E_PASSWORD;
  test.skip(!login || !password, 'Set E2E_LOGIN/E2E_PASSWORD');
  await assertSuccessful(await request.post('/api/v1/auth/login', {data: {login, password}}), 'POST /api/v1/auth/login');
  const currentUser = await responseData<{id: string; globalRole?: string}>(
    await request.get('/api/v1/auth/me'), 'GET /api/v1/auth/me');
  if (!currentUser.id || !['ADMIN', 'SUPERADMIN'].includes(currentUser.globalRole?.toUpperCase() ?? '')) {
    throw new Error('Performance seeding requires an ADMIN or SUPERADMIN account');
  }

  const state = await request.storageState();
  const csrfToken = state.cookies.find(cookie => cookie.name === 'XSRF-TOKEN')?.value;
  const headers = (): Record<string, string> => (csrfToken ? {'X-CSRF-Token': csrfToken} : {});
  const post = async (url: string, data: unknown) =>
    assertSuccessful(await request.post(url, {data, headers: headers()}), `POST ${url}`);

  const userProfiles = Array.from({length: USER_COUNT}, (_, index) => {
    const firstName = PERSON_FIRST_NAMES[index % PERSON_FIRST_NAMES.length];
    const lastName = PERSON_LAST_NAMES[Math.floor(index / PERSON_FIRST_NAMES.length)];
    const team = teamProfiles[index % teamProfiles.length];
    return {
      firstName, lastName, teamName: team.name,
      title: team.titles[Math.floor(index / teamProfiles.length) % team.titles.length],
    };
  });
  const suffix = identifierSuffix();
  const userBodies = userProfiles.map(profile => ({
    username: `${profile.firstName}.${profile.lastName}${suffix}`.toLowerCase(),
    displayName: `${profile.firstName} ${profile.lastName}`,
    email: `${profile.firstName}.${profile.lastName}${suffix}@northstar.example`.toLowerCase(),
    title: profile.title,
    bio: `${profile.title} on ${profile.teamName}, focused on dependable delivery and clear cross-team decisions.`,
    password: 'WindrunnerSeed!2026',
    timezone: 'America/Chicago',
    status: 'ACTIVE',
    globalRole: 'USER',
  }));
  const userResponses = await mapPool(userBodies, CONCURRENCY, body => post('/internal-api/v1/users', body));
  const userIds = await Promise.all(userResponses.map((response, index) =>
    responseData<{id: string}>(response, `Create seeded user ${index + 1}`).then(body => body.id)));
  created.users = userIds.length;

  const userIdsByTeam = new Map<string, string[]>();
  userProfiles.forEach((profile, index) => {
    const members = userIdsByTeam.get(profile.teamName) ?? [];
    members.push(userIds[index]);
    userIdsByTeam.set(profile.teamName, members);
  });

  const teamIdsByName = new Map<string, string>();
  for (const [index, team] of teamProfiles.entries()) {
    const teamMembers = userIdsByTeam.get(team.name) ?? [];
    const ownerUserId = teamMembers[0] ?? userIds[index % userIds.length];
    const response = await post('/internal-api/v1/teams', {
      name: visibleName(team.name), description: team.description, ownerUserIds: [ownerUserId],
    });
    teamIdsByName.set(team.name, (await responseData<{id: string}>(response, `Create ${team.name}`)).id);
  }
  created.teams = teamIdsByName.size;

  await mapPool([...teamIdsByName.entries()], CONCURRENCY, async ([teamName, teamId]) => {
    const members = userIdsByTeam.get(teamName) ?? [];
    for (const userId of members.slice(1)) await post(`/internal-api/v1/teams/${teamId}/members`, {userId});
  });

  const projects: {id: string; scenario: SeedScenario}[] = [];
  for (const scenario of scenarios) {
    const ownerTeamId = teamIdsByName.get(scenario.ownerTeam);
    if (!ownerTeamId) throw new Error(`No seeded team for ${scenario.ownerTeam}`);
    const response = await post('/internal-api/v1/projects', {
      name: visibleName(scenario.name), ownerUserIds: [currentUser.id], ownerTeamIds: [ownerTeamId],
    });
    const projectId = (await responseData<{id: string}>(response, `Create ${scenario.name}`)).id;
    projects.push({id: projectId, scenario});

    for (const access of scenario.teamAccess) {
      if (access.team === scenario.ownerTeam) continue;
      const teamId = teamIdsByName.get(access.team);
      if (!teamId) throw new Error(`No seeded team for ${access.team}`);
      await post(`/internal-api/v1/projects/${projectId}/teams`, {teamId, role: access.role});
    }
    const directMembers = userIds.filter((_, index) => index % PROJECT_COUNT === projects.length - 1).slice(0, 8);
    for (const [index, userId] of directMembers.entries()) {
      await post(`/internal-api/v1/projects/${projectId}/members`, {
        userId, role: index === directMembers.length - 1 ? 'VIEWER' : 'EDITOR',
      });
    }
  }
  created.projects = projects.length;

  const allByProject = new Map<string, CreatedItem[]>();
  for (const {id: projectId, scenario} of projects) {
    const base = `/internal-api/v1/projects/${projectId}/work-items`;
    const roots: CreatedItem[] = await mapPool(scenario.workstreams.slice(0, WORK_ITEMS_PER_PROJECT), CONCURRENCY, async (stream, streamIndex) => {
      const teamId = teamIdsByName.get(stream.team);
      if (!teamId) throw new Error(`No seeded team for ${stream.team}`);
      const response = await post(base, {
        workItem: {title: stream.name, type: 'TASK', status: 'IN_PROGRESS', priority: 'HIGH', dueDate: isoDate(45 + (streamIndex * 7))},
        assignees: [{assigneeType: 'TEAM', assigneeId: teamId}],
      });
      const result = await responseData<{workItem: {id: string}}>(response, `Create ${stream.name}`);
      return {id: result.workItem.id, title: stream.name, type: 'TASK' as const, status: 'IN_PROGRESS' as const, streamIndex, sequence: streamIndex, depth: 0};
    });

    const items: CreatedItem[] = [...roots];
    let frontier = [...roots];
    let sequence = roots.length;
    for (let depth = 1; items.length < WORK_ITEMS_PER_PROJECT && depth <= 3; depth++) {
      const plans: PlannedItem[] = [];
      for (const parent of frontier) {
        for (let child = 0; child < 8 && items.length + plans.length < WORK_ITEMS_PER_PROJECT; child++) {
          const stream = scenario.workstreams[parent.streamIndex];
          const itemSequence = sequence++;
          const type = workItemType(itemSequence);
          const status = workItemStatus(type, itemSequence);
          const teamId = teamIdsByName.get(stream.team);
          if (!teamId) throw new Error(`No seeded team for ${stream.team}`);
          const teamUsers = userIdsByTeam.get(stream.team) ?? [];
          let assignees: Assignee[];
          if (itemSequence % 23 === 0) assignees = [{assigneeType: 'USER', assigneeId: currentUser.id}];
          else if (itemSequence % 3 === 0 || teamUsers.length === 0) assignees = [{assigneeType: 'TEAM', assigneeId: teamId}];
          else assignees = [{assigneeType: 'USER', assigneeId: teamUsers[itemSequence % teamUsers.length]}];
          plans.push({
            parentId: parent.id,
            title: workItemTitle(stream, type, itemSequence),
            type, status, streamIndex: parent.streamIndex, sequence: itemSequence, depth,
            priority: status === 'BLOCKED' ? 'URGENT' : PRIORITIES[itemSequence % PRIORITIES.length],
            dueDate: dueDate(status, itemSequence),
            assignees,
          });
        }
      }
      const next = await mapPool(plans, CONCURRENCY, async plan => {
        const response = await post(base, {
          workItem: {
            title: plan.title, type: plan.type, status: plan.status, priority: plan.priority,
            dueDate: plan.dueDate, parentWorkItemId: plan.parentId,
          },
          assignees: plan.assignees,
        });
        const result = await responseData<{workItem: {id: string}}>(response, `Create ${plan.title}`);
        return {...plan, id: result.workItem.id};
      });
      items.push(...next);
      frontier = next;
    }
    if (items.length !== WORK_ITEMS_PER_PROJECT) {
      throw new Error(`Scenario hierarchy produced ${items.length} of ${WORK_ITEMS_PER_PROJECT} requested work items`);
    }
    allByProject.set(projectId, items);
    created.workItems += items.length;
  }

  for (const {id: projectId, scenario} of projects) {
    const items = allByProject.get(projectId) ?? [];
    const entryTargets = items.filter(item => item.depth === 0 || item.sequence % 2 === 0);
    await mapPool(entryTargets, CONCURRENCY, item => {
      const entry = entryFor(item, scenario.workstreams[item.streamIndex]);
      return post(`/internal-api/v1/projects/${projectId}/entries`, {workItemId: item.id, type: entry.type, body: entry.body});
    });
    created.entries += entryTargets.length;
  }

  for (const {id: projectId, scenario} of projects) {
    const items = allByProject.get(projectId) ?? [];
    const itemsByStream = scenario.workstreams.map((_, streamIndex) => items.filter(item => item.streamIndex === streamIndex));
    const relationships: {from: CreatedItem; to: CreatedItem; type: string; reason: string}[] = [];
    const relationshipKeys = new Set<string>();
    const addRelationship = (from: CreatedItem, to: CreatedItem, type: string, reason: string) => {
      const key = `${from.id}:${to.id}:${type}`;
      if (from.id !== to.id && !relationshipKeys.has(key)) {
        relationshipKeys.add(key);
        relationships.push({from, to, type, reason});
      }
    };

    for (const [streamIndex, streamItems] of itemsByStream.entries()) {
      const stream = scenario.workstreams[streamIndex];
      const active = streamItems.filter(item => !['DONE', 'ANSWERED', 'APPROVED', 'BLOCKED'].includes(item.status));
      for (const item of streamItems) {
        if (item.status === 'BLOCKED') {
          const blocker = active[item.sequence % active.length] ?? streamItems[0];
          addRelationship(item, blocker, 'BLOCKED_BY', stream.blockerReasons[item.sequence % stream.blockerReasons.length]);
        } else if (item.depth > 0 && item.sequence % 12 === 0) {
          const dependency = streamItems[Math.max(0, streamItems.indexOf(item) - 1)];
          addRelationship(item, dependency, 'DEPENDS_ON', `${dependency.title} must be completed first.`);
        }
      }
    }
    for (let index = 0; index < items.length; index += 50) {
      const from = items[index];
      const candidates = itemsByStream[(from.streamIndex + 1) % scenario.workstreams.length];
      const to = candidates[Math.floor(index / 50) % candidates.length];
      if (to) addRelationship(from, to, 'RELATED_TO', 'The workstreams share delivery readiness and operating dependencies.');
    }

    await mapPool(relationships, CONCURRENCY, relationship =>
      post(`/internal-api/v1/projects/${projectId}/relationships`, {
        fromEntityType: 'WORK_ITEM', fromEntityId: relationship.from.id,
        toEntityType: 'WORK_ITEM', toEntityId: relationship.to.id,
        type: relationship.type, reason: relationship.reason,
      }));
    created.relationships += relationships.length;
  }

  const seconds = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`Seeded real-world scenarios in ${seconds}s →`, JSON.stringify(created));
  expect(created.workItems).toBe(WORK_ITEMS_PER_PROJECT * PROJECT_COUNT);
  if (WORK_ITEMS_PER_PROJECT > 1) expect(created.relationships).toBeGreaterThan(0);
});
