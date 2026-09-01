import {spawn} from 'node:child_process';
import {resolve} from 'node:path';
import {expect, test} from '../support/helpers';

const cliPath = resolve(__dirname, '../../../cli/dist/index.js');
const serverUrl = (process.env.WINDRUNNER_BASE_URL ?? 'http://localhost:8066').replace(/\/+$/, '');

type CliResult = {
  exitCode: number | null;
  stdout: string;
  stderr: string;
};

async function runCli(args: string[], apiKey?: string): Promise<CliResult> {
  const environment: NodeJS.ProcessEnv = {...process.env, WINDRUNNER_URL: serverUrl};
  if (apiKey) {
    environment.WINDRUNNER_API_KEY = apiKey;
  } else {
    delete environment.WINDRUNNER_API_KEY;
  }

  return new Promise((resolveResult, reject) => {
    const child = spawn(process.execPath, [cliPath, ...args], {
      cwd: resolve(__dirname, '../../../cli'),
      env: environment,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      child.kill('SIGTERM');
    }, 30_000);

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString();
    });
    child.on('error', (error) => {
      clearTimeout(timeout);
      reject(error);
    });
    child.on('close', (exitCode) => {
      clearTimeout(timeout);
      resolveResult({exitCode: timedOut ? null : exitCode, stdout, stderr});
    });
  });
}

function parseJson<T = any>(result: CliResult): T {
  expect(result.exitCode, result.stderr).toBe(0);
  return JSON.parse(result.stdout) as T;
}

async function runJson<T = any>(args: string[], apiKey?: string): Promise<T> {
  return parseJson<T>(await runCli([...args, '--json'], apiKey));
}

async function cleanupCli(args: string[], apiKey: string): Promise<void> {
  await runCli([...args, '--json'], apiKey);
}

function uniqueName(prefix: string): string {
  return `cli-e2e-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

test.describe('CLI against a running server', () => {
  test('lists projects as JSON', async ({authenticated}) => {
    const result = await runCli(['projects', 'list', '--size', '5', '--json'], authenticated.apiKey);
    const projects = parseJson(result);

    expect(Array.isArray(projects)).toBe(true);
  });

  test('supports dry-run without an API key', async () => {
    const result = await runCli([
      'projects',
      'create',
      '--name',
      'CLI dry run',
      '--owner-user',
      'user-for-dry-run',
      '--dry-run',
      '--json',
    ]);
    const preview = parseJson(result);

    expect(preview).toMatchObject({
      dryRun: true,
      method: 'POST',
      path: `${serverUrl}/api/v1/projects`,
    });
  });

  test('fails clearly when no API key is configured', async () => {
    const result = await runCli(['projects', 'list', '--json']);

    expect(result.exitCode).toBe(1);
    expect(result.stderr).toContain('WINDRUNNER_API_KEY is required for API requests.');
  });

  test('covers project, work-item, entry, relationship, and search commands', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');

    let projectId: string | undefined;
    let firstWorkItemId: string | undefined;
    let secondWorkItemId: string | undefined;
    let firstEntryId: string | undefined;
    let secondEntryId: string | undefined;
    let relationshipId: string | undefined;
    const secondUserId = authenticated.secondUserId;
    try {
      const createdProject = await runJson<any>([
        'projects',
        'create',
        '--name',
        uniqueName('project'),
        '--owner-user',
        authenticated.userId!,
      ], authenticated.apiKey);
      const createdProjectId = createdProject.id as string;
      projectId = createdProjectId;
      expect(projectId).toBeTruthy();

      const listedProjects = await runJson<any[]>(['projects', 'list', '--page', '0', '--size', '5'], authenticated.apiKey);
      expect(Array.isArray(listedProjects)).toBe(true);

      const fetchedProject = await runJson<any>(['projects', 'get', createdProjectId], authenticated.apiKey);
      expect(fetchedProject.id).toBe(projectId);

      const updatedProject = await runJson<any>([
        'projects', 'update', createdProjectId, '--name', uniqueName('updated-project'),
      ], authenticated.apiKey);
      expect(updatedProject.id).toBe(projectId);

      const projectMembers = await runJson<any[]>([
        'projects', 'members', 'list', createdProjectId, '--page', '0', '--size', '20',
      ], authenticated.apiKey);
      expect(projectMembers).toEqual(expect.arrayContaining([expect.objectContaining({userId: authenticated.userId})]));

      if (secondUserId && secondUserId !== authenticated.userId) {
        const addedMember = await runJson<any>([
          'projects', 'members', 'add', createdProjectId,
          '--user-id', secondUserId, '--role', 'VIEWER',
        ], authenticated.apiKey);
        expect(addedMember).toMatchObject({projectId: createdProjectId, userId: secondUserId, role: 'VIEWER'});
        const membersWithSecondUser = await runJson<any[]>([
          'projects', 'members', 'list', createdProjectId, '--page', '0', '--size', '20',
        ], authenticated.apiKey);
        expect(membersWithSecondUser).toEqual(expect.arrayContaining([expect.objectContaining({userId: secondUserId})]));
        await runJson(['projects', 'members', 'remove', createdProjectId, secondUserId, '--yes'], authenticated.apiKey);
      }

      const createdWorkItem = await runJson<any>([
        'work-items',
        'create',
        createdProjectId,
        '--title',
        uniqueName('first-work-item'),
        '--type',
        'TASK',
        '--status',
        'OPEN',
        '--due-date',
        '2099-12-31',
        '--priority',
        'HIGH',
        '--assignee',
        `USER:${authenticated.userId}`,
      ], authenticated.apiKey);
      firstWorkItemId = createdWorkItem.workItem.id as string;
      expect(firstWorkItemId).toBeTruthy();

      const secondWorkItem = await runJson<any>([
        'work-items', 'create', createdProjectId, '--title', uniqueName('second-work-item'), '--type', 'TASK',
      ], authenticated.apiKey);
      secondWorkItemId = secondWorkItem.workItem.id as string;
      expect(secondWorkItemId).toBeTruthy();

      const listedWorkItems = await runJson<any[]>([
        'work-items', 'list', createdProjectId,
        '--page', '0', '--size', '25', '--status', 'OPEN', '--type', 'TASK', '--priority', 'HIGH',
        '--updated-after', '1970-01-01T00:00:00Z',
      ], authenticated.apiKey);
      expect(listedWorkItems).toEqual(expect.arrayContaining([
        expect.objectContaining({workItem: expect.objectContaining({id: firstWorkItemId})}),
      ]));

      const fetchedWorkItem = await runJson<any>(['work-items', 'get', firstWorkItemId], authenticated.apiKey);
      expect(fetchedWorkItem.workItem.id).toBe(firstWorkItemId);

      const updatedWorkItem = await runJson<any>([
        'work-items',
        'update',
        firstWorkItemId,
        '--title',
        'CLI E2E updated work item',
        '--type',
        'REVIEW',
        '--status',
        'IN_PROGRESS',
        '--due-date',
        '2099-12-31',
        '--priority',
        'HIGH',
        '--assignee',
        `USER:${authenticated.userId}`,
      ], authenticated.apiKey);
      expect(updatedWorkItem.workItem.id).toBe(firstWorkItemId);
      expect(updatedWorkItem.workItem.title).toBe('CLI E2E updated work item');

      const createdEntry = await runJson<any>([
        'entries',
        'create',
        firstWorkItemId,
        '--body',
        'CLI E2E entry',
        '--type',
        'COMMENT',
      ], authenticated.apiKey);
      firstEntryId = createdEntry.id as string;
      expect(firstEntryId).toBeTruthy();

      const secondEntry = await runJson<any>([
        'entries', 'create', firstWorkItemId, '--body', 'CLI E2E second entry', '--type', 'EVIDENCE',
      ], authenticated.apiKey);
      secondEntryId = secondEntry.id as string;
      expect(secondEntryId).toBeTruthy();

      const listedEntries = await runJson<any[]>([
        'entries', 'list', firstWorkItemId, '--page', '0', '--size', '25',
        '--updated-after', '1970-01-01T00:00:00Z',
      ], authenticated.apiKey);
      expect(listedEntries).toEqual(expect.arrayContaining([expect.objectContaining({id: firstEntryId})]));

      const fetchedEntry = await runJson<any>(['entries', 'get', firstEntryId], authenticated.apiKey);
      expect(fetchedEntry.id).toBe(firstEntryId);

      const updatedEntry = await runJson<any>([
        'entries',
        'update',
        firstEntryId,
        '--body',
        'CLI E2E updated entry',
        '--type',
        'EVIDENCE',
      ], authenticated.apiKey);
      expect(updatedEntry.id).toBe(firstEntryId);
      expect(updatedEntry.body).toBe('CLI E2E updated entry');

      const searchResult = await runJson<any>([
        'search',
        createdProjectId,
        'CLI E2E updated',
        '--limit',
        '20',
      ], authenticated.apiKey);
      expect(searchResult.workItems.length + searchResult.entries.length).toBeGreaterThan(0);

      const createdRelationship = await runJson<any>([
        'relationships', 'create', createdProjectId,
        '--from', `WORK_ITEM:${firstWorkItemId}`,
        '--to', `WORK_ITEM:${secondWorkItemId}`,
        '--type', 'BLOCKED_BY',
        '--reason', 'CLI E2E relationship',
        '--source-entry-id', firstEntryId,
      ], authenticated.apiKey);
      relationshipId = createdRelationship.id as string;
      expect(relationshipId).toBeTruthy();

      const listedRelationships = await runJson<any[]>([
        'relationships', 'list', createdProjectId,
        '--page', '0', '--size', '25', '--type', 'BLOCKED_BY',
        '--created-after', '1970-01-01T00:00:00Z',
      ], authenticated.apiKey);
      expect(listedRelationships).toEqual(expect.arrayContaining([expect.objectContaining({id: relationshipId})]));

      const updatedRelationship = await runJson<any>([
        'relationships', 'update-reason', relationshipId, '--reason', 'CLI E2E updated relationship',
      ], authenticated.apiKey);
      expect(updatedRelationship.id).toBe(relationshipId);

      await runJson(['relationships', 'delete', relationshipId, '--yes'], authenticated.apiKey);
      relationshipId = undefined;

      const movedWorkItem = await runJson<any>([
        'work-items', 'move', secondWorkItemId, '--before', `WORK_ITEM:${firstWorkItemId}`,
      ], authenticated.apiKey);
      expect(movedWorkItem.workItem.id).toBe(secondWorkItemId);

      const reordered = await runJson<any[]>([
        'projects', 'reorder', createdProjectId,
        '--item', `WORK_ITEM:${secondWorkItemId}`,
        '--item', `WORK_ITEM:${firstWorkItemId}`,
      ], authenticated.apiKey);
      expect(reordered.map(item => item.entityId)).toEqual([secondWorkItemId, firstWorkItemId]);

      await runJson(['entries', 'delete', secondEntryId, '--yes'], authenticated.apiKey);
      secondEntryId = undefined;
      await runJson(['work-items', 'delete', secondWorkItemId, '--yes'], authenticated.apiKey);
      secondWorkItemId = undefined;
      await runJson(['entries', 'delete', firstEntryId, '--yes'], authenticated.apiKey);
      firstEntryId = undefined;
      await runJson(['work-items', 'delete', firstWorkItemId, '--yes'], authenticated.apiKey);
      firstWorkItemId = undefined;

      await runJson(['projects', 'delete', createdProjectId, '--yes'], authenticated.apiKey);
      projectId = undefined;
    } finally {
      if (relationshipId) await cleanupCli(['relationships', 'delete', relationshipId, '--yes'], authenticated.apiKey);
      if (secondEntryId) await cleanupCli(['entries', 'delete', secondEntryId, '--yes'], authenticated.apiKey);
      if (firstEntryId) await cleanupCli(['entries', 'delete', firstEntryId, '--yes'], authenticated.apiKey);
      if (secondWorkItemId) await cleanupCli(['work-items', 'delete', secondWorkItemId, '--yes'], authenticated.apiKey);
      if (firstWorkItemId) await cleanupCli(['work-items', 'delete', firstWorkItemId, '--yes'], authenticated.apiKey);
      if (projectId) await cleanupCli(['projects', 'delete', projectId, '--yes'], authenticated.apiKey);
    }
  });

  test('covers team, team membership, user, and audit commands', async ({authenticated}) => {
    const secondUserId = authenticated.secondUserId;
    test.skip(
      !authenticated.userId || !secondUserId || secondUserId === authenticated.userId,
      'Requires a second active user; session login discovers one automatically, pre-existing keys need E2E_SECOND_USER_ID.',
    );
    test.skip(!authenticated.isAdminLike, 'Requires an ADMIN or SUPERADMIN API-key owner.');

    let projectId: string | undefined;
    let teamId: string | undefined;
    let projectTeamLinked = false;
    let teamMemberAdded = false;
    try {
      const project = await runJson<any>([
        'projects', 'create', '--name', uniqueName('team-project'), '--owner-user', authenticated.userId!,
      ], authenticated.apiKey);
      projectId = project.id as string;

      const createdTeam = await runJson<any>([
        'teams', 'create',
        '--name', uniqueName('team'),
        '--owner-user', authenticated.userId!,
        '--description', 'CLI E2E team',
      ], authenticated.apiKey);
      teamId = createdTeam.id as string;
      expect(teamId).toBeTruthy();

      const listedTeams = await runJson<any[]>(['teams', 'list', '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(listedTeams).toEqual(expect.arrayContaining([expect.objectContaining({id: teamId})]));

      const fetchedTeam = await runJson<any>(['teams', 'get', teamId], authenticated.apiKey);
      expect(fetchedTeam.id).toBe(teamId);

      const updatedTeam = await runJson<any>([
        'teams', 'update', teamId,
        '--name', uniqueName('updated-team'),
        '--description', 'CLI E2E updated team',
      ], authenticated.apiKey);
      expect(updatedTeam.id).toBe(teamId);

      const teamMembers = await runJson<any[]>(['teams', 'members', 'list', teamId, '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(teamMembers).toEqual(expect.arrayContaining([expect.objectContaining({userId: authenticated.userId})]));

      const addedTeamMember = await runJson<any>([
        'teams', 'members', 'add', teamId, '--user-id', secondUserId!, '--role', 'TEAM_MEMBER',
      ], authenticated.apiKey);
      expect(addedTeamMember).toMatchObject({teamId, userId: secondUserId, role: 'TEAM_MEMBER'});
      teamMemberAdded = true;

      const updatedTeamMembers = await runJson<any[]>(['teams', 'members', 'list', teamId, '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(updatedTeamMembers).toEqual(expect.arrayContaining([expect.objectContaining({userId: secondUserId})]));

      const linkedTeam = await runJson<any>([
        'projects', 'teams', 'add', projectId!, '--team-id', teamId, '--role', 'EDITOR',
      ], authenticated.apiKey);
      expect(linkedTeam).toMatchObject({projectId, teamId, role: 'EDITOR'});
      projectTeamLinked = true;

      const projectTeams = await runJson<any[]>(['projects', 'teams', 'list', projectId!, '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(projectTeams).toEqual(expect.arrayContaining([expect.objectContaining({teamId})]));

      const teamProjects = await runJson<any[]>(['teams', 'projects', teamId, '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(teamProjects).toEqual(expect.arrayContaining([expect.objectContaining({projectId})]));

      const user = await runJson<any>(['users', 'get', authenticated.userId!], authenticated.apiKey);
      expect(user.id).toBe(authenticated.userId);

      const auditLogs = await runJson<any[]>(['audit-logs', 'list', '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(Array.isArray(auditLogs)).toBe(true);
      const projectAuditLogs = await runJson<any[]>(['audit-logs', 'project', projectId!, '--page', '0', '--size', '25'], authenticated.apiKey);
      expect(projectAuditLogs).toEqual(expect.arrayContaining([expect.objectContaining({projectId})]));

      await runJson(['projects', 'teams', 'remove', projectId!, teamId, '--yes'], authenticated.apiKey);
      projectTeamLinked = false;
      await runJson(['teams', 'members', 'remove', teamId, secondUserId!, '--yes'], authenticated.apiKey);
      teamMemberAdded = false;
      await runJson(['teams', 'delete', teamId, '--yes'], authenticated.apiKey);
      teamId = undefined;
      await runJson(['projects', 'delete', projectId!, '--yes'], authenticated.apiKey);
      projectId = undefined;
    } finally {
      if (projectTeamLinked && projectId && teamId) {
        await cleanupCli(['projects', 'teams', 'remove', projectId, teamId, '--yes'], authenticated.apiKey);
      }
      if (teamMemberAdded && teamId) {
        await cleanupCli(['teams', 'members', 'remove', teamId, secondUserId!, '--yes'], authenticated.apiKey);
      }
      if (teamId) await cleanupCli(['teams', 'delete', teamId, '--yes'], authenticated.apiKey);
      if (projectId) await cleanupCli(['projects', 'delete', projectId, '--yes'], authenticated.apiKey);
    }
  });
});
