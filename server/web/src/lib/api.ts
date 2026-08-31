export type AuthUser = {
  id: string
  username: string
  email: string | null
  displayName: string | null
  title: string | null
  bio: string | null
  timezone: string | null
  status: string | null
  globalRole: string | null
  mustChangePassword: boolean
}

export type ApiKeyScope =
  | 'teams:read'
  | 'teams:write'
  | 'team_members:read'
  | 'team_members:write'
  | 'team_projects:read'
  | 'users:read'
  | 'projects:read'
  | 'projects:write'
  | 'project_access:read'
  | 'project_access:write'
  | 'work_items:read'
  | 'work_items:write'
  | 'entries:read'
  | 'entries:write'
  | 'relationships:read'
  | 'relationships:write'
  | 'audit_logs:read'

export interface ApiKey {
  id: string
  ownerUserId: string
  name: string
  status: string
  createdAt: string
  lastUsedAt: string | null
  revokedAt: string | null
  scopes: ApiKeyScope[]
}

export interface CreatedApiKey extends ApiKey {
  rawKey: string
}

export type SettingDataType = 'number' | 'text' | 'date' | 'boolean'

export type SettingValue = {
  dataType: SettingDataType
  value: unknown
}

export interface Project {
  id: string
  name?: string | null
  title: string | null
  description: string | null
  userId: string | null
  createdByUserId?: string | null
  createdAt?: string
  updatedAt?: string
  ownerUserIds?: string[]
  ownerDisplayNames?: Record<string, string>
}

export interface WorkItem {
  id: string
  projectId: string
  parentWorkItemId: string | null
  sortIndex: number
  type: 'TASK' | 'QUESTION' | 'APPROVAL' | 'REVIEW' | 'DECISION'
  title: string
  status: 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE' | 'WAITING' | 'ANSWERED' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  dueDate: string | null
  priority: string | null
  createdAt?: string
  updatedAt?: string
}

export interface WorkItemAssignee { id?: string; workItemId?: string; assigneeType: 'USER' | 'TEAM'; assigneeId: string }
export interface WorkItemView { workItem: WorkItem; assignees: WorkItemAssignee[] }
export interface Entry { id: string; projectId: string; workItemId: string; sortIndex: number; authorUserId: string; authorDisplayName?: string | null; type: string; body: string; createdAt: string; updatedAt?: string }
export interface Relationship { id: string; projectId: string; fromEntityType: 'WORK_ITEM' | 'ENTRY'; fromEntityId: string; toEntityType: 'WORK_ITEM' | 'ENTRY'; toEntityId: string; type: string; reason: string | null; sourceEntryId: string | null }
export interface ContentOrderItem { entityType: 'WORK_ITEM' | 'ENTRY'; entityId: string; sortIndex: number }
export interface Workspace { workItems: WorkItemView[]; entries: Entry[]; relationships: Relationship[] }

export interface ProjectNode {
  id: string
  projectId: string
  parentNodeId?: string | null
  sortIndex?: number | null
  type: string
  title: string
  fields: ProjectNodeField[]
  childrenCount?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface SubscribedWorkItem {
  userId: string
  projectId: string
  projectName: string
  workItemId: string
  workItemTitle: string
  workItemType: string
  parentWorkItemId: string | null
  parentWorkItemTitle: string | null
  subscribedAt: string
}

export interface SubscribedWorkItemPageResponse {
  items: SubscribedWorkItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface SubscriptionStatus {
  subscribed: boolean
}

export interface UserNotification {
  id: string
  notificationType: string
  actorUserId: string | null
  projectId: string | null
  workItemId: string | null
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface UserNotificationPage {
  items: UserNotification[]
  unreadCount: number
  totalItems: number
}

export type ProjectNodeFieldDataType = 'text' | 'number' | 'boolean' | 'date' | 'user' | 'team'

export interface ProjectNodeField {
  name: string
  label: string
  dataType: ProjectNodeFieldDataType
  value: string | number | boolean | string[] | null
  order?: number | null
  visibleInTree?: boolean | null
}

export interface ProjectNodeEdge {
  id: string
  projectId: string
  fromNodeId: string
  toNodeId: string
  relationType: string
}

export type GraphProposalDecision = 'ACCEPT' | 'REJECT' | 'REQUEST_UPDATE'

export interface GraphChangeProposal {
  id: string
  projectId: string
  chatSessionId: string
  sourceMessageId: string
  sourceText: string
  status: string
  createdAt?: string
  updatedAt?: string
  changes: GraphChangeProposalChange[]
}

export interface GraphChangeProposalChange {
  id: string
  sortIndex: number
  entityType: 'NODE' | 'ENTRY' | 'EDGE'
  action: 'ADD' | 'UPDATE' | 'DELETE'
  targetId: string
  summary: string
  status: string
  feedback?: string | null
  lastMessageId?: string | null
  appliedAt?: string | null
  createdAt?: string
  updatedAt?: string
  node?: ProjectNode | null
  previousNode?: ProjectNode | null
  edge?: ProjectNodeEdge | null
  entry?: Entry | null
  previousEntry?: Entry | null
  relationship?: Relationship | null
  previousRelationship?: Relationship | null
}

export interface User {
  id: string
  username: string
  email: string | null
  displayName: string | null
  title: string | null
  bio: string | null
  timezone: string | null
  status: string | null
  globalRole: string | null
  mustChangePassword: boolean
  createdAt: string
  updatedAt: string
}

export interface UserPageResponse {
  items: User[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface AuditLog {
  id: string
  occurredAt: string
  actorUserId: string | null
  actorDisplayName?: string | null
  action: string
  entityType: string
  entityId: string | null
  entityDisplayName?: string | null
  projectId: string | null
  projectName?: string | null
  outcome: string
  summary: string
  beforeJson: string | null
  afterJson: string | null
  changesJson: string | null
  metadataJson: string | null
}

export interface AuditLogPageResponse {
  items: AuditLog[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface NodePageResponse {
  items: ProjectNode[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface NodeSubtreeResponse {
  items: ProjectNode[]
  truncated: boolean
}

export interface Team {
  id: string
  name: string
  description: string | null
  createdAt?: string
  memberUserIds?: string[]
  memberDisplayNames?: Record<string, string>
  projectCount?: number
  currentUserRole?: 'TEAM_OWNER' | 'TEAM_MEMBER' | null
}

export interface CreateTeamRequest {
  name: string
  description?: string | null
  ownerUserIds: string[]
}

export interface CreateProjectRequest {
  title?: string | null
  description?: string | null
  ownerUserIds: string[]
  ownerTeamIds: string[]
}

export interface TeamMember {
  teamId: string
  userId: string
  role: 'TEAM_OWNER' | 'TEAM_MEMBER'
  createdAt?: string
}

export interface ProjectTeam {
  projectId: string
  teamId: string
  role: 'OWNER' | 'EDITOR' | 'VIEWER'
  createdAt?: string
}

export interface ProjectMember {
  projectId: string
  userId: string
  role: 'OWNER' | 'EDITOR' | 'VIEWER'
  createdAt?: string
}

export interface TeamJoinRequest {
  id: string
  teamId: string
  userId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
  createdAt?: string
  decidedAt?: string | null
  decidedByUserId?: string | null
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatContext {
  selectedNodeId?: string | null
  selectedProposalId?: string | null
  selectedProposalChangeId?: string | null
}

export interface ChatStreamData {
  text?: string
  title?: string
  message?: string
  chatSessionId?: string
  sourceMessageId?: string
  assistantMessageId?: string
}

export interface ChatSessionMessage extends ChatMessage {
  id: string
  chatSessionId: string
  createdAt?: string
}

export interface ChatSession {
  id: string
  status: string
  createdAt?: string
  messages: ChatSessionMessage[]
  contexts: ChatSessionContext[]
}

export interface ChatSessionSummary {
  id: string
  status: string
  createdAt?: string
  updatedAt?: string
  title: string
}

export type ChatContextEntityType = 'PROJECT' | 'TEAM' | 'USER' | 'WORK_ITEM'

export interface ChatSessionContext {
  id: string
  entityType: ChatContextEntityType
  entityId: string
  label: string
  projectId?: string | null
  createdAt?: string
}

export interface ChatSessionPage {
  items: ChatSessionSummary[]
  hasMore: boolean
  offset: number
  limit: number
}

export interface LlmStatus {
  provider: string
  available: boolean
}

export interface SystemInformation {
  serverVersion: string
  llmProvider: string
  llmModel: string
  llmAvailable: boolean
}

export interface ApiError {
  code: string
  message: string
  field?: string | null
  details?: Record<string, unknown> | null
}

export interface ApiMeta {
  requestId?: string | null
  page?: number | null
  size?: number | null
  totalItems?: number | null
  totalPages?: number | null
}

export interface ApiResponse<T> {
  data: T | null
  errors: ApiError[]
  meta: ApiMeta | null
}

function readCookie(name: string) {
  const cookiePrefix = `${name}=`
  return document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(cookiePrefix))
    ?.slice(cookiePrefix.length)
}

function csrfToken() {
  return readCookie('XSRF-TOKEN')
}

function formatClientDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  const offsetMinutes = -date.getTimezoneOffset()
  const offsetSign = offsetMinutes >= 0 ? '+' : '-'
  const absoluteOffsetMinutes = Math.abs(offsetMinutes)
  const offsetHours = String(Math.floor(absoluteOffsetMinutes / 60)).padStart(2, '0')
  const offsetRemainderMinutes = String(absoluteOffsetMinutes % 60).padStart(2, '0')

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${offsetSign}${offsetHours}:${offsetRemainderMinutes}`
}

function applyRequestHeaders(headers: Headers, method: string, hasBody: boolean, body: RequestInit['body']) {
  const isFormDataBody = typeof FormData !== 'undefined' && body instanceof FormData

  if (!headers.has('x-client-datetime')) {
    headers.set('x-client-datetime', formatClientDateTime(new Date()))
  }

  if (hasBody && !isFormDataBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const token = csrfToken()
    if (token && !headers.has('X-CSRF-Token')) {
      headers.set('X-CSRF-Token', token)
    }
  }
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return Boolean(
    value
      && typeof value === 'object'
      && 'data' in value
      && 'errors' in value
      && Array.isArray((value as { errors?: unknown }).errors),
  )
}

function errorMessageFromBody(body: unknown, fallback: string) {
  if (isApiResponse(body) && body.errors.length > 0) {
    return body.errors[0]?.message || fallback
  }
  if (body && typeof body === 'object') {
    if ('message' in body && typeof body.message === 'string') {
      return body.message
    }
    if ('error' in body && body.error && typeof body.error === 'object' && 'message' in body.error && typeof body.error.message === 'string') {
      return body.error.message
    }
  }
  if (typeof body === 'string' && body.trim()) {
    return body
  }

  return fallback
}

async function readJsonBody(response: Response) {
  const text = await response.text()
  if (!text) {
    return undefined
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

export async function requestEnvelope<T>(input: RequestInfo, init?: RequestInit): Promise<ApiResponse<T>> {
  const headers = new Headers(init?.headers)
  const method = (init?.method ?? 'GET').toUpperCase()
  const hasBody = init?.body !== undefined && init?.body !== null
  applyRequestHeaders(headers, method, hasBody, init?.body)

  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })

  const body = await readJsonBody(response)

  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, `Request failed with status ${response.status}`))
  }

  if (response.status === 204) {
    return {
      data: null,
      errors: [],
      meta: null,
    }
  }

  if (isApiResponse(body)) {
    return body as ApiResponse<T>
  }

  return {
    data: body as T,
    errors: [],
    meta: null,
  }
}

export async function request<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const envelope = await requestEnvelope<T>(input, init)
  return envelope.data as T
}

export async function streamRequest<T>(
  input: RequestInfo,
  init: RequestInit,
  onEvent: (event: { event: string; data: T }) => void,
) {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  const hasBody = init.body !== undefined && init.body !== null
  applyRequestHeaders(headers, method, hasBody, init.body)

  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const body = await readJsonBody(response)
    throw new Error(errorMessageFromBody(body, `Request failed with status ${response.status}`))
  }

  if (!response.body) {
    throw new Error('Streaming response body is not available.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })

    const normalized = buffer.replace(/\r\n/g, '\n')
    const segments = normalized.split('\n\n')
    buffer = segments.pop() ?? ''

    for (const segment of segments) {
      const parsed = parseSseSegment<T>(segment)
      if (parsed) {
        onEvent(parsed)
      }
    }

    if (done) {
      const trailing = buffer.trim()
      if (trailing) {
        const parsed = parseSseSegment<T>(trailing)
        if (parsed) {
          onEvent(parsed)
        }
      }
      return
    }
  }
}

function parseSseSegment<T>(segment: string) {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of segment.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  return {
    event: eventName,
    data: JSON.parse(dataLines.join('\n')) as T,
  }
}

export async function fetchCurrentUser() {
  return request<AuthUser>('/api/v1/auth/me', { method: 'GET' })
}

export async function listMyApiKeys() {
  return request<ApiKey[]>('/internal-api/v1/me/api-keys', { method: 'GET' })
}

export async function createMyApiKey(payload: { name: string; scopes: ApiKeyScope[] }) {
  return request<CreatedApiKey>('/internal-api/v1/me/api-keys', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function revokeMyApiKey(apiKeyId: string) {
  return request<void>(`/internal-api/v1/me/api-keys/${apiKeyId}`, { method: 'DELETE' })
}

export async function getMySettings() {
  return request<Record<string, SettingValue>>('/internal-api/v1/me/settings', { method: 'GET' })
}

export async function updateMySetting(key: string, setting: SettingValue) {
  return request<SettingValue>(`/internal-api/v1/me/settings/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify(setting),
  })
}


function normalizeProject(project: {
  id: string
  name?: string | null
  title?: string | null
  description?: string | null
  userId?: string | null
  createdByUserId?: string | null
  createdAt?: string
  updatedAt?: string
  ownerUserIds?: string[]
  ownerDisplayNames?: Record<string, string>
}): Project {
  const title = project.title ?? project.name ?? null

  return {
    id: project.id,
    name: project.name ?? title,
    title,
    description: project.description ?? null,
    userId: project.userId ?? null,
    createdByUserId: project.createdByUserId ?? null,
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
    ownerUserIds: project.ownerUserIds ?? [],
    ownerDisplayNames: project.ownerDisplayNames ?? {},
  }
}

export async function listProjects(): Promise<Project[]> {
  const projects = await request<Array<{
    id: string
    name?: string | null
    title?: string | null
    description?: string | null
    userId?: string | null
    createdByUserId?: string | null
    createdAt?: string
    updatedAt?: string
    ownerUserIds?: string[]
    ownerDisplayNames?: Record<string, string>
  }>>('/internal-api/v1/projects', { method: 'GET' })

  return projects.map(normalizeProject)
}

export async function getProject(id: string): Promise<Project> {
  const project = await request<{
    id: string
    name?: string | null
    title?: string | null
    description?: string | null
    userId?: string | null
    createdByUserId?: string | null
    createdAt?: string
    updatedAt?: string
  }>(`/internal-api/v1/projects/${id}`, { method: 'GET' })

  return normalizeProject(project)
}

export async function getLlmStatus(): Promise<LlmStatus> {
  return request<LlmStatus>('/internal-api/v1/llm/status', { method: 'GET' })
}

export async function getSystemInformation(): Promise<SystemInformation> {
  return request<SystemInformation>('/internal-api/v1/system-information', { method: 'GET' })
}

export interface LlmUsageTotals {
  inputTokens: number
  outputTokens: number
  requests: number
  failures: number
  successRate: number
  avgDurationMs: number
}

export interface LlmUsageProject {
  projectId: string
  inputTokens: number
  outputTokens: number
  requests: number
  failures: number
  successRate: number
  avgDurationMs: number
}

export interface LlmUsageFeature {
  feature: string
  inputTokens: number
  outputTokens: number
  requests: number
  failures: number
  successRate: number
}

export interface LlmUsageProvider {
  provider: string
  model: string
  inputTokens: number
  outputTokens: number
  requests: number
  failures: number
  successRate: number
}

export interface LlmUsageSummary {
  totals: LlmUsageTotals
  byProject: LlmUsageProject[]
  byFeature: LlmUsageFeature[]
  byProviderModel: LlmUsageProvider[]
}

export async function getLlmUsage(projectId?: string, days?: number): Promise<LlmUsageSummary> {
  const params = new URLSearchParams()
  if (projectId) {
    params.set('projectId', projectId)
  }
  if (days) {
    params.set('days', String(days))
  }
  const query = params.toString()
  return request<LlmUsageSummary>(`/internal-api/v1/llm-usage${query ? `?${query}` : ''}`, { method: 'GET' })
}

export async function streamChatSession(
  sessionId: string,
  messages: ChatMessage[],
  context: ChatContext | null | undefined,
  onEvent: (event: { event: string; data: ChatStreamData }) => void,
  signal?: AbortSignal,
  projectIds?: string[],
  targetProjectId?: string,
) {
  const body: { messages: ChatMessage[]; context: ChatContext | null | undefined; projectIds?: string[]; targetProjectId?: string } = { messages, context }
  if (projectIds?.length) {
    body.projectIds = projectIds
  }
  if (targetProjectId) body.targetProjectId = targetProjectId
  return streamRequest<ChatStreamData>(
    `/internal-api/v1/chat-sessions/${sessionId}/messages/stream`,
    {
      method: 'POST',
      body: JSON.stringify(body),
      signal,
    },
    onEvent,
  )
}

export async function getChatSession(sessionId: string): Promise<ChatSession> {
  return request<ChatSession>(`/internal-api/v1/chat-sessions/${sessionId}`, { method: 'GET' })
}

export async function listChatSessions(search = '', limit = 20, offset = 0): Promise<ChatSessionPage> {
  const params = new URLSearchParams({ limit: String(limit), offset: String(offset) })
  if (search.trim()) params.set('search', search.trim())
  return request<ChatSessionPage>(`/internal-api/v1/chat-sessions?${params.toString()}`, { method: 'GET' })
}

export async function startNewChatSession(): Promise<ChatSession> {
  return request<ChatSession>('/internal-api/v1/chat-sessions', { method: 'POST' })
}

export async function listChatSessionContext(sessionId: string): Promise<ChatSessionContext[]> {
  return request<ChatSessionContext[]>(`/internal-api/v1/chat-sessions/${sessionId}/context`, { method: 'GET' })
}

export async function addChatSessionContext(sessionId: string, entityType: ChatContextEntityType, entityId: string): Promise<ChatSessionContext> {
  return request<ChatSessionContext>(`/internal-api/v1/chat-sessions/${sessionId}/context`, {
    method: 'POST',
    body: JSON.stringify({ entityType, entityId }),
  })
}

export async function deleteChatSessionContext(sessionId: string, contextId: string): Promise<void> {
  await request<void>(`/internal-api/v1/chat-sessions/${sessionId}/context/${contextId}`, { method: 'DELETE' })
}

export async function renameChatSession(sessionId: string, title: string): Promise<void> {
  await request<void>(`/internal-api/v1/chat-sessions/${sessionId}/title`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  })
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await request<void>(`/internal-api/v1/chat-sessions/${sessionId}`, {
    method: 'DELETE',
  })
}

export async function createProject(project: CreateProjectRequest): Promise<Project> {
  const created = await request<{
    id: string
    name?: string | null
    title?: string | null
    createdByUserId?: string | null
    createdAt?: string
    updatedAt?: string
  }>('/internal-api/v1/projects', {
    method: 'POST',
    body: JSON.stringify({
      name: project.title ?? '',
      ownerUserIds: project.ownerUserIds,
      ownerTeamIds: project.ownerTeamIds,
    }),
  })

  return normalizeProject(created)
}

export async function updateProject(
  id: string,
  project: { title?: string | null; description?: string | null },
): Promise<Project> {
  const updated = await request<{
    id: string
    name?: string | null
    title?: string | null
    createdByUserId?: string | null
    createdAt?: string
    updatedAt?: string
  }>(`/internal-api/v1/projects/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      name: project.title ?? '',
    }),
  })

  return normalizeProject(updated)
}

function legacyNode(view: WorkItemView, all: WorkItemView[]): ProjectNode {
  const item = view.workItem
  const fields: ProjectNodeField[] = [
    { name: 'status', label: 'Status', dataType: 'text', value: item.status.replaceAll('_', ' '), visibleInTree: true },
    { name: 'dueDate', label: 'Due date', dataType: 'date', value: item.dueDate, visibleInTree: true },
    { name: 'priority', label: 'Priority', dataType: 'text', value: item.priority, visibleInTree: true },
    { name: 'assigneeUserIds', label: 'Assigned users', dataType: 'user', value: view.assignees.filter((assignee) => assignee.assigneeType === 'USER').map((assignee) => assignee.assigneeId), visibleInTree: false },
    { name: 'assigneeTeamIds', label: 'Assigned teams', dataType: 'team', value: view.assignees.filter((assignee) => assignee.assigneeType === 'TEAM').map((assignee) => assignee.assigneeId), visibleInTree: false },
  ]
  return { id: item.id, projectId: item.projectId, parentNodeId: item.parentWorkItemId, sortIndex: item.sortIndex, type: item.type, title: item.title, fields, childrenCount: all.filter((candidate) => candidate.workItem.parentWorkItemId === item.id).length, createdAt: item.createdAt, updatedAt: item.updatedAt }
}

function treeNode(view: WorkItemView): ProjectNode {
  return { ...legacyNode(view, []), childrenCount: null }
}

async function legacyNodes(projectId: string, query?: string) {
  const workspace = await getWorkspace(projectId, query)
  return workspace.workItems.map((view) => legacyNode(view, workspace.workItems))
}

function workItemType(type: string): WorkItem['type'] { return ['TASK', 'QUESTION', 'APPROVAL', 'REVIEW', 'DECISION'].includes(type.toUpperCase()) ? type.toUpperCase() as WorkItem['type'] : 'TASK' }

function fieldValue(fields: ProjectNodeField[], name: string) { return fields.find((field) => field.name === name)?.value }

function optionalTextField(fields: ProjectNodeField[], name: string) {
  const value = fieldValue(fields, name)
  const text = typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''
  return text || null
}

function parseStringArrayField(value: unknown): string[] {
  if (Array.isArray(value)) {
    return [...new Set(value.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean))]
  }
  if (typeof value !== 'string' || !value.trim()) {
    return []
  }
  try {
    return parseStringArrayField(JSON.parse(value))
  } catch {
    return [value.trim()]
  }
}

export async function listNodes(projectId: string, query?: string): Promise<ProjectNode[]> {
  return legacyNodes(projectId, query)
}

export async function listTreeNodes(
  projectId: string,
  options?: { parentNodeId?: string | null; page?: number | null; size?: number | null },
): Promise<NodePageResponse> {
  const parentNodeId = options?.parentNodeId ?? null
  const page = options?.page ?? 0
  const size = options?.size ?? 50
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (parentNodeId) {
    params.set('parentWorkItemId', parentNodeId)
  }
  const envelope = await requestEnvelope<WorkItemView[]>(`/internal-api/v1/projects/${projectId}/work-items/tree?${params.toString()}`, { method: 'GET' })
  const items = (envelope.data ?? []).map(treeNode)
  return {
    items,
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? items.length,
    totalPages: envelope.meta?.totalPages ?? (items.length > 0 ? 1 : 0),
  }
}

export async function listWorkItemSubtree(
  projectId: string,
  rootWorkItemId: string,
  options?: { maxDepth?: number; maxItems?: number },
): Promise<NodeSubtreeResponse> {
  const params = new URLSearchParams({
    rootWorkItemId,
    maxDepth: String(options?.maxDepth ?? 20),
    maxItems: String(options?.maxItems ?? 1000),
  })
  const response = await request<{ items: WorkItemView[]; truncated: boolean }>(`/internal-api/v1/projects/${projectId}/work-items/tree/subtree?${params.toString()}`, { method: 'GET' })
  return {
    items: response.items.map(treeNode),
    truncated: response.truncated,
  }
}

export async function getNode(projectId: string, nodeId: string): Promise<ProjectNode> {
  const node = (await legacyNodes(projectId)).find((item) => item.id === nodeId)
  if (!node) throw new Error('WorkItem not found')
  return node
}

export async function createNode(projectId: string, node: Omit<ProjectNode, 'id'> & { id?: string | null }): Promise<ProjectNode> {
  const status = String(fieldValue(node.fields, 'status') ?? 'OPEN').trim().toUpperCase().replaceAll(' ', '_') as WorkItem['status']
  const created = await createWorkItem(projectId, { title: node.title, type: workItemType(node.type), status, parentWorkItemId: node.parentNodeId ?? null, sortIndex: node.sortIndex ?? undefined })
  const workspace = await getWorkspace(projectId)
  return legacyNode(created, workspace.workItems)
}

export async function updateNode(projectId: string, id: string, node: Omit<ProjectNode, 'id'>): Promise<ProjectNode> {
  const workspace = await getWorkspace(projectId)
  const current = workspace.workItems.find((view) => view.workItem.id === id)
  if (!current) throw new Error('WorkItem not found')
  const status = String(fieldValue(node.fields, 'status') ?? current.workItem.status).trim().toUpperCase().replaceAll(' ', '_') as WorkItem['status']
  const assignees: WorkItemAssignee[] = [
    ...parseStringArrayField(fieldValue(node.fields, 'assigneeUserIds')).map((assigneeId) => ({ assigneeType: 'USER' as const, assigneeId })),
    ...parseStringArrayField(fieldValue(node.fields, 'assigneeTeamIds')).map((assigneeId) => ({ assigneeType: 'TEAM' as const, assigneeId })),
  ]
  const updated = await updateWorkItem(projectId, id, {
    ...current.workItem,
    title: node.title,
    type: workItemType(node.type),
    status,
    dueDate: optionalTextField(node.fields, 'dueDate'),
    priority: optionalTextField(node.fields, 'priority'),
    parentWorkItemId: node.parentNodeId ?? null,
    sortIndex: node.sortIndex ?? current.workItem.sortIndex,
  }, assignees)
  return legacyNode(updated, workspace.workItems.map((view) => view.workItem.id === id ? updated : view))
}

export async function deleteNode(projectId: string, id: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/work-items/${id}`, { method: 'DELETE' })
}


export async function moveWorkItemInContentOrder(
  projectId: string,
  nodeId: string,
  parentWorkItemId: string | null,
  before?: Pick<ContentOrderItem, 'entityType' | 'entityId'> | null,
): Promise<ProjectNode> {
  const updated = await request<WorkItemView>(`/internal-api/v1/projects/${projectId}/work-items/${nodeId}/move`, {
    method: 'PUT',
    body: JSON.stringify({ parentWorkItemId, beforeEntityType: before?.entityType ?? null, beforeEntityId: before?.entityId ?? null }),
  })
  const workspace = await getWorkspace(projectId)
  return legacyNode(updated, workspace.workItems.map((view) => view.workItem.id === nodeId ? updated : view))
}

export async function listSubscriptions(page = 0, size = 50): Promise<SubscribedWorkItemPageResponse> {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  const envelope = await requestEnvelope<SubscribedWorkItem[]>(`/internal-api/v1/subscriptions?${params.toString()}`, { method: 'GET' })
  return {
    items: envelope.data ?? [],
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? envelope.data?.length ?? 0,
    totalPages: envelope.meta?.totalPages ?? 0,
  }
}

export async function getSubscriptionStatus(projectId: string, workItemId: string): Promise<SubscriptionStatus> {
  return request<SubscriptionStatus>(`/internal-api/v1/projects/${projectId}/work-items/${workItemId}/subscription`, { method: 'GET' })
}

export interface AssignedWorkItem {
  projectId: string
  projectName: string
  workItemId: string
  title: string
  type: string
  status: string
  dueDate: string | null
  priority: string | null
  updatedAt: string
}

export async function listAssignedToMe(page = 0, size = 50): Promise<{ items: AssignedWorkItem[]; page: number; size: number; totalItems: number; totalPages: number }> {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  const envelope = await requestEnvelope<AssignedWorkItem[]>(`/internal-api/v1/assigned-to-me?${params.toString()}`, { method: 'GET' })
  return {
    items: envelope.data ?? [],
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? envelope.data?.length ?? 0,
    totalPages: envelope.meta?.totalPages ?? 0,
  }
}

export async function subscribeWorkItem(projectId: string, workItemId: string): Promise<SubscriptionStatus> {
  return request<SubscriptionStatus>(`/internal-api/v1/projects/${projectId}/work-items/${workItemId}/subscription`, { method: 'POST' })
}

export async function unsubscribeWorkItem(projectId: string, workItemId: string): Promise<SubscriptionStatus> {
  return request<SubscriptionStatus>(`/internal-api/v1/projects/${projectId}/work-items/${workItemId}/subscription`, { method: 'DELETE' })
}

export async function listNodeEdges(projectId: string): Promise<ProjectNodeEdge[]> {
  const workspace = await getWorkspace(projectId)
  return workspace.relationships
    .filter((relationship) => relationship.fromEntityType === 'WORK_ITEM' && relationship.toEntityType === 'WORK_ITEM')
    .map((relationship) => ({
      id: relationship.id,
      projectId: relationship.projectId,
      fromNodeId: relationship.fromEntityId,
      toNodeId: relationship.toEntityId,
      relationType: relationship.type,
    }))
}

export async function listGraphChangeProposals(projectId: string): Promise<GraphChangeProposal[]> {
  type StoredChange = Omit<GraphChangeProposalChange, 'entityType' | 'node' | 'previousNode' | 'edge'> & {
    entityType: 'WORK_ITEM' | 'ENTRY' | 'RELATIONSHIP'
    workItem?: WorkItemView | null
    previousWorkItem?: WorkItemView | null
    entry?: Entry | null
    previousEntry?: Entry | null
    relationship?: Relationship | null
    previousRelationship?: Relationship | null
  }
  type StoredProposal = Omit<GraphChangeProposal, 'changes'> & { changes: StoredChange[] }
  const proposals = await request<StoredProposal[]>(`/internal-api/v1/projects/${projectId}/graph-change-proposals`, { method: 'GET' })
  const proposalWorkItems = proposals.flatMap((proposal) => proposal.changes.flatMap((change) => [change.workItem, change.previousWorkItem].filter((value): value is WorkItemView => Boolean(value))))

  return proposals.map((proposal) => ({
    ...proposal,
    changes: proposal.changes.map((change): GraphChangeProposalChange => {
      if (change.entityType === 'WORK_ITEM') {
        return {
          ...change,
          entityType: 'NODE',
          node: change.workItem ? legacyNode(change.workItem, proposalWorkItems) : null,
          previousNode: change.previousWorkItem ? legacyNode(change.previousWorkItem, proposalWorkItems) : null,
        }
      }
      if (change.entityType === 'RELATIONSHIP') {
        const relationship = change.relationship
        return {
          ...change,
          entityType: 'EDGE',
          edge: relationship && relationship.fromEntityType === 'WORK_ITEM' && relationship.toEntityType === 'WORK_ITEM'
            ? { id: relationship.id, projectId: relationship.projectId, fromNodeId: relationship.fromEntityId, toNodeId: relationship.toEntityId, relationType: relationship.type }
            : null,
        }
      }
      return { ...change, entityType: 'ENTRY' }
    }),
  }))
}

export async function decideGraphChangeProposal(
  projectId: string,
  proposalId: string,
  changeId: string,
  entityType: 'NODE' | 'ENTRY' | 'EDGE',
  decision: GraphProposalDecision,
  feedback?: string | null,
): Promise<GraphChangeProposal> {
  return request<GraphChangeProposal>(
    `/internal-api/v1/projects/${projectId}/graph-change-proposals/${proposalId}/changes/${changeId}/decision`,
    {
      method: 'POST',
      body: JSON.stringify({ entityType, decision, feedback: feedback ?? null }),
    },
  )
}



export async function listUsers(page = 0, size = 100): Promise<UserPageResponse> {
  const envelope = await requestEnvelope<User[]>(`/internal-api/v1/users?page=${page}&size=${size}`, { method: 'GET' })
  return {
    items: envelope.data ?? [],
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? envelope.data?.length ?? 0,
    totalPages: envelope.meta?.totalPages ?? 0,
  }
}

export async function loadSelectableUsers(): Promise<User[]> {
  const pageSize = 100
  const firstPage = await listUsers(0, pageSize)
  const pages = [firstPage]

  for (let page = 1; page < firstPage.totalPages; page++) {
    pages.push(await listUsers(page, pageSize))
  }

  return pages
    .flatMap((page) => page.items)
    .filter((user) => user.globalRole?.toUpperCase() !== 'SUPERADMIN')
}

export async function listAuditLogs(page = 0, size = 20): Promise<AuditLogPageResponse> {
  const envelope = await requestEnvelope<AuditLog[]>(`/internal-api/v1/audit-logs?page=${page}&size=${size}`, { method: 'GET' })
  return {
    items: envelope.data ?? [],
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? envelope.data?.length ?? 0,
    totalPages: envelope.meta?.totalPages ?? 0,
  }
}


export async function listWorkItemAuditLogs(projectId: string, workItemId: string, page = 0, size = 20): Promise<AuditLogPageResponse> {
  const envelope = await requestEnvelope<AuditLog[]>(`/internal-api/v1/projects/${projectId}/work-items/${workItemId}/audit-logs?page=${page}&size=${size}`, { method: 'GET' })
  return {
    items: envelope.data ?? [],
    page: envelope.meta?.page ?? page,
    size: envelope.meta?.size ?? size,
    totalItems: envelope.meta?.totalItems ?? envelope.data?.length ?? 0,
    totalPages: envelope.meta?.totalPages ?? 0,
  }
}



export async function listTeams(): Promise<Team[]> {
  return request<Team[]>('/internal-api/v1/teams', { method: 'GET' })
}

export async function createTeam(team: CreateTeamRequest): Promise<Team> {
  return request<Team>('/internal-api/v1/teams', {
    method: 'POST',
    body: JSON.stringify(team),
  })
}

export async function updateTeam(id: string, team: Omit<Team, 'id'>): Promise<Team> {
  return request<Team>(`/internal-api/v1/teams/${id}`, {
    method: 'PUT',
    body: JSON.stringify(team),
  })
}

export async function deleteTeam(id: string): Promise<void> {
  return request<void>(`/internal-api/v1/teams/${id}`, {
    method: 'DELETE',
  })
}

export async function listTeamMembers(teamId: string): Promise<TeamMember[]> {
  return request<TeamMember[]>(`/internal-api/v1/teams/${teamId}/members`, { method: 'GET' })
}


export async function upsertTeamMember(teamId: string, userId: string, role: TeamMember['role']): Promise<TeamMember> {
  return request<TeamMember>(`/internal-api/v1/teams/${teamId}/members`, {
    method: 'POST',
    body: JSON.stringify({ userId, role }),
  })
}

export async function removeTeamMember(teamId: string, userId: string): Promise<void> {
  return request<void>(`/internal-api/v1/teams/${teamId}/members/${userId}`, {
    method: 'DELETE',
  })
}

export async function listTeamProjects(teamId: string): Promise<ProjectTeam[]> {
  return request<ProjectTeam[]>(`/internal-api/v1/teams/${teamId}/projects`, { method: 'GET' })
}

export async function addTeamProject(teamId: string, projectId: string, role: ProjectTeam['role'] = 'VIEWER'): Promise<ProjectTeam> {
  return request<ProjectTeam>(`/internal-api/v1/teams/${teamId}/projects`, {
    method: 'POST',
    body: JSON.stringify({ projectId, role }),
  })
}

export async function removeTeamProject(teamId: string, projectId: string): Promise<void> {
  return request<void>(`/internal-api/v1/teams/${teamId}/projects/${projectId}`, {
    method: 'DELETE',
  })
}

export async function listProjectTeams(projectId: string): Promise<ProjectTeam[]> {
  return request<ProjectTeam[]>(`/internal-api/v1/projects/${projectId}/teams`, { method: 'GET' })
}

export async function listProjectMembers(projectId: string): Promise<ProjectMember[]> {
  return request<ProjectMember[]>(`/internal-api/v1/projects/${projectId}/members`, { method: 'GET' })
}

export async function upsertProjectMember(projectId: string, userId: string, role: ProjectMember['role']): Promise<ProjectMember> {
  return request<ProjectMember>(`/internal-api/v1/projects/${projectId}/members`, {
    method: 'POST',
    body: JSON.stringify({ userId, role }),
  })
}

export async function removeProjectMember(projectId: string, userId: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/members/${userId}`, { method: 'DELETE' })
}

export async function assignProjectTeam(projectId: string, teamId: string, role: ProjectTeam['role']): Promise<ProjectTeam> {
  return request<ProjectTeam>(`/internal-api/v1/projects/${projectId}/teams`, {
    method: 'POST',
    body: JSON.stringify({ teamId, role }),
  })
}

export async function unassignProjectTeam(projectId: string, teamId: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/teams/${teamId}`, {
    method: 'DELETE',
  })
}

export async function listTeamJoinRequests(teamId: string): Promise<TeamJoinRequest[]> {
  return request<TeamJoinRequest[]>(`/internal-api/v1/teams/${teamId}/join-requests`, { method: 'GET' })
}

export async function requestTeamJoin(teamId: string): Promise<TeamJoinRequest> {
  return request<TeamJoinRequest>(`/internal-api/v1/teams/${teamId}/join-requests`, { method: 'POST' })
}

export async function decideTeamJoinRequest(teamId: string, requestId: string, decision: 'APPROVE' | 'REJECT'): Promise<TeamJoinRequest> {
  return request<TeamJoinRequest>(`/internal-api/v1/teams/${teamId}/join-requests/${requestId}/decision`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  })
}

export async function login(loginValue: string, password: string) {
  return request<AuthUser>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      login: loginValue,
      password,
    }),
  })
}

export async function logout() {
  return request<void>('/api/v1/auth/logout', {
    method: 'POST',
  })
}

export async function updatePassword(newPassword: string, currentPassword?: string) {
  return request<AuthUser>('/api/v1/auth/password', {
    method: 'POST',
    body: JSON.stringify(currentPassword ? { newPassword, currentPassword } : { newPassword }),
  })
}

export async function getWorkspace(projectId: string, query?: string): Promise<Workspace> {
  const params = new URLSearchParams()
  if (query?.trim()) params.set('q', query.trim())
  const suffix = params.size > 0 ? `?${params.toString()}` : ''
  return request<Workspace>(`/internal-api/v1/projects/${projectId}/workspace${suffix}`, { method: 'GET' })
}

export async function createWorkItem(projectId: string, workItem: Partial<WorkItem>, assignees: WorkItemAssignee[] = []): Promise<WorkItemView> {
  return request<WorkItemView>(`/internal-api/v1/projects/${projectId}/work-items`, { method: 'POST', body: JSON.stringify({ workItem, assignees }) })
}

export async function updateWorkItem(projectId: string, id: string, workItem: WorkItem, assignees: WorkItemAssignee[]): Promise<WorkItemView> {
  return request<WorkItemView>(`/internal-api/v1/projects/${projectId}/work-items/${id}`, { method: 'PUT', body: JSON.stringify({ workItem, assignees }) })
}

export async function createEntry(projectId: string, entry: Pick<Entry, 'workItemId' | 'type' | 'body'>): Promise<Entry> {
  return request<Entry>(`/internal-api/v1/projects/${projectId}/entries`, { method: 'POST', body: JSON.stringify(entry) })
}

export async function createRelationship(
  projectId: string,
  relationship: Pick<Relationship, 'fromEntityType' | 'fromEntityId' | 'toEntityType' | 'toEntityId' | 'type' | 'reason' | 'sourceEntryId'>,
): Promise<Relationship> {
  return request<Relationship>(`/internal-api/v1/projects/${projectId}/relationships`, {
    method: 'POST',
    body: JSON.stringify(relationship),
  })
}

export async function deleteRelationship(projectId: string, relationshipId: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/relationships/${relationshipId}`, { method: 'DELETE' })
}

export async function updateRelationshipReason(projectId: string, relationshipId: string, reason: string | null): Promise<Relationship> {
  return request<Relationship>(`/internal-api/v1/projects/${projectId}/relationships/${relationshipId}/reason`, {
    method: 'PUT',
    body: JSON.stringify({ reason }),
  })
}

export async function reorderContentItems(
  projectId: string,
  parentWorkItemId: string | null,
  items: Pick<ContentOrderItem, 'entityType' | 'entityId'>[],
): Promise<ContentOrderItem[]> {
  return request<ContentOrderItem[]>(`/internal-api/v1/projects/${projectId}/content-order`, {
    method: 'PUT',
    body: JSON.stringify({ parentWorkItemId, items }),
  })
}

export async function updateEntry(projectId: string, id: string, entry: Pick<Entry, 'workItemId' | 'type' | 'body'>): Promise<Entry> {
  return request<Entry>(`/internal-api/v1/projects/${projectId}/entries/${id}`, { method: 'PUT', body: JSON.stringify(entry) })
}

export type EntryAiReview = {
  originalBody: string
  proposedBody: string
  proposedType: string
  rationale?: string | null
  entryType?: string
}

export type WorkItemAiReview = {
  originalTitle: string
  proposedTitle: string
  proposedType: WorkItem['type']
  proposedStatus: WorkItem['status']
  proposedDueDate: string | null
  proposedPriority: string | null
  proposedAssignees: WorkItemAssignee[]
  proposedBlockers: { workItemId: string; reason: string | null }[]
  rationale?: string | null
}

export async function reviewWorkItemWithAi(projectId: string, id: string, review: Pick<WorkItem, 'title' | 'type' | 'status' | 'dueDate' | 'priority'> & { assignees: WorkItemAssignee[]; instruction?: string }): Promise<WorkItemAiReview> {
  return request<WorkItemAiReview>(`/internal-api/v1/projects/${projectId}/work-items/${id}/ai-review`, { method: 'POST', body: JSON.stringify(review) })
}

export async function reviewEntryWithAi(projectId: string, id: string, body: string, type: string, instruction?: string): Promise<EntryAiReview> {
  return request<EntryAiReview>(`/internal-api/v1/projects/${projectId}/entries/${id}/ai-review`, { method: 'POST', body: JSON.stringify({ body, type, instruction }) })
}

export async function reviewNewEntryWithAi(projectId: string, entry: Pick<Entry, 'workItemId' | 'type' | 'body'>, instruction?: string): Promise<EntryAiReview> {
  return request<EntryAiReview>(`/internal-api/v1/projects/${projectId}/entries/ai-review`, { method: 'POST', body: JSON.stringify({ ...entry, instruction }) })
}

export async function acceptEntryAiReview(projectId: string, id: string, originalBody: string, proposedBody: string, type?: string): Promise<Entry> {
  return request<Entry>(`/internal-api/v1/projects/${projectId}/entries/${id}/ai-review/accept`, { method: 'POST', body: JSON.stringify({ originalBody, proposedBody, type }) })
}

export async function rejectEntryAiReview(projectId: string, id: string, originalBody: string, proposedBody: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/entries/${id}/ai-review/reject`, { method: 'POST', body: JSON.stringify({ originalBody, proposedBody }) })
}

export async function acceptNewEntryAiReview(projectId: string, entry: Pick<Entry, 'workItemId' | 'type'>, originalBody: string, proposedBody: string): Promise<Entry> {
  return request<Entry>(`/internal-api/v1/projects/${projectId}/entries/ai-review/accept`, { method: 'POST', body: JSON.stringify({ ...entry, originalBody, proposedBody }) })
}

export async function rejectNewEntryAiReview(projectId: string, entry: Pick<Entry, 'workItemId' | 'type'>, originalBody: string, proposedBody: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/entries/ai-review/reject`, { method: 'POST', body: JSON.stringify({ ...entry, originalBody, proposedBody }) })
}

export async function deleteEntry(projectId: string, id: string): Promise<void> {
  return request<void>(`/internal-api/v1/projects/${projectId}/entries/${id}`, { method: 'DELETE' })
}


export async function getNotifications(options: { unread?: boolean; limit?: number; offset?: number } = {}): Promise<UserNotificationPage> {
  const params = new URLSearchParams()
  if (options.unread !== undefined) params.set('unread', String(options.unread))
  if (options.limit !== undefined) params.set('limit', String(options.limit))
  if (options.offset !== undefined) params.set('offset', String(options.offset))
  const query = params.toString()
  return request<UserNotificationPage>(`/internal-api/v1/notifications${query ? `?${query}` : ''}`, { method: 'GET' })
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  await request<void>(`/internal-api/v1/notifications/${notificationId}/read`, { method: 'PATCH' })
}

export async function markAllNotificationsRead(): Promise<void> {
  await request<void>('/internal-api/v1/notifications/read-all', { method: 'POST' })
}
