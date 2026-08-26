#!/usr/bin/env node

import { createInterface } from "node:readline/promises";
import { stdin as input, stderr as output } from "node:process";
import { Command } from "commander";

import { CliError, WindrunnerClient } from "./client.js";
import type {
  ApiResponse,
  Assignee,
  DryRunResult,
  GlobalOptions,
  JsonObject,
  Project,
  WorkItem,
  WorkItemResponse,
  Entry,
} from "./types.js";

function getGlobalOptions(command: Command): GlobalOptions {
  const options = command.optsWithGlobals() as GlobalOptions;
  return {
    url: options.url || process.env.WINDRUNNER_URL || "http://localhost:8080",
    json: Boolean(options.json),
    dryRun: Boolean(options.dryRun),
    yes: Boolean(options.yes),
  };
}

function printResult(value: unknown, options: GlobalOptions): void {
  if (options.json) {
    console.log(JSON.stringify(value));
    return;
  }
  console.log(JSON.stringify(value, null, 2));
}

function printResponse<T>(response: ApiResponse<T> | DryRunResult, options: GlobalOptions): void {
  if (isDryRunResult(response)) {
    printResult(response, options);
    return;
  }
  printResult(response.data, options);
}

function isDryRunResult(value: ApiResponse<unknown> | DryRunResult): value is DryRunResult {
  return "dryRun" in value && value.dryRun === true;
}

function encode(value: string): string {
  return encodeURIComponent(value);
}

function numberValue(value: string | undefined, name: string): number | undefined {
  if (value === undefined) return undefined;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new CliError(`--${name} must be a non-negative integer.`);
  }
  return parsed;
}

function queryString(parameters: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value !== undefined && value !== "") {
      query.set(key, String(value));
    }
  }
  const encoded = query.toString();
  return encoded ? `?${encoded}` : "";
}

function collectOption(value: string, previous: string[] = []): string[] {
  return [...previous, value];
}

function parseAssignees(values: string[] | undefined): Assignee[] | undefined {
  if (values === undefined) return undefined;
  return values.map((value) => {
    const separator = value.indexOf(":");
    if (separator <= 0 || separator === value.length - 1) {
      throw new CliError(`Invalid assignee '${value}'. Use USER:<id> or TEAM:<id>.`);
    }
    const assigneeType = value.slice(0, separator).toUpperCase();
    const assigneeId = value.slice(separator + 1).trim();
    if (assigneeType !== "USER" && assigneeType !== "TEAM") {
      throw new CliError(`Invalid assignee type '${assigneeType}'. Use USER or TEAM.`);
    }
    if (!assigneeId) {
      throw new CliError("Assignee id is required.");
    }
    return { assigneeType, assigneeId };
  });
}

function requireText(value: string | undefined, optionName: string): string {
  if (!value || !value.trim()) {
    throw new CliError(`${optionName} is required.`);
  }
  return value.trim();
}

function validateDate(value: string | undefined): string | undefined {
  if (value === undefined) return undefined;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new CliError("--due-date must use YYYY-MM-DD format.");
  }
  return value;
}

function workItemFields(options: Record<string, unknown>, current?: WorkItem): WorkItem {
  const title = options.title === undefined ? current?.title : String(options.title);
  const result: WorkItem = {
    title: requireText(title, "--title"),
  };

  const values: Array<[keyof WorkItem, unknown]> = [
    ["type", options.type === undefined ? current?.type : options.type],
    ["status", options.status === undefined ? current?.status : options.status],
    ["dueDate", options.dueDate === undefined ? current?.dueDate : validateDate(String(options.dueDate))],
    ["priority", options.priority === undefined ? current?.priority : options.priority],
    [
      "parentWorkItemId",
      options.parentId === undefined ? current?.parentWorkItemId : String(options.parentId),
    ],
  ];

  for (const [key, value] of values) {
    if (value !== undefined) {
      result[key] = value as never;
    }
  }
  return result;
}

async function confirmDelete(message: string, options: GlobalOptions): Promise<void> {
  if (options.yes) return;
  if (!input.isTTY) {
    throw new CliError(`${message} Re-run with --yes when using a non-interactive terminal.`);
  }

  const readline = createInterface({ input, output });
  try {
    const answer = await readline.question(`${message} [y/N] `);
    if (!/^(y|yes)$/i.test(answer.trim())) {
      throw new CliError("Cancelled.");
    }
  } finally {
    readline.close();
  }
}

function addPagination(command: Command): Command {
  return command
    .option("--page <number>", "Page number (zero-based)", "0")
    .option("--size <number>", "Items per page; the server caps this at 100", "50")
    .option("--updated-after <timestamp>", "Only return records updated after an ISO-8601 timestamp (UTC recommended)");
}

const sharedHelp = `
Environment:
  WINDRUNNER_URL       Server URL (default: http://localhost:8080)
  WINDRUNNER_API_KEY   Bearer API key; keep it in the environment, not in arguments

Global options:
  --url <url>          Override WINDRUNNER_URL
  --json               Print compact machine-readable JSON
  --dry-run            Preview a mutation without sending the mutation request
  -y, --yes            Skip destructive-operation confirmation prompts

Output and safety:
  Normal output is pretty JSON. Use --json when another agent or script will parse it.
  Destructive commands require confirmation unless --yes is explicitly provided.
  A dry run never sends a mutation request and does not require an API key.
`;

function addAgentHelp(command: Command, details: string): Command {
  return command.addHelpText("after", `${sharedHelp}\n${details.trim()}\n`);
}

const program = new Command();

program
  .name("windrunner")
  .description("Command-line interface for Windrunner")
  .version("0.1.0")
  .option("--url <url>", "Windrunner server URL", process.env.WINDRUNNER_URL || "http://localhost:8080")
  .option("--json", "Print compact JSON output")
  .option("--dry-run", "Preview mutations without sending them")
  .option("-y, --yes", "Skip confirmation prompts");

addAgentHelp(
  program,
  `Quick start:
  export WINDRUNNER_URL=http://localhost:8080
  export WINDRUNNER_API_KEY=your-api-key
  windrunner projects list --json

Use '<command> --help' for command-specific arguments and examples.`,
);

const projects = program.command("projects").description("Manage projects");
addAgentHelp(projects, "Permissions: projects:read for both project commands.");

addAgentHelp(
  projects
    .command("list")
    .description("List projects visible to the API key")
    .option("--page <number>", "Page number (zero-based)", "0")
    .option("--size <number>", "Items per page; the server caps this at 100", "50"),
  `Permissions: projects:read

Examples:
  windrunner projects list
  windrunner projects list --page 1 --size 25 --json`,
)
  .action(async (options: { page: string; size: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const response = await client.get<Project[]>(
      `/projects${queryString({ page: numberValue(options.page, "page"), size: numberValue(options.size, "size") })}`,
    );
    printResponse(response, globalOptions);
  });

addAgentHelp(
  projects
    .command("get")
    .description("Get a project")
    .argument("<projectId>", "Project id"),
  `Permissions: projects:read

Example:
  windrunner projects get PROJECT_ID --json`,
)
  .action(async (projectId: string, _options: unknown, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(await client.get<Project>(`/projects/${encode(projectId)}`), globalOptions);
  });

const workItems = program.command("work-items").description("Manage work items");
addAgentHelp(workItems, "Use --json for machine-readable results. Work item type and status values are validated by the server.");

addAgentHelp(
  addPagination(
    workItems
      .command("list")
      .description("List work items in a project")
      .argument("<projectId>", "Project id")
      .option("--status <status>", "Filter by status")
      .option("--type <type>", "Filter by type")
      .option("--priority <priority>", "Filter by priority"),
  ),
  `Permissions: work_items:read

Examples:
  windrunner work-items list PROJECT_ID
  windrunner work-items list PROJECT_ID --status OPEN --size 25 --json`,
).action(
  async (
    projectId: string,
    options: {
      page: string;
      size: string;
      status?: string;
      type?: string;
      priority?: string;
      updatedAfter?: string;
    },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const response = await client.get<WorkItemResponse[]>(
      `/projects/${encode(projectId)}/work-items${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
        status: options.status,
        type: options.type,
        priority: options.priority,
        updated_after: options.updatedAfter,
      })}`,
    );
    printResponse(response, globalOptions);
  },
);

addAgentHelp(
  workItems
    .command("get")
    .description("Get a work item")
    .argument("<workItemId>", "Work item id"),
  `Permissions: work_items:read

Example:
  windrunner work-items get WORK_ITEM_ID --json`,
)
  .action(async (workItemId: string, _options: unknown, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(await client.get<WorkItemResponse>(`/work-items/${encode(workItemId)}`), globalOptions);
  });

function addWorkItemWriteOptions(command: Command): Command {
  return command
    .requiredOption("--title <title>", "Work item title")
    .option("--type <type>", "Work item type")
    .option("--status <status>", "Work item status")
    .option("--due-date <date>", "Due date in YYYY-MM-DD format")
    .option("--priority <priority>", "Work item priority")
    .option("--parent-id <workItemId>", "Parent work item id")
    .option("--assignee <type:id>", "Assignee in USER:<id> or TEAM:<id> format", collectOption);
}

addAgentHelp(
  addWorkItemWriteOptions(
    workItems
      .command("create")
      .description("Create a work item")
      .argument("<projectId>", "Project id"),
  ),
  `Permissions: work_items:write
Required: --title.
Repeat --assignee for multiple assignments using USER:<id> or TEAM:<id>.

Examples:
  windrunner work-items create PROJECT_ID --title "Fix login"
  windrunner work-items create PROJECT_ID --title "Release" --status OPEN --assignee USER:user-1 --dry-run`,
).action(
  async (
    projectId: string,
    options: {
      title: string;
      type?: string;
      status?: string;
      dueDate?: string;
      priority?: string;
      parentId?: string;
      assignee?: string[];
    },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const body: JsonObject = {
      workItem: workItemFields(options),
    };
    const assignees = parseAssignees(options.assignee);
    if (assignees !== undefined) body.assignees = assignees;
    printResponse(await client.post<WorkItemResponse>(`/projects/${encode(projectId)}/work-items`, body), globalOptions);
  },
);

addAgentHelp(
  addWorkItemWriteOptions(
    workItems
      .command("update")
      .description("Update a work item")
      .argument("<workItemId>", "Work item id"),
  ),
  `Permissions: work_items:read and work_items:write.
Required: --title. The CLI reads the current item first so omitted fields are preserved.

Examples:
  windrunner work-items update WORK_ITEM_ID --title "Updated title"
  windrunner work-items update WORK_ITEM_ID --title "Done" --status DONE --json`,
).action(
  async (
    workItemId: string,
    options: {
      title: string;
      type?: string;
      status?: string;
      dueDate?: string;
      priority?: string;
      parentId?: string;
      assignee?: string[];
    },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    let current: WorkItem | undefined;

    // The API accepts a full WorkItem representation for PUT. Fetching first
    // lets the CLI offer a safe field-oriented update without clearing fields
    // the caller did not mention.
    if (!globalOptions.dryRun) {
      const existing = await client.get<WorkItemResponse>(`/work-items/${encode(workItemId)}`);
      current = existing.data?.workItem;
      if (!current) throw new CliError("Work item response did not include a work item.");
    }

    const body: JsonObject = {
      workItem: workItemFields(options, current),
    };
    const assignees = parseAssignees(options.assignee);
    if (assignees !== undefined) body.assignees = assignees;
    printResponse(await client.put<WorkItemResponse>(`/work-items/${encode(workItemId)}`, body), globalOptions);
  },
);

addAgentHelp(
  workItems
    .command("delete")
    .description("Delete a work item and its descendants")
    .argument("<workItemId>", "Work item id"),
  `Permissions: work_items:write
This permanently deletes the work item, descendants, entries, and relationships.
Use --dry-run to preview the request. Use --yes only when deletion is explicitly intended.

Examples:
  windrunner work-items delete WORK_ITEM_ID --dry-run
  windrunner work-items delete WORK_ITEM_ID --yes`,
)
  .action(async (workItemId: string, _options: unknown, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    if (!globalOptions.dryRun) {
      await confirmDelete(`Delete work item ${workItemId}? This cannot be undone.`, globalOptions);
    }
    printResponse(await client.delete(`/work-items/${encode(workItemId)}`), globalOptions);
  });

const entries = program.command("entries").description("Manage entries");
addAgentHelp(entries, "Entries are attached to work items. Use --json for machine-readable results.");

addAgentHelp(
  addPagination(
    entries
      .command("list")
      .description("List entries attached to a work item")
      .argument("<workItemId>", "Work item id"),
  ),
  `Permissions: entries:read

Example:
  windrunner entries list WORK_ITEM_ID --updated-after 2026-01-01T00:00:00Z --json`,
).action(
  async (
    workItemId: string,
    options: { page: string; size: string; updatedAfter?: string },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const response = await client.get<Entry[]>(
      `/work-items/${encode(workItemId)}/entries${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
        updated_after: options.updatedAfter,
      })}`,
    );
    printResponse(response, globalOptions);
  },
);

addAgentHelp(
  entries
    .command("create")
    .description("Create an entry on a work item")
    .argument("<workItemId>", "Work item id")
    .requiredOption("--body <body>", "Entry body")
    .option("--type <type>", "Entry type"),
  `Permissions: entries:write
Required: --body.

Examples:
  windrunner entries create WORK_ITEM_ID --body "Deployment completed"
  windrunner entries create WORK_ITEM_ID --body "Comment" --type COMMENT --dry-run`,
)
  .action(async (workItemId: string, options: { body: string; type?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const body: Entry = {
      body: requireText(options.body, "--body"),
      ...(options.type === undefined ? {} : { type: options.type }),
    };
    printResponse(await client.post<Entry>(`/work-items/${encode(workItemId)}/entries`, body), globalOptions);
  });

addAgentHelp(
  program
    .command("search")
    .description("Search project work items, entries, and relationships")
    .argument("<projectId>", "Project id")
    .argument("<query>", "Search query")
    .option("--limit <number>", "Maximum number of matches"),
  `Permissions: work_items:read

Example:
  windrunner search PROJECT_ID "login failure" --limit 20 --json`,
)
  .action(async (projectId: string, query: string, options: { limit?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const limit = numberValue(options.limit, "limit");
    printResponse(
      await client.get<JsonObject>(
        `/projects/${encode(projectId)}/search${queryString({ q: query, limit })}`,
      ),
      globalOptions,
    );
  });

try {
  await program.parseAsync(process.argv);
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`Error: ${message}`);
  process.exitCode = 1;
}
