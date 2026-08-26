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

export interface DryRunResult {
  dryRun: true;
  method: string;
  path: string;
  body?: unknown;
}
