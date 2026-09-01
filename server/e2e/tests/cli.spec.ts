import {spawn} from 'node:child_process';
import {resolve} from 'node:path';
import {expect, test} from './helpers';

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

function parseJson(result: CliResult): any {
  expect(result.exitCode, result.stderr).toBe(0);
  return JSON.parse(result.stdout);
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

  test('runs a project, work-item, entry, and search workflow', async ({authenticated}) => {
    test.skip(!authenticated.userId, 'Requires E2E_USER_ID or session login.');

    let projectId: string | undefined;
    try {
      const createdProject = parseJson(await runCli([
        'projects',
        'create',
        '--name',
        uniqueName('project'),
        '--owner-user',
        authenticated.userId!,
        '--json',
      ], authenticated.apiKey));
      const createdProjectId = createdProject.id as string;
      projectId = createdProjectId;
      expect(projectId).toBeTruthy();

      const fetchedProject = parseJson(await runCli([
        'projects',
        'get',
        createdProjectId,
        '--json',
      ], authenticated.apiKey));
      expect(fetchedProject.id).toBe(projectId);

      const updatedProject = parseJson(await runCli([
        'projects',
        'update',
        createdProjectId,
        '--name',
        uniqueName('updated-project'),
        '--json',
      ], authenticated.apiKey));
      expect(updatedProject.id).toBe(projectId);

      const createdWorkItem = parseJson(await runCli([
        'work-items',
        'create',
        createdProjectId,
        '--title',
        uniqueName('work-item'),
        '--type',
        'TASK',
        '--status',
        'OPEN',
        '--json',
      ], authenticated.apiKey));
      const workItemId = createdWorkItem.workItem.id as string;
      expect(workItemId).toBeTruthy();

      const updatedWorkItem = parseJson(await runCli([
        'work-items',
        'update',
        workItemId,
        '--title',
        'CLI E2E updated work item',
        '--json',
      ], authenticated.apiKey));
      expect(updatedWorkItem.workItem.id).toBe(workItemId);
      expect(updatedWorkItem.workItem.title).toBe('CLI E2E updated work item');

      const createdEntry = parseJson(await runCli([
        'entries',
        'create',
        workItemId,
        '--body',
        'CLI E2E entry',
        '--json',
      ], authenticated.apiKey));
      const entryId = createdEntry.id as string;
      expect(entryId).toBeTruthy();

      const updatedEntry = parseJson(await runCli([
        'entries',
        'update',
        entryId,
        '--body',
        'CLI E2E updated entry',
        '--json',
      ], authenticated.apiKey));
      expect(updatedEntry.id).toBe(entryId);
      expect(updatedEntry.body).toBe('CLI E2E updated entry');

      const searchResult = parseJson(await runCli([
        'search',
        createdProjectId,
        'CLI E2E updated',
        '--json',
      ], authenticated.apiKey));
      expect(searchResult.workItems.length + searchResult.entries.length).toBeGreaterThan(0);
    } finally {
      if (projectId) {
        await runCli(['projects', 'delete', projectId, '--yes', '--json'], authenticated.apiKey);
      }
    }
  });
});
