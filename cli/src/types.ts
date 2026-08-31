export type JsonObject = Record<string, unknown>;

export interface ApiError {
  code?: string;
  message?: string;
  field?: string;
  details?: unknown;
}

export interface ApiResponse<T> {
  data: T | null;
  errors?: ApiError[];
  meta?: JsonObject | null;
}

export interface GlobalOptions {
  url: string;
  json?: boolean;
  dryRun?: boolean;
  yes?: boolean;
}

export interface Project {
  id: string;
  name: string;
  createdByUserId?: string;
  createdAt?: string;
  updatedAt?: string;
  archivedAt?: string | null;
}

export interface ProjectMember {
  projectId: string;
  userId: string;
  role: string;
  createdAt?: string;
}

export interface ProjectTeam {
  projectId: string;
  teamId: string;
  role: string;
  createdAt?: string;
}

export interface WorkItem {
  id?: string;
  projectId?: string;
  parentWorkItemId?: string | null;
  sortIndex?: number;
  type?: string;
  title: string;
  status?: string;
  dueDate?: string | null;
  priority?: string | null;
  createdByUserId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Assignee {
  assigneeType: string;
  assigneeId: string;
}

export interface WorkItemResponse {
  workItem: WorkItem;
  assignees: Assignee[];
}

export interface Entry {
  id?: string;
  projectId?: string;
  workItemId?: string;
  sortIndex?: number;
  authorUserId?: string;
  type?: string;
  body: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Relationship {
  id?: string;
  projectId?: string;
  fromEntityType: string;
  fromEntityId: string;
  toEntityType: string;
  toEntityId: string;
  type: string;
  reason?: string | null;
  sourceEntryId?: string | null;
  createdByUserId?: string;
  createdAt?: string;
}

export interface SearchResult {
  workItems: WorkItemResponse[];
  entries: Entry[];
  relationships: Relationship[];
}

export interface Team {
  id: string;
  name: string;
  description?: string | null;
}

export interface TeamMember {
  teamId: string;
  userId: string;
  role: string;
  createdAt?: string;
}

export interface UserIdentity {
  id: string;
  username: string;
  displayName?: string | null;
  title?: string | null;
  bio?: string | null;
}

export interface ContentOrderItem {
  entityType: string;
  entityId: string;
  sortIndex: number;
}

export interface AuditLog {
  id: string;
  occurredAt?: string;
  actorUserId?: string | null;
  actorDisplayName?: string | null;
  action?: string;
  entityType?: string;
  entityId?: string;
  entityDisplayName?: string | null;
  projectId?: string | null;
  projectName?: string | null;
  outcome?: string;
  summary?: string;
  beforeJson?: string | null;
  afterJson?: string | null;
  changesJson?: string | null;
  metadataJson?: string | null;
}

export interface DryRunResult {
  dryRun: true;
  method: string;
  path: string;
  body?: unknown;
}
