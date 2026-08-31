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
  ProjectMember,
  ProjectTeam,
  WorkItem,
  WorkItemResponse,
  Entry,
  Relationship,
  SearchResult,
  Team,
  TeamMember,
  UserIdentity,
  ContentOrderItem,
  AuditLog,
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

function collectRequiredOption(values: string[] | undefined, optionName: string): string[] {
  if (!values || values.length === 0) {
    throw new CliError(`At least one ${optionName} is required.`);
  }
  return values.map((value) => requireText(value, `--${optionName}`));
}

function parseEntityReference(value: string, optionName: string): { entityType: string; entityId: string } {
  const separator = value.indexOf(":");
  if (separator <= 0 || separator === value.length - 1) {
    throw new CliError(`Invalid ${optionName} '${value}'. Use TYPE:<id>.`);
  }
  return {
    entityType: requireText(value.slice(0, separator), `--${optionName}`).toUpperCase(),
    entityId: requireText(value.slice(separator + 1), `--${optionName}`),
  };
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

function addPageOptions(command: Command, defaultSize = "50"): Command {
  return command
    .option("--page <number>", "Page number (zero-based)", "0")
    .option("--size <number>", "Items per page; the server caps this at 100", defaultSize);
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
  .version("1.0.0")
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
addAgentHelp(projects, "Use command-specific help for the required API-key scope.");

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

addAgentHelp(
  projects
    .command("create")
    .description("Create a project")
    .requiredOption("--name <name>", "Project name")
    .option("--owner-user <userId>", "Project owner user id; repeat for multiple owners", collectOption)
    .option("--owner-team <teamId>", "Project owner team id; repeat for multiple owners", collectOption),
  `Permissions: projects:write
At least one --owner-user or --owner-team is required.

Examples:
  windrunner projects create --name "Platform work" --owner-user user-1
  windrunner projects create --name "Shared work" --owner-team team-1 --dry-run --json`,
).action(
  async (
    options: { name: string; ownerUser?: string[]; ownerTeam?: string[] },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const ownerUserIds = options.ownerUser?.map((value) => requireText(value, "--owner-user")) ?? [];
    const ownerTeamIds = options.ownerTeam?.map((value) => requireText(value, "--owner-team")) ?? [];
    if (ownerUserIds.length === 0 && ownerTeamIds.length === 0) {
      throw new CliError("At least one --owner-user or --owner-team is required.");
    }
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.post<Project>("/projects", {
        name: requireText(options.name, "--name"),
        ownerUserIds,
        ownerTeamIds,
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  projects
    .command("update")
    .description("Update a project")
    .argument("<projectId>", "Project id")
    .requiredOption("--name <name>", "Project name"),
  `Permissions: projects:write

Example:
  windrunner projects update PROJECT_ID --name "Updated project"`,
).action(async (projectId: string, options: { name: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.put<Project>(`/projects/${encode(projectId)}`, {
      name: requireText(options.name, "--name"),
    }),
    globalOptions,
  );
});

addAgentHelp(
  projects
    .command("delete")
    .description("Delete a project and all of its content")
    .argument("<projectId>", "Project id"),
  `Permissions: projects:write
This permanently deletes the project, work items, entries, relationships, and access links.
Use --dry-run to preview the request. Use --yes only when deletion is explicitly intended.

Example:
  windrunner projects delete PROJECT_ID --yes`,
).action(async (projectId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Delete project ${projectId}? This cannot be undone.`, globalOptions);
  }
  printResponse(await client.delete(`/projects/${encode(projectId)}`), globalOptions);
});

const projectMembers = projects.command("members").description("Manage project user access");
addAgentHelp(projectMembers, "Project membership changes require project owner access.");

addAgentHelp(
  addPageOptions(
    projectMembers
      .command("list")
      .description("List users with access to a project")
      .argument("<projectId>", "Project id"),
  ),
  `Permissions: project_access:read

Example:
  windrunner projects members list PROJECT_ID --json`,
).action(async (projectId: string, options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<ProjectMember[]>(
      `/projects/${encode(projectId)}/members${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
      })}`,
    ),
    globalOptions,
  );
});

addAgentHelp(
  projectMembers
    .command("add")
    .description("Add or update a project user")
    .argument("<projectId>", "Project id")
    .requiredOption("--user-id <userId>", "User id")
    .option("--role <role>", "Project role: OWNER, EDITOR, or VIEWER", "VIEWER"),
  `Permissions: project_access:write

Example:
  windrunner projects members add PROJECT_ID --user-id USER_ID --role EDITOR`,
).action(
  async (projectId: string, options: { userId: string; role: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.post<ProjectMember>(`/projects/${encode(projectId)}/members`, {
        userId: requireText(options.userId, "--user-id"),
        role: options.role,
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  projectMembers
    .command("remove")
    .description("Remove a user from a project")
    .argument("<projectId>", "Project id")
    .argument("<userId>", "User id"),
  `Permissions: project_access:write

Example:
  windrunner projects members remove PROJECT_ID USER_ID --yes`,
).action(async (projectId: string, userId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Remove user ${userId} from project ${projectId}?`, globalOptions);
  }
  printResponse(
    await client.delete(`/projects/${encode(projectId)}/members/${encode(userId)}`),
    globalOptions,
  );
});

const projectTeams = projects.command("teams").description("Manage project team access");
addAgentHelp(projectTeams, "Project team links require project owner access.");

addAgentHelp(
  addPageOptions(
    projectTeams
      .command("list")
      .description("List teams linked to a project")
      .argument("<projectId>", "Project id"),
  ),
  `Permissions: project_access:read

Example:
  windrunner projects teams list PROJECT_ID --json`,
).action(async (projectId: string, options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<ProjectTeam[]>(
      `/projects/${encode(projectId)}/teams${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
      })}`,
    ),
    globalOptions,
  );
});

addAgentHelp(
  projectTeams
    .command("add")
    .description("Add or update a project team")
    .argument("<projectId>", "Project id")
    .requiredOption("--team-id <teamId>", "Team id")
    .option("--role <role>", "Project role: OWNER, EDITOR, or VIEWER", "VIEWER"),
  `Permissions: project_access:write

Example:
  windrunner projects teams add PROJECT_ID --team-id TEAM_ID --role EDITOR`,
).action(
  async (projectId: string, options: { teamId: string; role: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.post<ProjectTeam>(`/projects/${encode(projectId)}/teams`, {
        teamId: requireText(options.teamId, "--team-id"),
        role: options.role,
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  projectTeams
    .command("remove")
    .description("Unlink a team from a project")
    .argument("<projectId>", "Project id")
    .argument("<teamId>", "Team id"),
  `Permissions: project_access:write

Example:
  windrunner projects teams remove PROJECT_ID TEAM_ID --yes`,
).action(async (projectId: string, teamId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Unlink team ${teamId} from project ${projectId}?`, globalOptions);
  }
  printResponse(
    await client.delete(`/projects/${encode(projectId)}/teams/${encode(teamId)}`),
    globalOptions,
  );
});

addAgentHelp(
  projects
    .command("reorder")
    .description("Reorder work items and entries in a project content stream")
    .argument("<projectId>", "Project id")
    .requiredOption("--item <type:id>", "Ordered item in WORK_ITEM:<id> or ENTRY:<id> format", collectOption)
    .option("--parent-id <workItemId>", "Parent work item id; omit for the project root"),
  `Permissions: work_items:write and entries:write
Repeat --item in the desired order.

Example:
  windrunner projects reorder PROJECT_ID \\
    --item WORK_ITEM:item-1 --item ENTRY:entry-1 --item WORK_ITEM:item-2`,
).action(
  async (projectId: string, options: { item: string[]; parentId?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const items = options.item.map((value) => parseEntityReference(value, "item"));
    printResponse(
      await client.put<ContentOrderItem[]>(`/projects/${encode(projectId)}/content-order`, {
        ...(options.parentId === undefined ? {} : { parentWorkItemId: options.parentId }),
        items,
      }),
      globalOptions,
    );
  },
);

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

addAgentHelp(
  workItems
    .command("move")
    .description("Move a work item to a different content position")
    .argument("<workItemId>", "Work item id")
    .option("--parent-id <workItemId>", "Destination parent work item id; omit for the project root")
    .option("--before <type:id>", "Place before WORK_ITEM:<id> or ENTRY:<id> in the destination stream"),
  `Permissions: work_items:write

Examples:
  windrunner work-items move WORK_ITEM_ID --parent-id PARENT_ID
  windrunner work-items move WORK_ITEM_ID --before ENTRY:entry-1`,
).action(
  async (workItemId: string, options: { parentId?: string; before?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const before = options.before === undefined ? undefined : parseEntityReference(options.before, "before");
    printResponse(
      await client.put<WorkItemResponse>(`/work-items/${encode(workItemId)}/move`, {
        ...(options.parentId === undefined ? {} : { parentWorkItemId: options.parentId }),
        ...(before === undefined
          ? {}
          : { beforeEntityType: before.entityType, beforeEntityId: before.entityId }),
      }),
      globalOptions,
    );
  },
);

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
  entries
    .command("get")
    .description("Get an entry")
    .argument("<entryId>", "Entry id"),
  `Permissions: entries:read

Example:
  windrunner entries get ENTRY_ID --json`,
).action(async (entryId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(await client.get<Entry>(`/entries/${encode(entryId)}`), globalOptions);
});

addAgentHelp(
  entries
    .command("update")
    .description("Update an entry")
    .argument("<entryId>", "Entry id")
    .requiredOption("--body <body>", "Entry body")
    .option("--type <type>", "Entry type; defaults to COMMENT when omitted"),
  `Permissions: entries:write
Required: --body. The API treats an omitted type as COMMENT.

Example:
  windrunner entries update ENTRY_ID --body "Updated context" --type EVIDENCE`,
).action(async (entryId: string, options: { body: string; type?: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.put<Entry>(`/entries/${encode(entryId)}`, {
      body: requireText(options.body, "--body"),
      ...(options.type === undefined ? {} : { type: options.type }),
    }),
    globalOptions,
  );
});

addAgentHelp(
  entries
    .command("delete")
    .description("Delete an entry")
    .argument("<entryId>", "Entry id"),
  `Permissions: entries:write
This permanently deletes the entry and its relationships.
Use --dry-run to preview the request.

Example:
  windrunner entries delete ENTRY_ID --yes`,
).action(async (entryId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Delete entry ${entryId}? This cannot be undone.`, globalOptions);
  }
  printResponse(await client.delete(`/entries/${encode(entryId)}`), globalOptions);
});

addAgentHelp(
  program
    .command("search")
    .description("Search project work items, entries, and relationships")
    .argument("<projectId>", "Project id")
    .argument("<query>", "Search query")
    .option("--limit <number>", "Maximum number of matches"),
  `Permissions: work_items:read, entries:read, and relationships:read

Example:
  windrunner search PROJECT_ID "login failure" --limit 20 --json`,
)
  .action(async (projectId: string, query: string, options: { limit?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const limit = numberValue(options.limit, "limit");
    printResponse(
      await client.get<SearchResult>(
        `/projects/${encode(projectId)}/search${queryString({ q: query, limit })}`,
      ),
      globalOptions,
    );
  });

const relationships = program.command("relationships").description("Manage work item relationships");
addAgentHelp(relationships, "Relationships connect work items and entries with a type and optional reason.");

addAgentHelp(
  addPageOptions(
    relationships
      .command("list")
      .description("List relationships in a project")
      .argument("<projectId>", "Project id")
      .option("--type <type>", "Filter by relationship type")
      .option("--created-after <timestamp>", "Only return relationships created after an ISO-8601 timestamp"),
  ),
  `Permissions: relationships:read

Example:
  windrunner relationships list PROJECT_ID --type BLOCKED_BY --json`,
).action(
  async (
    projectId: string,
    options: { page: string; size: string; type?: string; createdAfter?: string },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.get<Relationship[]>(
        `/projects/${encode(projectId)}/relationships${queryString({
          page: numberValue(options.page, "page"),
          size: numberValue(options.size, "size"),
          type: options.type,
          created_after: options.createdAfter,
        })}`,
      ),
      globalOptions,
    );
  },
);

addAgentHelp(
  relationships
    .command("create")
    .description("Create a relationship")
    .argument("<projectId>", "Project id")
    .requiredOption("--from <type:id>", "Source entity in WORK_ITEM:<id> or ENTRY:<id> format")
    .requiredOption("--to <type:id>", "Target entity in WORK_ITEM:<id> or ENTRY:<id> format")
    .requiredOption("--type <type>", "Relationship type")
    .option("--reason <reason>", "Relationship reason")
    .option("--source-entry-id <entryId>", "Entry supporting the relationship"),
  `Permissions: relationships:write

Example:
  windrunner relationships create PROJECT_ID \\
    --from WORK_ITEM:item-1 --to WORK_ITEM:item-2 --type BLOCKED_BY \\
    --reason "Waiting on the database migration"`,
).action(
  async (
    projectId: string,
    options: {
      from: string;
      to: string;
      type: string;
      reason?: string;
      sourceEntryId?: string;
    },
    command: Command,
  ) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    const from = parseEntityReference(options.from, "from");
    const to = parseEntityReference(options.to, "to");
    printResponse(
      await client.post<Relationship>(`/projects/${encode(projectId)}/relationships`, {
        fromEntityType: from.entityType,
        fromEntityId: from.entityId,
        toEntityType: to.entityType,
        toEntityId: to.entityId,
        type: requireText(options.type, "--type"),
        ...(options.reason === undefined ? {} : { reason: options.reason }),
        ...(options.sourceEntryId === undefined ? {} : { sourceEntryId: options.sourceEntryId }),
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  relationships
    .command("update-reason")
    .description("Update or clear a relationship reason")
    .argument("<relationshipId>", "Relationship id")
    .option("--reason <reason>", "New reason; omit to clear the reason"),
  `Permissions: relationships:write

Examples:
  windrunner relationships update-reason RELATIONSHIP_ID --reason "New explanation"
  windrunner relationships update-reason RELATIONSHIP_ID --dry-run`,
).action(async (relationshipId: string, options: { reason?: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.put<Relationship>(`/relationships/${encode(relationshipId)}/reason`, {
      reason: options.reason ?? null,
    }),
    globalOptions,
  );
});

addAgentHelp(
  relationships
    .command("delete")
    .description("Delete a relationship")
    .argument("<relationshipId>", "Relationship id"),
  `Permissions: relationships:write
Use --dry-run to preview the request.

Example:
  windrunner relationships delete RELATIONSHIP_ID --yes`,
).action(async (relationshipId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Delete relationship ${relationshipId}? This cannot be undone.`, globalOptions);
  }
  printResponse(await client.delete(`/relationships/${encode(relationshipId)}`), globalOptions);
});

const teams = program.command("teams").description("Manage teams");
addAgentHelp(teams, "Team creation, updates, deletion, and membership changes require an admin-like API-key owner.");

addAgentHelp(
  addPageOptions(
    teams
      .command("list")
      .description("List teams"),
  ),
  `Permissions: teams:read

Example:
  windrunner teams list --json`,
).action(async (options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<Team[]>(`/teams${queryString({
      page: numberValue(options.page, "page"),
      size: numberValue(options.size, "size"),
    })}`),
    globalOptions,
  );
});

addAgentHelp(
  teams
    .command("get")
    .description("Get a team")
    .argument("<teamId>", "Team id"),
  `Permissions: teams:read

Example:
  windrunner teams get TEAM_ID --json`,
).action(async (teamId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(await client.get<Team>(`/teams/${encode(teamId)}`), globalOptions);
});

addAgentHelp(
  teams
    .command("create")
    .description("Create a team")
    .requiredOption("--name <name>", "Team name")
    .requiredOption("--owner-user <userId>", "Team owner user id; repeat for multiple owners", collectOption)
    .option("--description <description>", "Team description"),
  `Permissions: teams:write
At least one --owner-user is required.

Example:
  windrunner teams create --name "Platform" --owner-user user-1 --description "Platform team"`,
).action(
  async (options: { name: string; ownerUser: string[]; description?: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.post<Team>("/teams", {
        name: requireText(options.name, "--name"),
        ownerUserIds: collectRequiredOption(options.ownerUser, "--owner-user"),
        ...(options.description === undefined ? {} : { description: options.description }),
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  teams
    .command("update")
    .description("Update a team")
    .argument("<teamId>", "Team id")
    .requiredOption("--name <name>", "Team name")
    .requiredOption("--description <description>", "Team description; use an empty value to clear it"),
  `Permissions: teams:write
Both fields are required because the API accepts a full team representation.

Example:
  windrunner teams update TEAM_ID --name "Platform engineering" --description "Owns platform services"`,
).action(
  async (teamId: string, options: { name: string; description: string }, command: Command) => {
    const globalOptions = getGlobalOptions(command);
    const client = new WindrunnerClient(globalOptions);
    printResponse(
      await client.put<Team>(`/teams/${encode(teamId)}`, {
        name: requireText(options.name, "--name"),
        description: options.description,
      }),
      globalOptions,
    );
  },
);

addAgentHelp(
  teams
    .command("delete")
    .description("Delete a team")
    .argument("<teamId>", "Team id"),
  `Permissions: teams:write
This permanently deletes the team and removes its memberships and project links.
Use --dry-run to preview the request.

Example:
  windrunner teams delete TEAM_ID --yes`,
).action(async (teamId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Delete team ${teamId}? This cannot be undone.`, globalOptions);
  }
  printResponse(await client.delete(`/teams/${encode(teamId)}`), globalOptions);
});

const teamMembers = teams.command("members").description("Manage team membership");
addAgentHelp(teamMembers, "Team membership changes require an admin-like API-key owner.");

addAgentHelp(
  addPageOptions(
    teamMembers
      .command("list")
      .description("List team members")
      .argument("<teamId>", "Team id"),
  ),
  `Permissions: team_members:read

Example:
  windrunner teams members list TEAM_ID --json`,
).action(async (teamId: string, options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<TeamMember[]>(
      `/teams/${encode(teamId)}/members${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
      })}`,
    ),
    globalOptions,
  );
});

addAgentHelp(
  teamMembers
    .command("add")
    .description("Add a user to a team")
    .argument("<teamId>", "Team id")
    .requiredOption("--user-id <userId>", "User id")
    .option("--role <role>", "Team role: TEAM_OWNER or TEAM_MEMBER", "TEAM_MEMBER"),
  `Permissions: team_members:write

Example:
  windrunner teams members add TEAM_ID --user-id USER_ID --role TEAM_MEMBER`,
).action(async (teamId: string, options: { userId: string; role: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.post<TeamMember>(`/teams/${encode(teamId)}/members`, {
      userId: requireText(options.userId, "--user-id"),
      role: options.role,
    }),
    globalOptions,
  );
});

addAgentHelp(
  teamMembers
    .command("remove")
    .description("Remove a user from a team")
    .argument("<teamId>", "Team id")
    .argument("<userId>", "User id"),
  `Permissions: team_members:write

Example:
  windrunner teams members remove TEAM_ID USER_ID --yes`,
).action(async (teamId: string, userId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  if (!globalOptions.dryRun) {
    await confirmDelete(`Remove user ${userId} from team ${teamId}?`, globalOptions);
  }
  printResponse(await client.delete(`/teams/${encode(teamId)}/members/${encode(userId)}`), globalOptions);
});

addAgentHelp(
  addPageOptions(
    teams
      .command("projects")
      .description("List projects linked to a team")
      .argument("<teamId>", "Team id"),
  ),
  `Permissions: team_projects:read

Example:
  windrunner teams projects TEAM_ID --json`,
).action(async (teamId: string, options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<ProjectTeam[]>(
      `/teams/${encode(teamId)}/projects${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
      })}`,
    ),
    globalOptions,
  );
});

const users = program.command("users").description("Resolve users");
addAgentHelp(users, "Only limited identity fields are returned by the external API.");

addAgentHelp(
  users
    .command("get")
    .description("Get limited user identity information")
    .argument("<userId>", "User id"),
  `Permissions: users:read

Example:
  windrunner users get USER_ID --json`,
).action(async (userId: string, _options: unknown, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(await client.get<UserIdentity>(`/users/${encode(userId)}`), globalOptions);
});

const auditLogs = program.command("audit-logs").description("Read audit logs");
addAgentHelp(auditLogs, "Audit log access requires an administrator or superadministrator API-key owner.");

addAgentHelp(
  addPageOptions(
    auditLogs
      .command("list")
      .description("List platform audit logs"),
    "20",
  ),
  `Permissions: audit_logs:read

Example:
  windrunner audit-logs list --json`,
).action(async (options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<AuditLog[]>(`/audit-logs${queryString({
      page: numberValue(options.page, "page"),
      size: numberValue(options.size, "size"),
    })}`),
    globalOptions,
  );
});

addAgentHelp(
  addPageOptions(
    auditLogs
      .command("project")
      .description("List audit logs for a project")
      .argument("<projectId>", "Project id"),
    "20",
  ),
  `Permissions: audit_logs:read

Example:
  windrunner audit-logs project PROJECT_ID --json`,
).action(async (projectId: string, options: { page: string; size: string }, command: Command) => {
  const globalOptions = getGlobalOptions(command);
  const client = new WindrunnerClient(globalOptions);
  printResponse(
    await client.get<AuditLog[]>(
      `/projects/${encode(projectId)}/audit-logs${queryString({
        page: numberValue(options.page, "page"),
        size: numberValue(options.size, "size"),
      })}`,
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
