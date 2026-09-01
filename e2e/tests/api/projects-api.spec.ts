import { test, expect, bearer } from '../support/helpers';

test.describe('External API: projects', () => {
  test('lists visible projects with the standard envelope', async ({ authenticated }) => {
    const response = await authenticated.api.get('/api/v1/projects?page=0&size=25', {
      headers: bearer(authenticated),
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.meta).toMatchObject({ page: 0, size: 25 });
    expect(typeof body.meta.totalItems).toBe('number');
  });

  test('rejects requests without an API key', async ({ request }) => {
    const response = await request.get('/api/v1/projects');
    expect(response.status()).toBe(401);
  });

  test('creates, fetches, and deletes a project end to end', async ({ authenticated }) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID (or login credentials) to own the created project.');
    const projectName = `e2e-project-${Date.now()}`;

    // Create — the key owner is auto-added as owner (Fix 1), so the caller
    // must be able to read the project back immediately.
    const createResponse = await authenticated.api.post('/api/v1/projects', {
      headers: bearer(authenticated),
      data: {
        name: projectName,
        ownerUserIds: [authenticated.userId!],
        ownerTeamIds: [],
      },
    });
    const createResponseText = await createResponse.text();
    expect(createResponse.status(), `${createResponse.url()}\n${createResponseText}`).toBe(201);
    const created = JSON.parse(createResponseText);
    expect(created.data.name).toBe(projectName);
    expect(created.data.id).toBeTruthy();

    const projectId = created.data.id;

    // Fetch it back with a fresh GET.
    const getResponse = await authenticated.api.get(`/api/v1/projects/${projectId}`, {
      headers: bearer(authenticated),
    });
    expect(getResponse.status()).toBe(200);
    const fetched = await getResponse.json();
    expect(fetched.data.id).toBe(projectId);
    // Server-owned fields survive the round trip (Fix 2).
    expect(fetched.data.createdAt).toBeTruthy();

    // Delete and confirm it is gone.
    const deleteResponse = await authenticated.api.delete(`/api/v1/projects/${projectId}`, {
      headers: bearer(authenticated),
    });
    expect(deleteResponse.status()).toBe(200);

    const refetch = await authenticated.api.get(`/api/v1/projects/${projectId}`, {
      headers: bearer(authenticated),
    });
    expect(refetch.status()).toBe(404);
  });
});
