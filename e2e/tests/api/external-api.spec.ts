import type { APIResponse } from '@playwright/test';
import { bearer, csrfHeaders, expect, test, type E2EContext } from '../support/helpers';

type Project = {
  id: string;
  name: string;
  createdByUserId: string;
  createdAt: string;
};

type ProjectMember = {
  projectId: string;
  userId: string;
  role: string;
};

type ProjectTeam = {
  projectId: string;
  teamId: string;
  role: string;
};

type Team = {
  id: string;
  name: string;
  description: string | null;
};

type TeamMember = {
  teamId: string;
  userId: string;
  role: string;
};

type WorkItem = {
  workItem: {
    id: string;
    projectId: string;
    title: string;
    type: string;
    status: string;
    dueDate: string | null;
    priority: string | null;
    parentWorkItemId: string | null;
    sortIndex: number;
  };
  assignees: Array<{assigneeType: string; assigneeId: string}>;
};

type Entry = {
  id: string;
  projectId: string;
  workItemId: string;
  type: string;
  body: string;
};

type Relationship = {
  id: string;
  projectId: string;
  fromEntityType: string;
  fromEntityId: string;
  toEntityType: string;
  toEntityId: string;
  type: string;
  reason: string | null;
  sourceEntryId: string | null;
};

type SearchResult = {
  workItems: WorkItem['workItem'][];
  entries: Entry[];
  relationships: Relationship[];
};

type ApiBody<T> = {
  data: T;
  errors?: unknown[];
  meta?: {
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
  };
};

type CreatedApiKey = {id: string; rawKey: string};

function uniqueName(prefix: string) {
  return `e2e-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function readBody<T>(response: APIResponse, status: number): Promise<ApiBody<T>> {
  expect(response.status()).toBe(status);
  const body = await response.json() as ApiBody<T>;
  if (status >= 200 && status < 300) {
    expect(body.errors ?? []).toEqual([]);
  }
  return body;
}

async function readData<T>(response: APIResponse, status: number): Promise<T> {
  return (await readBody<T>(response, status)).data;
}

async function readPage<T>(response: APIResponse, page: number, size: number): Promise<T[]> {
  const body = await readBody<T[]>(response, 200);
  expect(Array.isArray(body.data)).toBe(true);
  expect(body.meta).toMatchObject({page, size});
  expect(typeof body.meta?.totalItems).toBe('number');
  expect(typeof body.meta?.totalPages).toBe('number');
  return body.data;
}

async function createProject(context: E2EContext, label: string): Promise<Project> {
  const userId = context.userId;
  if (!userId) throw new Error('E2E_USER_ID or session login is required');
  return readData<Project>(await context.api.post('/api/v1/projects', {
    headers: bearer(context),
    data: {
      name: uniqueName(label),
      ownerUserIds: [userId],
      ownerTeamIds: [],
    },
  }), 201);
}

async function deleteProject(context: E2EContext, projectId: string) {
  await context.api.delete(`/api/v1/projects/${projectId}`, {headers: bearer(context)});
}

async function createWorkItem(context: E2EContext, projectId: string, title: string, assignees: Array<{assigneeType: string; assigneeId: string}> = []): Promise<WorkItem> {
  return readData<WorkItem>(await context.api.post(`/api/v1/projects/${projectId}/work-items`, {
    headers: bearer(context),
    data: {
      workItem: {
        title,
        type: 'TASK',
        status: 'OPEN',
        dueDate: '2099-12-31',
        priority: 'HIGH',
        parentWorkItemId: null,
      },
      assignees,
    },
  }), 201);
}

async function createEntry(context: E2EContext, workItemId: string, body: string): Promise<Entry> {
  return readData<Entry>(await context.api.post(`/api/v1/work-items/${workItemId}/entries`, {
    headers: bearer(context),
    data: {type: 'COMMENT', body},
  }), 201);
}

async function createScopedKey(context: E2EContext, scopes: readonly string[]): Promise<CreatedApiKey | null> {
  if (!context.csrfToken) return null;
  const response = await context.api.post('/internal-api/v1/me/api-keys', {
    headers: csrfHeaders(context),
    data: {name: uniqueName('scope-test'), scopes: [...scopes]},
  });
  const body = await readBody<{id: string; rawKey: string}>(response, 200);
  return {id: body.data.id, rawKey: body.data.rawKey};
}

async function revokeScopedKey(context: E2EContext, key: CreatedApiKey) {
  await context.api.delete(`/internal-api/v1/me/api-keys/${key.id}`, {headers: csrfHeaders(context)});
}

test.describe('External API: work items and entries', () => {
  test('creates, lists, updates, moves, and deletes work items', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');
    const project = await createProject(authenticated, 'work-items');
    let secondId: string | undefined;
    try {
      const root = await createWorkItem(authenticated, project.id, uniqueName('root'), [
        {assigneeType: 'USER', assigneeId: authenticated.userId!},
      ]);
      const second = await createWorkItem(authenticated, project.id, uniqueName('second'));
      secondId = second.workItem.id;

      const listed = await readPage<WorkItem>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/work-items?page=-1&size=500&status=open&type=task&priority=high&updated_after=1970-01-01T00:00:00Z`,
        {headers: bearer(authenticated)},
      ), 0, 100);
      expect(listed.map(item => item.workItem.id)).toEqual(expect.arrayContaining([root.workItem.id, second.workItem.id]));

      const fetched = await readData<WorkItem>(await authenticated.api.get(
        `/api/v1/work-items/${root.workItem.id}`, {headers: bearer(authenticated)}), 200);
      expect(fetched.workItem).toMatchObject({
        id: root.workItem.id,
        projectId: project.id,
        title: root.workItem.title,
        status: 'OPEN',
        priority: 'HIGH',
      });
      expect(fetched.assignees).toEqual([{assigneeType: 'USER', assigneeId: authenticated.userId}]);

      const updated = await readData<WorkItem>(await authenticated.api.put(
        `/api/v1/work-items/${root.workItem.id}`,
        {
          headers: bearer(authenticated),
          data: {
            workItem: {
              title: `${root.workItem.title} updated`,
              type: 'TASK',
              status: 'DONE',
              dueDate: '2099-12-30',
              priority: 'URGENT',
              parentWorkItemId: null,
            },
            assignees: [],
          },
        },
      ), 200);
      expect(updated.workItem).toMatchObject({status: 'DONE', priority: 'URGENT', dueDate: '2099-12-30'});
      expect(updated.assignees).toEqual([]);

      const moved = await readData<WorkItem>(await authenticated.api.put(
        `/api/v1/work-items/${second.workItem.id}/move`,
        {
          headers: bearer(authenticated),
          data: {parentWorkItemId: null, beforeEntityType: 'WORK_ITEM', beforeEntityId: root.workItem.id},
        },
      ), 200);
      expect(moved.workItem.id).toBe(second.workItem.id);

      expect((await authenticated.api.delete(`/api/v1/work-items/${second.workItem.id}`, {headers: bearer(authenticated)})).status()).toBe(200);
      expect((await authenticated.api.get(`/api/v1/work-items/${second.workItem.id}`, {headers: bearer(authenticated)})).status()).toBe(404);
      secondId = undefined;
    } finally {
      if (secondId) await authenticated.api.delete(`/api/v1/work-items/${secondId}`, {headers: bearer(authenticated)});
      await deleteProject(authenticated, project.id);
    }
  });

  test('creates, lists, updates, and deletes entries', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');
    const project = await createProject(authenticated, 'entries');
    try {
      const workItem = await createWorkItem(authenticated, project.id, uniqueName('entry-item'));
      const entry = await createEntry(authenticated, workItem.workItem.id, `${uniqueName('entry')} initial`);

      const listed = await readPage<Entry>(await authenticated.api.get(
        `/api/v1/work-items/${workItem.workItem.id}/entries?page=0&size=25&updated_after=1970-01-01T00:00:00Z`,
        {headers: bearer(authenticated)},
      ), 0, 25);
      expect(listed).toEqual(expect.arrayContaining([expect.objectContaining({id: entry.id, workItemId: workItem.workItem.id})]));

      const fetched = await readData<Entry>(await authenticated.api.get(
        `/api/v1/entries/${entry.id}`, {headers: bearer(authenticated)}), 200);
      expect(fetched).toMatchObject({id: entry.id, projectId: project.id, workItemId: workItem.workItem.id});

      const updatedBody = `${entry.body} revised`;
      const updated = await readData<Entry>(await authenticated.api.put(`/api/v1/entries/${entry.id}`, {
        headers: bearer(authenticated),
        data: {type: 'INFORMATION', body: updatedBody},
      }), 200);
      expect(updated).toMatchObject({id: entry.id, type: 'INFORMATION', body: updatedBody, workItemId: workItem.workItem.id});

      expect((await authenticated.api.delete(`/api/v1/entries/${entry.id}`, {headers: bearer(authenticated)})).status()).toBe(200);
      expect((await authenticated.api.get(`/api/v1/entries/${entry.id}`, {headers: bearer(authenticated)})).status()).toBe(404);
    } finally {
      await deleteProject(authenticated, project.id);
    }
  });
});

test.describe('External API: relationships, search, and content order', () => {
  test('creates, filters, updates, searches, reorders, and deletes relationships', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');
    const project = await createProject(authenticated, 'relationships');
    try {
      const first = await createWorkItem(authenticated, project.id, uniqueName('relationship-first'));
      const second = await createWorkItem(authenticated, project.id, uniqueName('relationship-second'));
      const entry = await createEntry(authenticated, first.workItem.id, `${uniqueName('relationship-entry')} evidence`);
      const marker = uniqueName('relationship-marker');

      const relationship = await readData<Relationship>(await authenticated.api.post(`/api/v1/projects/${project.id}/relationships`, {
        headers: bearer(authenticated),
        data: {
          fromEntityType: 'WORK_ITEM',
          fromEntityId: first.workItem.id,
          toEntityType: 'WORK_ITEM',
          toEntityId: second.workItem.id,
          type: 'BLOCKED_BY',
          reason: marker,
          sourceEntryId: entry.id,
        },
      }), 201);
      expect(relationship).toMatchObject({
        projectId: project.id,
        fromEntityId: first.workItem.id,
        toEntityId: second.workItem.id,
        type: 'BLOCKED_BY',
        sourceEntryId: entry.id,
      });

      const listed = await readPage<Relationship>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/relationships?page=0&size=25&type=blocked_by&created_after=1970-01-01T00:00:00Z`,
        {headers: bearer(authenticated)},
      ), 0, 25);
      expect(listed).toEqual(expect.arrayContaining([expect.objectContaining({id: relationship.id, type: 'BLOCKED_BY'})]));

      const revisedMarker = `${marker}-revised`;
      const revised = await readData<Relationship>(await authenticated.api.put(`/api/v1/relationships/${relationship.id}/reason`, {
        headers: bearer(authenticated),
        data: {reason: revisedMarker},
      }), 200);
      expect(revised).toMatchObject({id: relationship.id, reason: revisedMarker});

      const search = await readData<SearchResult>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/search?q=${encodeURIComponent(revisedMarker)}&limit=10`,
        {headers: bearer(authenticated)},
      ), 200);
      expect(search.relationships).toEqual(expect.arrayContaining([expect.objectContaining({id: relationship.id, reason: revisedMarker})]));

      const reordered = await readData<Array<{entityType: string; entityId: string; sortIndex: number}>>(
        await authenticated.api.put(`/api/v1/projects/${project.id}/content-order`, {
          headers: bearer(authenticated),
          data: {
            parentWorkItemId: null,
            items: [
              {entityType: 'WORK_ITEM', entityId: second.workItem.id},
              {entityType: 'WORK_ITEM', entityId: first.workItem.id},
            ],
          },
        }),
        200,
      );
      expect(reordered.map(item => item.entityId)).toEqual([second.workItem.id, first.workItem.id]);

      expect((await authenticated.api.delete(`/api/v1/relationships/${relationship.id}`, {headers: bearer(authenticated)})).status()).toBe(200);
      const afterDelete = await readPage<Relationship>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/relationships?type=BLOCKED_BY`,
        {headers: bearer(authenticated)},
      ), 0, 50);
      expect(afterDelete).not.toEqual(expect.arrayContaining([expect.objectContaining({id: relationship.id})]));
    } finally {
      await deleteProject(authenticated, project.id);
    }
  });
});

test.describe('External API: project access and teams', () => {
  test('updates projects and manages project members and team links', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');
    const project = await createProject(authenticated, 'project-access');
    const secondUserId = process.env.E2E_SECOND_USER_ID;
    let teamId: string | undefined;
    try {
      const updatedName = uniqueName('project-updated');
      const updated = await readData<Project>(await authenticated.api.put(`/api/v1/projects/${project.id}`, {
        headers: bearer(authenticated),
        data: {name: updatedName},
      }), 200);
      expect(updated).toMatchObject({id: project.id, name: updatedName, createdByUserId: authenticated.userId});

      const fetched = await readData<Project>(await authenticated.api.get(`/api/v1/projects/${project.id}`, {
        headers: bearer(authenticated),
      }), 200);
      expect(fetched.name).toBe(updatedName);

      const members = await readPage<ProjectMember>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/members?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
      expect(members).toEqual(expect.arrayContaining([expect.objectContaining({projectId: project.id, userId: authenticated.userId, role: 'OWNER'})]));

      const projectTeams = await readPage<ProjectTeam>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/teams?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
      expect(projectTeams).toEqual([]);

      if (secondUserId && secondUserId !== authenticated.userId) {
        const member = await readData<ProjectMember>(await authenticated.api.post(`/api/v1/projects/${project.id}/members`, {
          headers: bearer(authenticated),
          data: {userId: secondUserId, role: 'VIEWER'},
        }), 201);
        expect(member).toMatchObject({projectId: project.id, userId: secondUserId, role: 'VIEWER'});

        const withMember = await readPage<ProjectMember>(await authenticated.api.get(
          `/api/v1/projects/${project.id}/members?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
        expect(withMember).toEqual(expect.arrayContaining([expect.objectContaining({userId: secondUserId, role: 'VIEWER'})]));

        expect((await authenticated.api.delete(`/api/v1/projects/${project.id}/members/${secondUserId}`, {headers: bearer(authenticated)})).status()).toBe(200);
      }

      if (authenticated.isAdminLike) {
        const team = await readData<Team>(await authenticated.api.post('/api/v1/teams', {
          headers: bearer(authenticated),
          data: {name: uniqueName('team'), description: 'External API E2E team', ownerUserIds: [authenticated.userId]},
        }), 201);
        teamId = team.id;
        expect(team).toMatchObject({id: team.id, description: 'External API E2E team'});

        const teams = await readPage<Team>(await authenticated.api.get('/api/v1/teams?page=0&size=100', {
          headers: bearer(authenticated),
        }), 0, 100);
        expect(teams).toEqual(expect.arrayContaining([expect.objectContaining({id: team.id})]));

        const fetchedTeam = await readData<Team>(await authenticated.api.get(`/api/v1/teams/${team.id}`, {
          headers: bearer(authenticated),
        }), 200);
        expect(fetchedTeam).toMatchObject({id: team.id, name: team.name});

        const updatedTeam = await readData<Team>(await authenticated.api.put(`/api/v1/teams/${team.id}`, {
          headers: bearer(authenticated),
          data: {name: `${team.name}-updated`, description: 'Updated external API E2E team'},
        }), 200);
        expect(updatedTeam).toMatchObject({id: team.id, name: `${team.name}-updated`, description: 'Updated external API E2E team'});

        const teamMembers = await readPage<TeamMember>(await authenticated.api.get(
          `/api/v1/teams/${team.id}/members?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
        expect(teamMembers).toEqual(expect.arrayContaining([expect.objectContaining({teamId: team.id, userId: authenticated.userId, role: 'TEAM_OWNER'})]));

        if (secondUserId && secondUserId !== authenticated.userId) {
          const added = await readData<TeamMember>(await authenticated.api.post(`/api/v1/teams/${team.id}/members`, {
            headers: bearer(authenticated),
            data: {userId: secondUserId, role: 'TEAM_MEMBER'},
          }), 201);
          expect(added).toMatchObject({teamId: team.id, userId: secondUserId, role: 'TEAM_MEMBER'});

          const withMember = await readPage<TeamMember>(await authenticated.api.get(
            `/api/v1/teams/${team.id}/members?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
          expect(withMember).toEqual(expect.arrayContaining([expect.objectContaining({userId: secondUserId, role: 'TEAM_MEMBER'})]));

          expect((await authenticated.api.delete(`/api/v1/teams/${team.id}/members/${secondUserId}`, {headers: bearer(authenticated)})).status()).toBe(200);
        }

        const projectTeam = await readData<ProjectTeam>(await authenticated.api.post(`/api/v1/projects/${project.id}/teams`, {
          headers: bearer(authenticated),
          data: {teamId: team.id, role: 'EDITOR'},
        }), 201);
        expect(projectTeam).toMatchObject({projectId: project.id, teamId: team.id, role: 'EDITOR'});

        const linkedProjectTeams = await readPage<ProjectTeam>(await authenticated.api.get(
          `/api/v1/projects/${project.id}/teams?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
        expect(linkedProjectTeams).toEqual(expect.arrayContaining([expect.objectContaining({teamId: team.id, role: 'EDITOR'})]));

        const linkedProjects = await readPage<ProjectTeam>(await authenticated.api.get(
          `/api/v1/teams/${team.id}/projects?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
        expect(linkedProjects).toEqual(expect.arrayContaining([expect.objectContaining({projectId: project.id, teamId: team.id})]));

        expect((await authenticated.api.delete(`/api/v1/projects/${project.id}/teams/${team.id}`, {headers: bearer(authenticated)})).status()).toBe(200);
        expect((await authenticated.api.delete(`/api/v1/teams/${team.id}`, {headers: bearer(authenticated)})).status()).toBe(200);
        teamId = undefined;
        expect((await authenticated.api.get(`/api/v1/teams/${team.id}`, {headers: bearer(authenticated)})).status()).toBe(404);
      }
    } finally {
      if (teamId) {
        await authenticated.api.delete(`/api/v1/projects/${project.id}/teams/${teamId}`, {headers: bearer(authenticated)});
        if (secondUserId && secondUserId !== authenticated.userId) {
          await authenticated.api.delete(`/api/v1/teams/${teamId}/members/${secondUserId}`, {headers: bearer(authenticated)});
        }
        await authenticated.api.delete(`/api/v1/teams/${teamId}`, {headers: bearer(authenticated)});
      }
      await deleteProject(authenticated, project.id);
    }
  });
});

test.describe('External API: users, audit logs, and authorization', () => {
  test('resolves active users and returns limited identity fields', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');
    const user = await readData<{id: string; username: string; displayName: string; title: string; bio: string}>(
      await authenticated.api.get(`/api/v1/users/${authenticated.userId}`, {headers: bearer(authenticated)}), 200);
    expect(user).toMatchObject({id: authenticated.userId});
    expect(user).not.toHaveProperty('password');
    expect((await authenticated.api.get('/api/v1/users/missing-user', {headers: bearer(authenticated)})).status()).toBe(404);
  });

  test('reads global and project audit logs', async ({authenticated}) => {
    test.skip(!authenticated.userId || !authenticated.isAdminLike, 'Requires an ADMIN or SUPERADMIN external API key.');
    const project = await createProject(authenticated, 'audit-logs');
    try {
      const globalLogs = await readPage<Record<string, unknown>>(await authenticated.api.get(
        '/api/v1/audit-logs?page=0&size=25', {headers: bearer(authenticated)}), 0, 25);
      expect(globalLogs.length).toBeGreaterThan(0);

      const projectLogs = await readPage<Record<string, unknown>>(await authenticated.api.get(
        `/api/v1/projects/${project.id}/audit-logs?page=0&size=25`, {headers: bearer(authenticated)}), 0, 25);
      expect(projectLogs).toEqual(expect.arrayContaining([expect.objectContaining({projectId: project.id, entityId: project.id})]));
    } finally {
      await deleteProject(authenticated, project.id);
    }
  });

  test('rejects a valid API key that lacks the endpoint scope', async ({authenticated}) => {
    test.skip(!authenticated.csrfToken, 'Requires session login to mint a limited test key.');
    const limitedKey = await createScopedKey(authenticated, ['teams:read']);
    if (!limitedKey) return;
    try {
      const response = await authenticated.api.get('/api/v1/projects', {
        headers: {Authorization: `Bearer ${limitedKey.rawKey}`},
      });
      expect(response.status()).toBe(403);
    } finally {
      await revokeScopedKey(authenticated, limitedKey);
    }
  });
});
