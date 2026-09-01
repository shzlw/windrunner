import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, ReactElement, ReactNode } from 'react'
import { NavLink, Navigate, useLocation, useOutletContext, useParams, useSearchParams } from 'react-router'
import { ArrowDown, ArrowUp, Bookmark, BookmarkCheck, Bot, Check, ChevronDown, ChevronRight, CircleAlert, CircleHelp, CircleSmall, ClipboardCheck, FileText, Filter, Focus, FolderOpen, History, ListTodo, Loader2, MessageSquarePlus, MessageSquareText, MoreHorizontal, MoveRight, OctagonAlert, PanelRightClose, PanelRightOpen, Pencil, Plus, Save, Search, Settings, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'
import { usePanelRef } from 'react-resizable-panels'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { Textarea } from '@/components/ui/textarea'
import {
  createNode,
  createEntry,
  createRelationship,
  deleteRelationship,
  acceptEntryAiReview,
  acceptNewEntryAiReview,
  decideGraphChangeProposal,
  deleteEntry,
  deleteNode,
  getNode,
  getProject,
  getWorkspace,
  getLlmStatus,
  listProjectMembers,
  listProjectTeams,
  listTeamMembers,
  listGraphChangeProposals,
  listNodes,
  listTreeNodes,
  listWorkItemSubtree,
  listTeams,
  loadSelectableUsers,
  type Entry,
  type EntryAiReview,
  type WorkItemAiReview,
  type GraphChangeProposal,
  type GraphChangeProposalChange,
  type NodePageResponse,
  moveWorkItemInContentOrder,
  type Project,
  type WorkItem,
  type ProjectNode,
  type ProjectNodeField,
  type ProjectNodeFieldDataType,
  type ContentOrderItem,
  type Relationship,
  type Team,
  type User,
  type AuthUser,
  updateNode,
  updateEntry,
  updateRelationshipReason,
  rejectEntryAiReview,
  rejectNewEntryAiReview,
  reorderContentItems,
  reviewEntryWithAi,
  reviewWorkItemWithAi,
  reviewNewEntryWithAi,
  getSubscriptionStatus,
  subscribeWorkItem,
  unsubscribeWorkItem,
} from '@/lib/api'
import { cn } from '@/lib/utils'
import { entryTypeBadgeClass, workItemTypeBadgeClass } from '@/lib/typeBadges'
import { translateEntryType, translatePriority, translateProposalAction, translateRelationshipType, translateStatus, translateWorkItemType } from '@/i18n/labels'
import WorkItemHistoryPanel from '@/WorkItemHistoryPanel'

type TreeNode = ProjectNode & {
  children: TreeNode[]
  proposal?: TreeNodeProposal
}

type TreeNodeProposal = {
  proposalId: string
  changeId: string
  action: 'ADD' | 'UPDATE' | 'DELETE'
  status: string
  summary: string
  targetId: string
  previousNode?: ProjectNode | null
  proposedNode?: ProjectNode | null
}

type NodeFormField = {
  clientId: string
  name: string
  label: string
  dataType: ProjectNodeFieldDataType
  value: string | number | boolean | string[] | null
  visibleInTree: boolean
}

type NodeFormState = {
  type: string
  title: string
  fields: NodeFormField[]
}

type FlatTreeNode = TreeNode & {
  depth: number
}

type InspectorMode = 'task' | 'history'
type CreatedSortDirection = 'ASC' | 'DESC' | null
type WorkItemFilterField = 'STATUS' | 'PRIORITY' | 'DUE_DATE' | 'ASSIGNEE'

type ProjectWorkspaceOutletContext = {
  artifactRefreshKey: number
}
type WorkItemFilterOperator = 'AND' | 'OR'

type WorkItemFilterCondition = {
  id: string
  field: WorkItemFilterField
  value: string
  operator: WorkItemFilterOperator
}

type NodePageInfo = Pick<NodePageResponse, 'page' | 'size' | 'totalItems' | 'totalPages'>

type ProposalFieldDiff = {
  name: string
  label: string
  status: 'added' | 'removed' | 'changed' | 'unchanged'
  previousValue: string
  nextValue: string
}

type PendingProposalNode = ProjectNode & {
  children: TreeNode[]
  proposal: TreeNodeProposal
  placementReason: string
}

type OrderedWorkItemContent =
  | { entityType: 'WORK_ITEM'; entityId: string; child: TreeNode }
  | { entityType: 'ENTRY'; entityId: string; entry: Entry }

const relationType = 'contains'
const workItemRelationshipTypes = ['BLOCKED_BY', 'DEPENDS_ON', 'RELATED_TO', 'ANSWERS', 'SUPPORTS', 'CONTRADICTS', 'RESOLVES', 'SUPERSEDES'] as const

function relationshipTypeBadgeClass(type: string) {
  switch (type) {
    case 'BLOCKED_BY': return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-300'
    case 'DEPENDS_ON': return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-300'
    case 'RELATED_TO': return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300'
    case 'ANSWERS': return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900 dark:bg-blue-950/50 dark:text-blue-300'
    case 'SUPPORTS': return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-300'
    case 'CONTRADICTS': return 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900 dark:bg-rose-950/50 dark:text-rose-300'
    case 'RESOLVES': return 'border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-900 dark:bg-violet-950/50 dark:text-violet-300'
    case 'SUPERSEDES': return 'border-orange-200 bg-orange-50 text-orange-700 dark:border-orange-900 dark:bg-orange-950/50 dark:text-orange-300'
    default: return 'border-border bg-muted text-muted-foreground'
  }
}

const treePageSize = 50
const treeSubtreeMaxDepth = 20
const treeSubtreeMaxItems = 1000
const treeDepthIndentPx = 18
const workspacePanelLayoutStorageKey = 'windrunner.project-workspace.panel-layout'
const workspaceInspectorCollapsedStorageKey = 'windrunner.project-workspace.inspector-collapsed'
const defaultWorkspacePanelLayout = { 'project-tree': 70, 'project-inspector': 30 }

function readWorkspacePanelLayout() {
  if (typeof window === 'undefined') {
    return defaultWorkspacePanelLayout
  }
  try {
    const storedLayout = JSON.parse(window.localStorage.getItem(workspacePanelLayoutStorageKey) ?? '') as Record<string, unknown>
    const treeSize = Number(storedLayout['project-tree'])
    const inspectorSize = Number(storedLayout['project-inspector'])
    if (Number.isFinite(treeSize) && Number.isFinite(inspectorSize) && treeSize >= 45 && treeSize <= 76 && inspectorSize >= 24 && inspectorSize <= 55 && Math.abs(treeSize + inspectorSize - 100) < 0.1) {
      return { 'project-tree': treeSize, 'project-inspector': inspectorSize }
    }
  } catch {
    // Ignore missing or malformed persisted layouts.
  }
  return defaultWorkspacePanelLayout
}

function readWorkspaceInspectorCollapsed() {
  if (typeof window === 'undefined') {
    return false
  }
  try {
    return window.localStorage.getItem(workspaceInspectorCollapsedStorageKey) === 'true'
  } catch {
    // Ignore unavailable browser storage.
    return false
  }
}

const workItemTypeOptions = ['TASK', 'QUESTION', 'APPROVAL', 'REVIEW', 'DECISION'] as const
const entryTypeOptions = ['COMMENT', 'INFORMATION', 'ANSWER', 'EVIDENCE', 'PROPOSAL', 'RESOLUTION'] as const
const workItemStatusOptions: Record<string, { value: string; label: string }[]> = {
  TASK: [
    { value: 'OPEN', label: 'Open' },
    { value: 'IN PROGRESS', label: 'In progress' },
    { value: 'BLOCKED', label: 'Blocked' },
    { value: 'DONE', label: 'Done' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
  QUESTION: [
    { value: 'OPEN', label: 'Open' },
    { value: 'WAITING', label: 'Waiting' },
    { value: 'ANSWERED', label: 'Answered' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
  APPROVAL: [
    { value: 'PENDING', label: 'Pending' },
    { value: 'APPROVED', label: 'Approved' },
    { value: 'REJECTED', label: 'Rejected' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
  REVIEW: [
    { value: 'OPEN', label: 'Open' },
    { value: 'IN PROGRESS', label: 'In progress' },
    { value: 'BLOCKED', label: 'Blocked' },
    { value: 'DONE', label: 'Done' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
  DECISION: [
    { value: 'OPEN', label: 'Open' },
    { value: 'IN PROGRESS', label: 'In progress' },
    { value: 'DONE', label: 'Decided' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
}
const newNodeDefaults: NodeFormState = {
  type: 'TASK',
  title: '',
  fields: [],
}

function defaultStatusForType(type: string) {
  return type.trim().toUpperCase() === 'APPROVAL' ? 'PENDING' : 'OPEN'
}

function statusOptionsForType(type: string, t: TFunction) {
  const normalizedType = type.trim().toUpperCase()
  const options = workItemStatusOptions[normalizedType] ?? workItemStatusOptions.TASK
  const statusKeys: Record<string, string> = {
    OPEN: 'status.open',
    'IN PROGRESS': 'status.inProgress',
    BLOCKED: 'status.blocked',
    DONE: normalizedType === 'DECISION' ? 'status.decided' : 'status.done',
    CANCELLED: 'status.cancelled',
    WAITING: 'status.waiting',
    ANSWERED: 'status.answered',
    PENDING: 'status.pending',
    APPROVED: 'status.approved',
    REJECTED: 'status.rejected',
  }
  return options.map((option) => ({ ...option, label: t(statusKeys[option.value] ?? option.label) }))
}

function normalizedStatus(value: unknown) {
  return String(value ?? '').trim().toUpperCase().replaceAll('_', ' ')
}

function contentParentKey(parentWorkItemId: string | null | undefined) {
  return parentWorkItemId ?? '__PROJECT_ROOT__'
}

function contentEntityKey(entityType: 'WORK_ITEM' | 'ENTRY', entityId: string) {
  return `${entityType}:${entityId}`
}

function groupContentOrderByParent(nodes: ProjectNode[], entries: Entry[]) {
  const grouped = new Map<string, ContentOrderItem[]>()
  nodes.forEach((node) => {
    const key = contentParentKey(node.parentNodeId)
    grouped.set(key, [...(grouped.get(key) ?? []), { entityType: 'WORK_ITEM', entityId: node.id, sortIndex: node.sortIndex ?? 0 }])
  })
  entries.forEach((entry) => {
    const key = contentParentKey(entry.workItemId)
    grouped.set(key, [...(grouped.get(key) ?? []), { entityType: 'ENTRY', entityId: entry.id, sortIndex: entry.sortIndex }])
  })
  grouped.forEach((siblings, key) => grouped.set(key, siblings.sort((left, right) => left.sortIndex - right.sortIndex || contentEntityKey(left.entityType, left.entityId).localeCompare(contentEntityKey(right.entityType, right.entityId)))))
  return grouped
}

function sortTreeByContentOrder(nodes: TreeNode[], itemsByParent: Map<string, ContentOrderItem[]>) {
  function sortSiblings(siblings: TreeNode[], parentId: string | null): TreeNode[] {
    const positions = new Map(
      (itemsByParent.get(contentParentKey(parentId)) ?? [])
        .filter((item) => item.entityType === 'WORK_ITEM')
        .map((item) => [item.entityId, item.sortIndex]),
    )
    return [...siblings]
      .sort((left, right) => (positions.get(left.id) ?? Number.MAX_SAFE_INTEGER) - (positions.get(right.id) ?? Number.MAX_SAFE_INTEGER) || left.id.localeCompare(right.id))
      .map((node) => ({ ...node, children: sortSiblings(node.children, node.id) }))
  }
  return sortSiblings(nodes, null)
}

function orderedWorkItemContent(node: TreeNode, entries: Entry[], orderItems: ContentOrderItem[]) {
  const contentByKey = new Map<string, OrderedWorkItemContent>()
  node.children.forEach((child) => contentByKey.set(contentEntityKey('WORK_ITEM', child.id), { entityType: 'WORK_ITEM', entityId: child.id, child }))
  entries.forEach((entry) => contentByKey.set(contentEntityKey('ENTRY', entry.id), { entityType: 'ENTRY', entityId: entry.id, entry }))
  const ordered: OrderedWorkItemContent[] = []
  orderItems.forEach((item) => {
    const content = contentByKey.get(contentEntityKey(item.entityType, item.entityId))
    if (content) {
      ordered.push(content)
      contentByKey.delete(contentEntityKey(item.entityType, item.entityId))
    }
  })
  return [...ordered, ...contentByKey.values()]
}

function createDraftId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `field-${Math.random().toString(36).slice(2, 10)}`
}

function formatProjectTitle(project: Project, fallback: string) {
  return project.title?.trim() ? project.title : fallback
}

function formatActivityDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function normalizeFieldValueForForm(
  dataType: ProjectNodeFieldDataType,
  value: ProjectNodeField['value'],
): string | number | boolean | string[] | null {
  switch (dataType) {
    case 'boolean':
      return Boolean(value)
    case 'user':
    case 'team':
      return parseStringArrayValue(value)
    case 'number':
      return value ?? ''
    case 'date':
    case 'text':
    default:
      return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
  }
}

function formatProposalValue(value: unknown, t: TFunction) {
  if (value === null || value === undefined || value === '') {
    return t('workspace.empty')
  }

  if (typeof value === 'boolean') {
    return value ? t('common.yes') : t('common.no')
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? t('workspace.empty') : t('common.selected', { count: value.length })
  }

  return String(value)
}

function parseStringArrayValue(value: unknown): string[] {
  if (Array.isArray(value)) {
    return [...new Set(value.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean))]
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) {
      return []
    }
    try {
      const parsed = JSON.parse(trimmed) as unknown
      if (Array.isArray(parsed)) {
        return parseStringArrayValue(parsed)
      }
    } catch {
      return [trimmed]
    }
    return []
  }
  return []
}

function toFormField(field: ProjectNodeField): NodeFormField {
  return {
    clientId: createDraftId(),
    name: field.name,
    label: field.label,
    dataType: field.dataType,
    value: normalizeFieldValueForForm(field.dataType, field.value),
    visibleInTree: Boolean(field.visibleInTree),
  }
}

function createFormState(node: ProjectNode | null | undefined): NodeFormState {
  if (!node) {
    return {
      type: newNodeDefaults.type,
      title: newNodeDefaults.title,
      fields: [],
    }
  }

  return {
    type: node.type,
    title: node.title,
    fields: [...(node.fields ?? [])]
      .sort((left, right) => (left.order ?? 0) - (right.order ?? 0))
      .map(toFormField),
  }
}

function formFieldValue(fields: NodeFormField[], name: string) {
  return fields.find((field) => field.name === name)?.value
}

function nodeFieldValue(node: ProjectNode, name: string) {
  return node.fields?.find((field) => field.name === name)?.value
}

function withNodeFieldValue(node: ProjectNode, name: string, label: string, value: ProjectNodeField['value']) {
  const hasField = node.fields.some((field) => field.name === name)
  return {
    ...node,
    fields: hasField
      ? node.fields.map((field) => field.name === name ? { ...field, value } : field)
      : [...node.fields, { name, label, dataType: 'text' as const, value, visibleInTree: true }],
  }
}

function isDefaultWorkItemStatus(value: unknown) {
  const status = normalizedStatus(value)
  return !status || status === 'OPEN' || status === 'TODO' || status === 'TO DO'
}

function formatWorkItemDueDate(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }

  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const daysUntilDue = Math.round((date.getTime() - today.getTime()) / 86_400_000)
  return {
    label: new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date),
    title: new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date),
    isOverdue: daysUntilDue < 0,
    isDueSoon: daysUntilDue >= 0 && daysUntilDue <= 3,
  }
}

function avatarInitials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase() || '?'
}

function fieldKey(field: ProjectNodeField) {
  return field.name.trim().toLowerCase() || field.label.trim().toLowerCase()
}

function fieldValueKey(field: ProjectNodeField | undefined) {
  if (!field) {
    return ''
  }

  return JSON.stringify({
    label: field.label,
    dataType: field.dataType,
    value: field.value,
    visibleInTree: Boolean(field.visibleInTree),
  })
}

function buildProposalFieldDiffs(previousFields: ProjectNodeField[], nextFields: ProjectNodeField[], t: TFunction): ProposalFieldDiff[] {
  const previousByKey = new Map(previousFields.map((field) => [fieldKey(field), field]))
  const nextByKey = new Map(nextFields.map((field) => [fieldKey(field), field]))
  const keys = [...new Set([
    ...previousFields.map(fieldKey),
    ...nextFields.map(fieldKey),
  ])]

  return keys.map((key) => {
    const previousField = previousByKey.get(key)
    const nextField = nextByKey.get(key)
    const status: ProposalFieldDiff['status'] = !previousField
      ? 'added'
      : !nextField
        ? 'removed'
        : fieldValueKey(previousField) === fieldValueKey(nextField)
          ? 'unchanged'
          : 'changed'

    return {
      name: key,
      label: nextField?.label || previousField?.label || key,
      status,
      previousValue: previousField ? formatProposalValue(previousField.value, t) : '-',
      nextValue: nextField ? formatProposalValue(nextField.value, t) : '-',
    }
  }).filter((diff) => diff.status !== 'unchanged')
}

function serializeFields(fields: NodeFormField[]): ProjectNodeField[] {
  return fields.map((field, index) => {
    switch (field.dataType) {
      case 'boolean':
        return {
          name: field.name.trim(),
          label: field.label.trim() || field.name.trim(),
          dataType: field.dataType,
          value: Boolean(field.value),
          order: index,
          visibleInTree: field.visibleInTree,
        }
      case 'number': {
        const rawValue = typeof field.value === 'number' ? String(field.value) : String(field.value ?? '').trim()
        return {
          name: field.name.trim(),
          label: field.label.trim() || field.name.trim(),
          dataType: field.dataType,
          value: rawValue ? Number(rawValue) : null,
          order: index,
          visibleInTree: field.visibleInTree,
        }
      }
      case 'user':
      case 'team':
        return {
          name: field.name.trim(),
          label: field.label.trim() || field.name.trim(),
          dataType: field.dataType,
          value: parseStringArrayValue(field.value),
          order: index,
          visibleInTree: field.visibleInTree,
        }
      case 'date':
      case 'text':
      default:
        return {
          name: field.name.trim(),
          label: field.label.trim() || field.name.trim(),
          dataType: field.dataType,
          value: String(field.value ?? '').trim(),
          order: index,
          visibleInTree: field.visibleInTree,
        }
    }
  })
}

function formFingerprint(form: NodeFormState) {
  return JSON.stringify({
    type: form.type.trim(),
    title: form.title.trim(),
    fields: serializeFields(form.fields).map(({ order, ...field }) => field),
  })
}

function buildTree(nodes: ProjectNode[], createdSortDirection: CreatedSortDirection = null) {
  const nodesById = new Map(nodes.map((node) => [node.id, { ...node, children: [] as TreeNode[] }]))

  const childIds = new Set<string>()
  for (const node of nodesById.values()) {
    const parentNodeId = node.parentNodeId ?? null
    const parent = parentNodeId ? nodesById.get(parentNodeId) : null
    if (parent) {
      node.parentNodeId = parentNodeId
      childIds.add(node.id)
      parent.children.push(node)
    }
  }

  const sortNodes = (items: TreeNode[]) => {
    items.sort((left, right) => {
      if (createdSortDirection) {
        const leftCreatedAt = left.createdAt ? new Date(left.createdAt).getTime() : 0
        const rightCreatedAt = right.createdAt ? new Date(right.createdAt).getTime() : 0
        const createdDifference = leftCreatedAt - rightCreatedAt
        if (createdDifference !== 0) return createdSortDirection === 'ASC' ? createdDifference : -createdDifference
      }
      return (left.sortIndex ?? 0) - (right.sortIndex ?? 0) || left.title.localeCompare(right.title) || left.id.localeCompare(right.id)
    })
    items.forEach((item) => sortNodes(item.children))
  }

  const roots = [...nodesById.values()].filter((node) => !childIds.has(node.id))
  sortNodes(roots)
  return roots
}

function isOpenProposalStatus(status: string | null | undefined) {
  return status === 'PENDING' || status === 'NEEDS_UPDATE'
}

function openNodeProposalChanges(proposals: GraphChangeProposal[]) {
  return proposals.flatMap((proposal) => (
    proposal.changes
      .filter((change) => change.entityType === 'NODE' && isOpenProposalStatus(change.status))
      .map((change) => ({ proposal, change }))
  ))
}

function openEdgeProposalChanges(proposals: GraphChangeProposal[]) {
  return proposals.flatMap((proposal) => (
    proposal.changes
      .filter((change) => change.entityType === 'EDGE' && isOpenProposalStatus(change.status))
      .map((change) => ({ proposal, change }))
  ))
}

function proposalForChange(proposal: GraphChangeProposal, change: GraphChangeProposalChange, previousNode?: ProjectNode | null): TreeNodeProposal {
  return {
    proposalId: proposal.id,
    changeId: change.id,
    action: change.action,
    status: change.status,
    summary: change.summary,
    targetId: change.targetId,
    previousNode: previousNode ?? null,
    proposedNode: change.node ?? null,
  }
}

function canPlaceProposalChangeInTree(change: GraphChangeProposalChange, loadedNodeIds: Set<string>) {
  if (change.action === 'ADD') {
    const parentNodeId = change.node?.parentNodeId ?? null
    return !parentNodeId || loadedNodeIds.has(parentNodeId)
  }

  return loadedNodeIds.has(change.targetId)
}

function displayNodeForProposalChange(change: GraphChangeProposalChange) {
  if (change.action === 'ADD') {
    return change.node ?? null
  }

  return change.previousNode ?? change.node ?? null
}

function placementReasonForProposalChange(change: GraphChangeProposalChange, t: TFunction) {
  if (change.action === 'ADD') {
    const parentNodeId = change.node?.parentNodeId ?? null
    return parentNodeId ? t('workspace.parentOutsideTree') : t('workspace.topLevelOutsideTree')
  }

  return t('workspace.targetOutsideTree')
}

function mergeProposalNodes(nodes: ProjectNode[], proposals: GraphChangeProposal[]) {
  const mergedNodes = nodes.map((node) => ({ ...node }))
  const nodeIndexById = new Map(mergedNodes.map((node, index) => [node.id, index]))
  const loadedNodeIds = new Set(nodes.map((node) => node.id))
  const pendingAddChanges = openNodeProposalChanges(proposals)
    .filter(({ change }) => change.action === 'ADD' && change.node)
  const otherChanges = openNodeProposalChanges(proposals)
    .filter(({ change }) => change.action !== 'ADD')

  let remainingAdds = pendingAddChanges
  while (remainingAdds.length > 0) {
    const nextRemainingAdds: typeof pendingAddChanges = []
    let addedAny = false

    for (const { proposal, change } of remainingAdds) {
      if (!canPlaceProposalChangeInTree(change, loadedNodeIds)) {
        nextRemainingAdds.push({ proposal, change })
        continue
      }
      const proposalMeta = proposalForChange(proposal, change)
      mergedNodes.push({
        ...change.node!,
        id: change.targetId,
        proposal: proposalMeta,
      } as ProjectNode & { proposal: TreeNodeProposal })
      nodeIndexById.set(change.targetId, mergedNodes.length - 1)
      loadedNodeIds.add(change.targetId)
      addedAny = true
    }

    if (!addedAny) {
      break
    }
    remainingAdds = nextRemainingAdds
  }

  for (const { proposal, change } of otherChanges) {
    const targetIndex = nodeIndexById.get(change.targetId)
    if (targetIndex === undefined) {
      continue
    }

    const currentNode = mergedNodes[targetIndex]
    const proposalMeta = proposalForChange(proposal, change, change.previousNode ?? currentNode)
    mergedNodes[targetIndex] = {
      ...currentNode,
      proposal: proposalMeta,
    } as ProjectNode & { proposal: TreeNodeProposal }
  }

  return mergedNodes
}

function pendingProposalNodes(nodes: ProjectNode[], proposals: GraphChangeProposal[], t: TFunction): PendingProposalNode[] {
  const loadedNodeIds = new Set(nodes.map((node) => node.id))

  return openNodeProposalChanges(proposals)
    .filter(({ change }) => !canPlaceProposalChangeInTree(change, loadedNodeIds))
    .map(({ proposal, change }) => {
      const displayNode = displayNodeForProposalChange(change)
      if (!displayNode) {
        return null
      }

      const pendingNode: PendingProposalNode = {
        ...displayNode,
        id: change.targetId,
        children: [],
        proposal: proposalForChange(proposal, change, change.previousNode ?? displayNode),
        placementReason: placementReasonForProposalChange(change, t),
      }
      return pendingNode
    })
    .filter((node): node is PendingProposalNode => Boolean(node))
}

function proposalParentIds(proposals: GraphChangeProposal[]) {
  const nodeParentIds = openNodeProposalChanges(proposals)
    .map(({ change }) => change.node?.parentNodeId ?? null)
  const edgeParentIds = openEdgeProposalChanges(proposals)
    .filter(({ change }) => change.action !== 'DELETE' && change.edge?.relationType === relationType)
    .map(({ change }) => change.edge?.fromNodeId ?? null)

  return [...nodeParentIds, ...edgeParentIds]
    .filter((parentNodeId): parentNodeId is string => Boolean(parentNodeId))
}

function proposalContextNodeIds(proposals: GraphChangeProposal[]) {
  const ids = new Set<string>()

  for (const { change } of openNodeProposalChanges(proposals)) {
    const nodeId = change.action === 'ADD' ? change.node?.parentNodeId : change.targetId
    if (nodeId) {
      ids.add(nodeId)
    }
  }
  for (const { change } of openEdgeProposalChanges(proposals)) {
    if (change.edge?.fromNodeId) {
      ids.add(change.edge.fromNodeId)
    }
    if (change.edge?.toNodeId) {
      ids.add(change.edge.toNodeId)
    }
  }

  return ids
}

async function loadProposalContextNodes(projectId: string, proposals: GraphChangeProposal[], initialNodes: ProjectNode[]) {
  const nodesById = new Map(initialNodes.map((node) => [node.id, node]))
  const queuedIds = [...proposalContextNodeIds(proposals)]
  const visitedIds = new Set<string>()

  while (queuedIds.length > 0) {
    const nodeIds = queuedIds.splice(0).filter((nodeId) => {
      if (visitedIds.has(nodeId)) {
        return false
      }
      visitedIds.add(nodeId)
      return true
    })
    const loadedNodes = await Promise.all(nodeIds.map((nodeId) => (
      nodesById.get(nodeId) ?? getNode(projectId, nodeId).catch(() => null)
    )))

    for (const node of loadedNodes) {
      if (!node) {
        continue
      }
      nodesById.set(node.id, node)
      if (node.parentNodeId && !visitedIds.has(node.parentNodeId)) {
        queuedIds.push(node.parentNodeId)
      }
    }
  }

  return [...nodesById.values()]
}

function collectDescendantIds(nodeId: string, tree: TreeNode[]) {
  const target = findTreeNode(tree, nodeId)
  if (!target) {
    return []
  }

  const ids: string[] = []
  function visit(node: TreeNode) {
    ids.push(node.id)
    node.children.forEach(visit)
  }

  visit(target)
  return ids
}

function flattenTree(nodes: TreeNode[], depth = 0): FlatTreeNode[] {
  return nodes.flatMap((node) => [
    { ...node, depth },
    ...flattenTree(node.children, depth + 1),
  ])
}

function nodeMatchesWorkItemFilterCondition(node: ProjectNode, condition: WorkItemFilterCondition) {
  const status = normalizedStatus(nodeFieldValue(node, 'status')).replaceAll(' ', '_')
  if (condition.field === 'STATUS') {
    return status === condition.value
  }

  const priority = String(nodeFieldValue(node, 'priority') ?? '').trim().toUpperCase()
  if (condition.field === 'PRIORITY') {
    return priority === condition.value
  }

  const dueDateValue = nodeFieldValue(node, 'dueDate')
  const dueDate = typeof dueDateValue === 'string' && dueDateValue.trim()
    ? new Date(`${dueDateValue}T00:00:00`)
    : null
  const isValidDueDate = dueDate !== null && !Number.isNaN(dueDate.getTime())
  if (condition.field === 'DUE_DATE') {
    if (condition.value === 'NONE') return !isValidDueDate
    if (!isValidDueDate) {
      return false
    }
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const daysUntilDue = Math.round((dueDate.getTime() - today.getTime()) / 86_400_000)
    if (condition.value === 'OVERDUE') return daysUntilDue < 0
    if (condition.value === 'TODAY') return daysUntilDue === 0
    return daysUntilDue >= 0 && daysUntilDue <= 7
  }

  if (condition.field === 'ASSIGNEE') {
    const [assigneeType, assigneeId] = condition.value.split(':', 2)
    const assigneeFieldName = assigneeType === 'TEAM' ? 'assigneeTeamIds' : 'assigneeUserIds'
    return parseStringArrayValue(nodeFieldValue(node, assigneeFieldName)).includes(assigneeId)
  }

  return false
}

function conditionsMatchWorkItem(node: ProjectNode, conditions: WorkItemFilterCondition[]) {
  if (conditions.length === 0) return true
  let completedOrClause = false
  let currentAndClause = nodeMatchesWorkItemFilterCondition(node, conditions[0])
  for (const condition of conditions.slice(1)) {
    const matches = nodeMatchesWorkItemFilterCondition(node, condition)
    if (condition.operator === 'OR') {
      completedOrClause ||= currentAndClause
      currentAndClause = matches
    } else {
      currentAndClause &&= matches
    }
  }
  return completedOrClause || currentAndClause
}

function filterTreeByWorkItemFilters(nodes: TreeNode[], conditions: WorkItemFilterCondition[]): TreeNode[] {
  if (conditions.length === 0) {
    return nodes
  }
  return nodes
    .map((node) => {
      const filteredChildren = filterTreeByWorkItemFilters(node.children, conditions)
      const nodeMatches = conditionsMatchWorkItem(node, conditions)
      if (nodeMatches || filteredChildren.length > 0) {
        return { ...node, children: filteredChildren }
      }
      return null
    })
    .filter((node): node is TreeNode => Boolean(node))
}

function collectExpandableTreeNodeIds(nodes: TreeNode[]) {
  const ids = new Set<string>()

  function visit(node: TreeNode) {
    if (node.children.length > 0) {
      ids.add(node.id)
      node.children.forEach(visit)
    }
  }

  nodes.forEach(visit)
  return ids
}

function openWorkspaceChangesForNode(proposals: GraphChangeProposal[], nodeId: string) {
  return proposals.flatMap((proposal) => proposal.changes
    .filter((change) => isOpenProposalStatus(change.status))
    .filter((change) => {
      if (change.entityType === 'NODE') return change.targetId === nodeId
      if (change.entityType === 'ENTRY') return (change.entry ?? change.previousEntry)?.workItemId === nodeId
      const relationship = change.relationship ?? change.previousRelationship
      return Boolean(relationship && (
        (relationship.fromEntityType === 'WORK_ITEM' && relationship.fromEntityId === nodeId)
        || (relationship.toEntityType === 'WORK_ITEM' && relationship.toEntityId === nodeId)
      ))
    })
    .map((change) => ({ proposalId: proposal.id, change })))
}

function pageInfoFromResponse(response: NodePageResponse): NodePageInfo {
  return {
    page: response.page,
    size: response.size,
    totalItems: response.totalItems,
    totalPages: response.totalPages,
  }
}

function mergeNodesById(currentNodes: ProjectNode[], nextNodes: ProjectNode[]) {
  const nodeById = new Map(currentNodes.map((node) => [node.id, node]))
  nextNodes.forEach((node) => {
    nodeById.set(node.id, node)
  })
  return [...nodeById.values()]
}

async function loadRequestedWorkItemTreePath(projectId: string, workItemId: string, rootNodes: ProjectNode[]) {
  let nextNodes = rootNodes
  const nodeById = new Map(nextNodes.map((node) => [node.id, node]))
  const requestedNode = nodeById.get(workItemId) ?? await getNode(projectId, workItemId).catch(() => null)

  if (!requestedNode) {
    return {
      nodes: nextNodes,
      selectedNode: null,
      expandedNodeIds: [] as string[],
      loadedChildrenParentIds: new Set<string>(),
      childrenPageInfoByParentId: new Map<string, NodePageInfo>(),
    }
  }

  const ancestorNodes: ProjectNode[] = []
  const ancestorNodeIds: string[] = []
  const visitedNodeIds = new Set([requestedNode.id])
  let parentNodeId = requestedNode.parentNodeId ?? null

  while (parentNodeId && !visitedNodeIds.has(parentNodeId)) {
    const parentNode = nodeById.get(parentNodeId)
      ?? ancestorNodes.find((node) => node.id === parentNodeId)
      ?? await getNode(projectId, parentNodeId).catch(() => null)

    if (!parentNode) {
      break
    }

    ancestorNodes.unshift(parentNode)
    ancestorNodeIds.unshift(parentNode.id)
    visitedNodeIds.add(parentNode.id)
    parentNodeId = parentNode.parentNodeId ?? null
  }

  nextNodes = mergeNodesById(nextNodes, [...ancestorNodes, requestedNode])

  const loadedChildrenParentIds = new Set<string>()
  const childrenPageInfoByParentId = new Map<string, NodePageInfo>()
  const childResponses = await Promise.all(
    ancestorNodeIds.map(async (ancestorNodeId) => ({
      ancestorNodeId,
      response: await listTreeNodes(projectId, { parentNodeId: ancestorNodeId, page: 0, size: treePageSize }).catch(() => null),
    })),
  )

  for (const { ancestorNodeId, response } of childResponses) {
    if (!response) {
      continue
    }

    nextNodes = mergeNodesById(nextNodes, response.items)
    loadedChildrenParentIds.add(ancestorNodeId)
    childrenPageInfoByParentId.set(ancestorNodeId, pageInfoFromResponse(response))
  }

  return {
    nodes: nextNodes,
    selectedNode: requestedNode,
    expandedNodeIds: ancestorNodeIds,
    loadedChildrenParentIds,
    childrenPageInfoByParentId,
  }
}

function removeLoadedSubtree(nodes: ProjectNode[], nodeIds: Set<string>) {
  return nodes.filter((node) => !nodeIds.has(node.id))
}

function findTreeNode(nodes: TreeNode[], nodeId: string | null): TreeNode | null {
  if (!nodeId) {
    return null
  }

  for (const node of nodes) {
    if (node.id === nodeId) {
      return node
    }

    const childMatch = findTreeNode(node.children, nodeId)
    if (childMatch) {
      return childMatch
    }
  }

  return null
}

function findTreeNodePath(nodes: TreeNode[], nodeId: string | null, path: TreeNode[] = []): TreeNode[] {
  if (!nodeId) {
    return []
  }

  for (const node of nodes) {
    const nextPath = [...path, node]
    if (node.id === nodeId) {
      return nextPath
    }

    const childPath = findTreeNodePath(node.children, nodeId, nextPath)
    if (childPath.length > 0) {
      return childPath
    }
  }

  return []
}

function referenceUserLabel(user: User) {
  return user.displayName?.trim() || user.username || user.email || 'Unnamed user'
}

function referenceTeamLabel(team: Team) {
  return team.name || 'Unnamed team'
}

function entryAuthorLabel(entry: Entry, userLabels: Map<string, string>, fallback: string) {
  return entry.authorDisplayName?.trim() || userLabels.get(entry.authorUserId) || fallback
}

function WorkItemEntries({
  content,
  renderChild,
  canReorder,
  highlightConnectors,
  showEntries,
  selectedEntryId,
  startAdding,
  defaultType,
  composerPlaceholder,
  isQuestion,
  acceptedAnswerEntryId,
  isAiReviewAvailable,
  onEntryComposerOpened,
  onSelect,
  onCreate,
  onReviewNew,
  onAcceptNewReview,
  onRejectNewReview,
  onUpdate,
  onReview,
  onAcceptReview,
  onRejectReview,
  onAcceptAnswer,
  onMoveContent,
  onDelete,
}: {
  content: OrderedWorkItemContent[]
  renderChild: (child: TreeNode, index: number, total: number) => ReactNode
  canReorder: boolean
  highlightConnectors: boolean
  showEntries: boolean
  selectedEntryId: string | null
  startAdding: boolean
  defaultType: string
  composerPlaceholder: string
  isQuestion: boolean
  acceptedAnswerEntryId: string | null
  isAiReviewAvailable: boolean
  onEntryComposerOpened: () => void
  onSelect: (entry: Entry) => void
  onCreate: (type: string, body: string) => Promise<void>
  onReviewNew: (type: string, body: string, instruction?: string) => Promise<EntryAiReview>
  onAcceptNewReview: (type: string, review: EntryAiReview) => Promise<void>
  onRejectNewReview: (type: string, review: EntryAiReview) => Promise<void>
  onUpdate: (entry: Entry, type: string, body: string) => Promise<void>
  onReview: (entry: Entry, type: string, body: string, instruction?: string) => Promise<EntryAiReview>
  onAcceptReview: (entry: Entry, review: EntryAiReview) => Promise<void>
  onRejectReview: (entry: Entry, review: EntryAiReview) => Promise<void>
  onAcceptAnswer: (entryId: string) => Promise<void>
  onMoveContent: (entityType: 'WORK_ITEM' | 'ENTRY', entityId: string, offset: number) => Promise<void>
  onDelete: (entryId: string) => Promise<void>
}) {
  const { t } = useTranslation()
  const [isAdding, setIsAdding] = useState(false)
  const [newType, setNewType] = useState(defaultType)
  const [newBody, setNewBody] = useState('')
  const [editingEntryId, setEditingEntryId] = useState<string | null>(null)
  const [editType, setEditType] = useState('COMMENT')
  const [editBody, setEditBody] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [reviewByEntryId, setReviewByEntryId] = useState<Map<string, EntryAiReview>>(new Map())
  const [reviewFeedbackByEntryId, setReviewFeedbackByEntryId] = useState<Map<string, string>>(new Map())
  const [newEntryReview, setNewEntryReview] = useState<EntryAiReview | null>(null)
  const [newReviewFeedback, setNewReviewFeedback] = useState('')
  const [acceptingAnswerEntryId, setAcceptingAnswerEntryId] = useState<string | null>(null)
  const visibleContent = showEntries ? content : content.filter((item) => item.entityType === 'WORK_ITEM')

  useEffect(() => {
    if (startAdding) {
      setIsAdding(true)
      setNewType(defaultType)
      onEntryComposerOpened()
    }
  }, [onEntryComposerOpened, startAdding])

  async function reviewNewEntry() {
    if (!newBody.trim()) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      const review = await onReviewNew(newType, newBody.trim())
      setNewEntryReview({ ...review, entryType: newType })
      setIsAdding(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedReviewUpdate'))
    } finally {
      setIsSaving(false)
    }
  }

  async function reviewEditedEntry(entry: Entry) {
    if (!editBody.trim()) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      const review = await onReview(entry, editType, editBody.trim())
      setReviewByEntryId((current) => new Map(current).set(entry.id, { ...review, entryType: editType }))
      setEditingEntryId(null)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedReviewUpdate'))
    } finally {
      setIsSaving(false)
    }
  }

  async function saveNewEntry() {
    if (!newBody.trim()) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      await onCreate(newType, newBody.trim())
      setNewBody('')
      setIsAdding(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedAddUpdate'))
    } finally {
      setIsSaving(false)
    }
  }

  async function saveEditedEntry(entry: Entry) {
    if (!editBody.trim()) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      await onUpdate(entry, editType, editBody.trim())
      setEditingEntryId(null)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSaveUpdate'))
    } finally {
      setIsSaving(false)
    }
  }

  if (visibleContent.length === 0 && !isAdding && !newEntryReview) {
    return null
  }

  return (
    <div className="space-y-1" data-work-item-entries>
      {visibleContent.map((item) => {
        const contentIndex = content.indexOf(item)
        if (item.entityType === 'WORK_ITEM') {
          return (
            <div
              key={`work-item-${item.entityId}`}
              className={cn(
                'relative before:absolute before:-left-3 before:top-4 before:h-px before:w-3',
                highlightConnectors ? 'before:bg-primary/50' : 'before:bg-border',
              )}
            >
              {renderChild(item.child, contentIndex, content.length)}
            </div>
          )
        }
        const entry = item.entry
        const isEditing = editingEntryId === entry.id
        const review = reviewByEntryId.get(entry.id)
        const isAcceptedAnswer = entry.id === acceptedAnswerEntryId
        return (
          <div key={entry.id} className={cn('group/entry-row relative flex min-h-[50px] min-w-0 items-center gap-2 rounded-md border border-border/50 bg-muted/15 px-2 py-1 transition-colors hover:bg-muted/35', selectedEntryId === entry.id && 'border-primary/40 bg-primary/5 ring-1 ring-inset ring-primary/25')} onClick={() => onSelect(entry)}>
            <span className={cn('absolute top-1/2 -left-3 h-px w-3', highlightConnectors ? 'bg-primary/50' : 'bg-border')} aria-hidden="true" />
            <span className="grid h-7 w-7 shrink-0 place-items-center text-muted-foreground" aria-hidden="true"><MessageSquareText className="h-3.5 w-3.5" /></span>
            {isEditing ? (
              <div className="flex min-w-0 flex-1 items-center gap-2">
                <NativeSelect className="w-32 shrink-0" value={editType} onChange={(event) => setEditType(event.target.value)} disabled={isSaving}>
                  {entryTypeOptions.map((type) => <NativeSelectOption key={type} value={type}>{translateEntryType(type, t)}</NativeSelectOption>)}
                </NativeSelect>
                <Input
                  autoFocus
                  className="h-7 min-w-0 flex-1 text-sm"
                  value={editBody}
                  onChange={(event) => setEditBody(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      void saveEditedEntry(entry)
                    }
                    if (event.key === 'Escape') {
                      event.preventDefault()
                      setEditingEntryId(null)
                    }
                  }}
                  disabled={isSaving}
                  aria-label={t('workspace.updateContent')}
                />
                {isAiReviewAvailable ? (
                  <Button type="button" size="xs" variant="outline" disabled={isSaving} onClick={() => void reviewEditedEntry(entry)}>
                    <Bot className="h-3.5 w-3.5" /> {t('workspace.aiReview')}
                  </Button>
                ) : null}
                <Button type="button" size="icon-xs" variant="ghost" disabled={isSaving} onClick={() => void saveEditedEntry(entry)} aria-label={t('workspace.updateSaved')} title={t('workspace.updateSaved')}>
                  {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
                </Button>
                <Button type="button" size="icon-xs" variant="ghost" disabled={isSaving} onClick={() => setEditingEntryId(null)} aria-label={t('common.cancel')} title={t('common.cancel')}><X /></Button>
              </div>
            ) : (
              <>
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 items-start">
                    <div className="min-w-0 flex-1">
                      <p className="min-w-0 truncate text-sm leading-5 text-foreground" title={entry.body}>{entry.body}</p>
                      <div className="mt-0.5 flex min-h-5 min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap text-[11px] leading-4 text-muted-foreground">
                        <Badge variant="outline" className={cn('h-5 shrink-0 px-1.5 text-[10px] font-medium uppercase tracking-wide', entryTypeBadgeClass(entry.type))}>{translateEntryType(entry.type, t)}</Badge>
                        {isAcceptedAnswer ? (
                          <span className="flex shrink-0 items-center gap-1 text-primary">
                            <Check className="h-3 w-3" /> Accepted
                          </span>
                        ) : null}
                      </div>
                    </div>
                {!review ? (
                  <div className="ml-auto flex w-7 shrink-0 items-center justify-end opacity-0 transition-opacity group-hover/entry-row:opacity-100 group-focus-within/entry-row:opacity-100 group-hover/entry-row:pointer-events-auto group-focus-within/entry-row:pointer-events-auto pointer-events-none">
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={(
                          <Button type="button" size="icon-xs" variant="ghost" aria-label={t('workspace.updateActions')} title={t('workspace.updateActions')}>
                            <MoreHorizontal />
                          </Button>
                        )}
                      />
                      <DropdownMenuContent align="end" className="w-44">
                        {canReorder && contentIndex > 0 ? (
                          <DropdownMenuItem onClick={(event) => { event.stopPropagation(); void onMoveContent('ENTRY', entry.id, -1) }}>
                            <ArrowUp className="h-4 w-4" /> {t('workspace.moveUp')}
                          </DropdownMenuItem>
                        ) : null}
                        {canReorder && contentIndex < content.length - 1 ? (
                          <DropdownMenuItem onClick={(event) => { event.stopPropagation(); void onMoveContent('ENTRY', entry.id, 1) }}>
                            <ArrowDown className="h-4 w-4" /> {t('workspace.moveDown')}
                          </DropdownMenuItem>
                        ) : null}
                        {isQuestion && !isAcceptedAnswer ? (
                          <DropdownMenuItem
                            disabled={acceptingAnswerEntryId !== null}
                            onClick={async (event) => {
                              event.stopPropagation()
                              setAcceptingAnswerEntryId(entry.id)
                              try {
                                await onAcceptAnswer(entry.id)
                              } catch (error) {
                                toast.error(error instanceof Error ? error.message : t('workspace.failedAcceptAnswer'))
                              } finally {
                                setAcceptingAnswerEntryId(null)
                              }
                            }}
                          >
                            {acceptingAnswerEntryId === entry.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                            {t('workspace.acceptAnswer')}
                          </DropdownMenuItem>
                        ) : null}
                        <DropdownMenuItem onClick={(event) => { event.stopPropagation(); setEditingEntryId(entry.id); setEditType(entry.type); setEditBody(entry.body) }}>
                          <Pencil className="h-4 w-4" /> {t('workspace.editUpdate')}
                        </DropdownMenuItem>
                        <DeleteConfirmPopover
                          title={t('workspace.deleteUpdate')}
                          description={t('workspace.deleteUpdateDescription')}
                          trigger={(
                            <DropdownMenuItem variant="destructive" onClick={(event) => event.stopPropagation()}>
                              <Trash2 className="h-4 w-4" /> {t('common.delete')}
                            </DropdownMenuItem>
                          )}
                          onConfirm={() => onDelete(entry.id)}
                        />
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                ) : null}
                </div>
              {review ? (
                <div className="mt-2 ml-4 flex min-w-0 items-start gap-3 rounded-md border border-primary/25 bg-primary/5 px-3 py-2.5">
                  <Bot className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium text-muted-foreground">{t('workspace.yourDraft')}</p>
                    <p className="whitespace-pre-wrap break-words text-sm text-muted-foreground line-through">{review.originalBody}</p>
                    <p className="mt-2 text-xs font-medium text-primary">{t('workspace.suggested')}</p>
                    <p className="whitespace-pre-wrap break-words text-sm">{review.proposedBody}</p>
                    {review.proposedType && review.proposedType !== (review.entryType ?? entry.type) ? (
                      <p className="mt-2 text-xs text-primary">{t('workspace.classification')}: {translateEntryType(review.entryType ?? entry.type, t)} → {translateEntryType(review.proposedType, t)}</p>
                    ) : null}
                    {review.rationale ? <p className="mt-1 whitespace-pre-wrap break-words text-xs text-muted-foreground">{review.rationale}</p> : null}
                    <div className="mt-2 flex gap-2">
                      <Input
                        className="h-7 bg-white text-xs"
                        value={reviewFeedbackByEntryId.get(entry.id) ?? ''}
                        onChange={(event) => setReviewFeedbackByEntryId((current) => new Map(current).set(entry.id, event.target.value))}
                        placeholder={t('workspace.tellAi')}
                        disabled={isSaving}
                      />
                      <Button type="button" size="xs" variant="outline" disabled={isSaving} onClick={async () => {
                        setIsSaving(true)
                        try {
                          const nextReview = await onReview(entry, review.proposedType ?? review.entryType ?? entry.type, review.proposedBody, reviewFeedbackByEntryId.get(entry.id))
                          setReviewByEntryId((current) => new Map(current).set(entry.id, { ...nextReview, entryType: review.proposedType ?? review.entryType ?? entry.type }))
                          setReviewFeedbackByEntryId((current) => { const next = new Map(current); next.delete(entry.id); return next })
                        } catch (error) {
                          toast.error(error instanceof Error ? error.message : t('workspace.failedSuggestionUpdate'))
                        } finally {
                          setIsSaving(false)
                        }
                      }}>{t('workspace.askAgain')}</Button>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-1 pt-0.5">
                  <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={async () => {
                    setIsSaving(true)
                    try {
                      await onAcceptReview(entry, review)
                      setReviewByEntryId((current) => {
                        const next = new Map(current)
                        next.delete(entry.id)
                        return next
                      })
                    } catch (error) {
                      toast.error(error instanceof Error ? error.message : t('workspace.failedAcceptSuggestion'))
                    } finally {
                      setIsSaving(false)
                    }
                  }} aria-label={t('workspace.acceptSuggestion')} title={t('workspace.acceptSuggestion')}>
                    {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
                  </Button>
                  <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={() => {
                    setEditingEntryId(entry.id)
                    setEditType(review.proposedType ?? review.entryType ?? entry.type)
                    setEditBody(review.proposedBody)
                    setReviewByEntryId((current) => { const next = new Map(current); next.delete(entry.id); return next })
                  }} aria-label={t('workspace.keepEditingSuggestion')} title={t('workspace.keepEditing')}><Pencil /></Button>
                  <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={async () => {
                    setIsSaving(true)
                    try {
                      await onRejectReview(entry, review)
                      setReviewByEntryId((current) => {
                        const next = new Map(current)
                        next.delete(entry.id)
                        return next
                      })
                      setEditingEntryId(entry.id)
                      setEditType(review.entryType ?? entry.type)
                      setEditBody(review.originalBody)
                    } catch (error) {
                      toast.error(error instanceof Error ? error.message : t('workspace.failedRejectSuggestion'))
                    } finally {
                      setIsSaving(false)
                    }
                  }} aria-label={t('workspace.rejectSuggestion')} title={t('workspace.rejectSuggestion')}><X /></Button>
                  </div>
                </div>
              ) : null}
                </div>
              </>
            )}
          </div>
        )
      })}
      {isAdding ? (
        <div className="relative flex min-w-0 items-center gap-2 rounded-sm py-2 pl-3">
          <span className="absolute top-1/2 left-0 size-1.5 -translate-x-[3px] -translate-y-1/2 rounded-full bg-primary/60" aria-hidden="true" />
          <NativeSelect className="w-32 shrink-0" value={newType} onChange={(event) => setNewType(event.target.value)} disabled={isSaving} aria-label={t('workspace.updateClassification')}>
            {entryTypeOptions.map((type) => <NativeSelectOption key={type} value={type}>{translateEntryType(type, t)}</NativeSelectOption>)}
          </NativeSelect>
          <Input
            autoFocus
            className="h-7 min-w-0 flex-1 text-sm"
            value={newBody}
            onChange={(event) => setNewBody(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                void saveNewEntry()
              }
              if (event.key === 'Escape') {
                event.preventDefault()
                setIsAdding(false)
              }
            }}
            placeholder={composerPlaceholder}
            disabled={isSaving}
            aria-label={composerPlaceholder}
          />
          {isAiReviewAvailable ? (
            <Button type="button" size="xs" variant="outline" disabled={isSaving} onClick={() => void reviewNewEntry()}>
              <Bot className="h-3.5 w-3.5" /> {t('workspace.aiReview')}
            </Button>
          ) : null}
          <Button type="button" size="icon-xs" variant="ghost" disabled={isSaving} onClick={() => void saveNewEntry()} aria-label={t('workspace.addUpdate')} title={t('workspace.addUpdate')}>
            {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
          </Button>
          <Button type="button" size="icon-xs" variant="ghost" disabled={isSaving} onClick={() => setIsAdding(false)} aria-label={t('workspace.cancelNewUpdate')} title={t('common.cancel')}><X /></Button>
        </div>
      ) : null}
      {newEntryReview ? (
        <div className="relative mt-2 ml-3 flex min-w-0 items-start gap-3 rounded-md border border-primary/25 bg-primary/5 px-3 py-2.5">
          <Bot className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium text-muted-foreground">{t('workspace.yourDraft')}</p>
            <p className="whitespace-pre-wrap break-words text-sm text-muted-foreground line-through">{newEntryReview.originalBody}</p>
            <p className="mt-2 text-xs font-medium text-primary">{t('workspace.suggested')}</p>
            <p className="whitespace-pre-wrap break-words text-sm">{newEntryReview.proposedBody}</p>
            {newEntryReview.proposedType && newEntryReview.proposedType !== (newEntryReview.entryType ?? newType) ? (
              <p className="mt-2 text-xs text-primary">{t('workspace.classification')}: {translateEntryType(newEntryReview.entryType ?? newType, t)} → {translateEntryType(newEntryReview.proposedType, t)}</p>
            ) : null}
            {newEntryReview.rationale ? <p className="mt-1 whitespace-pre-wrap break-words text-xs text-muted-foreground">{newEntryReview.rationale}</p> : null}
            <div className="mt-2 flex gap-2">
              <Input className="h-7 bg-white text-xs" value={newReviewFeedback} onChange={(event) => setNewReviewFeedback(event.target.value)} placeholder={t('workspace.tellAi')} disabled={isSaving} />
              <Button type="button" size="xs" variant="outline" disabled={isSaving} onClick={async () => {
                setIsSaving(true)
                try {
                  const nextReview = await onReviewNew(newEntryReview.proposedType ?? newEntryReview.entryType ?? newType, newEntryReview.proposedBody, newReviewFeedback)
                  setNewEntryReview({ ...nextReview, entryType: newEntryReview.proposedType ?? newEntryReview.entryType ?? newType })
                  setNewReviewFeedback('')
                } catch (error) {
                  toast.error(error instanceof Error ? error.message : t('workspace.failedSuggestionUpdate'))
                } finally {
                  setIsSaving(false)
                }
              }}>{t('workspace.askAgain')}</Button>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1 pt-0.5">
          <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={async () => {
            setIsSaving(true)
            try {
              await onAcceptNewReview(newEntryReview.proposedType ?? newEntryReview.entryType ?? 'COMMENT', newEntryReview)
              setNewEntryReview(null)
              setNewBody('')
            } catch (error) {
              toast.error(error instanceof Error ? error.message : t('workspace.failedAcceptSuggestion'))
            } finally {
              setIsSaving(false)
            }
          }} aria-label={t('workspace.acceptSuggestion')} title={t('workspace.acceptSuggestion')}>
            {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
          </Button>
          <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={() => {
            setNewType(newEntryReview.proposedType ?? newEntryReview.entryType ?? newType)
            setNewBody(newEntryReview.proposedBody)
            setNewEntryReview(null)
            setIsAdding(true)
          }} aria-label={t('workspace.keepEditingSuggestion')} title={t('workspace.keepEditing')}><Pencil /></Button>
          <Button type="button" size="icon-sm" variant="ghost" disabled={isSaving} onClick={async () => {
            setIsSaving(true)
            try {
              await onRejectNewReview(newEntryReview.entryType ?? 'COMMENT', newEntryReview)
              setNewEntryReview(null)
              setNewType(newEntryReview.entryType ?? newType)
              setNewBody(newEntryReview.originalBody)
              setIsAdding(true)
            } catch (error) {
              toast.error(error instanceof Error ? error.message : t('workspace.failedRejectSuggestion'))
            } finally {
              setIsSaving(false)
            }
          }} aria-label={t('workspace.rejectSuggestion')} title={t('workspace.rejectSuggestion')}><X /></Button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

type BlockerUi = {
  relationshipsByNodeId: Map<string, Relationship[]>
  workItems: WorkItem[]
  titleById: Map<string, string>
  isSaving: boolean
  onRefresh: () => void
  onAdd: (nodeId: string, blockerId: string) => Promise<void>
  onRemove: (relationshipId: string) => Promise<void>
  onUpdateReason: (relationshipId: string, reason: string | null) => Promise<void>
}

function BlockerPopover({ nodeId, trigger, blockerUi }: { nodeId: string; trigger: ReactElement; blockerUi: BlockerUi }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [addingId, setAddingId] = useState<string | null>(null)
  const [editingReasonId, setEditingReasonId] = useState<string | null>(null)
  const [reasonDraft, setReasonDraft] = useState('')
  const blockers = blockerUi.relationshipsByNodeId.get(nodeId) ?? []
  const existingIds = new Set(blockers.map((relationship) => relationship.toEntityId))
  const normalizedSearch = search.trim().toLowerCase()
  const options = blockerUi.workItems
    .filter((item) => item.id !== nodeId && !existingIds.has(item.id))
    .filter((item) => !normalizedSearch || item.title.toLowerCase().includes(normalizedSearch))
    .slice(0, 8)

  async function addBlocker(blockerId: string) {
    setAddingId(blockerId)
    try {
      await blockerUi.onAdd(nodeId, blockerId)
      setSearch('')
    } finally {
      setAddingId(null)
    }
  }

  async function saveReason(relationshipId: string) {
    await blockerUi.onUpdateReason(relationshipId, reasonDraft.trim() || null)
    setEditingReasonId(null)
    setReasonDraft('')
  }

  return (
    <Popover open={open} onOpenChange={(nextOpen) => {
      setOpen(nextOpen)
      if (nextOpen) blockerUi.onRefresh()
      else {
        setSearch('')
        setEditingReasonId(null)
      }
    }}>
      <PopoverTrigger render={trigger} />
      <PopoverContent align="start" className="w-96 gap-0 p-0">
        <PopoverHeader className="border-b px-4 py-3">
          <PopoverTitle>{t('workspace.blockedBy')}</PopoverTitle>
        </PopoverHeader>

        {blockers.length > 0 ? (
          <div className="divide-y border-b">
            {blockers.map((relationship) => (
              <div key={relationship.id} className="flex items-start gap-3 px-3 py-2.5">
                <OctagonAlert className="mt-1 h-4 w-4 shrink-0 text-destructive" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{blockerUi.titleById.get(relationship.toEntityId) ?? relationship.toEntityId}</div>
                  {editingReasonId === relationship.id ? (
                    <div className="mt-2 flex gap-1.5">
                      <Input
                        autoFocus
                        className="h-8"
                        value={reasonDraft}
                        onChange={(event) => setReasonDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault()
                            void saveReason(relationship.id)
                          }
                        }}
                      />
                      <Button type="button" size="icon-sm" variant="ghost" disabled={blockerUi.isSaving} onClick={() => void saveReason(relationship.id)} aria-label={t('common.save')}><Check /></Button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="mt-0.5 block max-w-full truncate text-left text-xs text-muted-foreground hover:text-foreground"
                      onClick={() => {
                        setEditingReasonId(relationship.id)
                        setReasonDraft(relationship.reason ?? '')
                      }}
                    >
                      {relationship.reason || t('workspace.addReason')}
                    </button>
                  )}
                </div>
                <Button
                  type="button"
                  size="icon-sm"
                  variant="ghost"
                  disabled={blockerUi.isSaving}
                  onClick={() => void blockerUi.onRemove(relationship.id)}
                  aria-label={t('workspace.removeBlocker')}
                  title={t('workspace.removeBlocker')}
                >
                  <X />
                </Button>
              </div>
            ))}
          </div>
        ) : null}

        <div className="space-y-4 p-4">
          <div className="space-y-2">
            <label htmlFor={`blocker-search-${nodeId}`} className="block text-sm font-semibold">{t('workspace.searchWorkItems')}</label>
            <Input id={`blocker-search-${nodeId}`} autoFocus value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t('common.search')} />
          </div>
          <div className="max-h-52 overflow-auto">
            {options.length === 0 ? (
              <div className="px-2 py-5 text-center text-sm text-muted-foreground">{t('workspace.noAvailableWorkItems')}</div>
            ) : options.map((item) => (
              <button
                key={item.id}
                type="button"
                className="flex min-h-9 w-full items-center gap-2 rounded-sm px-2 text-left text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
                disabled={addingId !== null || blockerUi.isSaving}
                onClick={() => void addBlocker(item.id)}
              >
                {addingId === item.id ? <Loader2 className="h-4 w-4 shrink-0 animate-spin" /> : <FileText className="h-4 w-4 shrink-0" />}
                <span className="min-w-0 flex-1 truncate">{item.title}</span>
              </button>
            ))}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

type WorkItemRelationshipUi = {
  workItems: WorkItem[]
  isSaving: boolean
  onAdd: (nodeId: string, relatedNodeId: string, type: string, direction: 'OUTGOING' | 'INCOMING', reason: string | null) => Promise<void>
}

function WorkItemRelationshipPopover({ nodeId, trigger, relationshipUi }: { nodeId: string; trigger: ReactElement; relationshipUi: WorkItemRelationshipUi }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [type, setType] = useState<string>(workItemRelationshipTypes[0])
  const [direction, setDirection] = useState<'OUTGOING' | 'INCOMING'>('OUTGOING')
  const [reason, setReason] = useState('')
  const [search, setSearch] = useState('')
  const [addingId, setAddingId] = useState<string | null>(null)
  const normalizedSearch = search.trim().toLowerCase()
  const options = relationshipUi.workItems
    .filter((item) => item.id !== nodeId)
    .filter((item) => !normalizedSearch || item.title.toLowerCase().includes(normalizedSearch))
    .slice(0, 8)

  async function addRelationship(relatedNodeId: string) {
    setAddingId(relatedNodeId)
    try {
      await relationshipUi.onAdd(nodeId, relatedNodeId, type, direction, reason.trim() || null)
      setSearch('')
      setReason('')
      setOpen(false)
    } finally {
      setAddingId(null)
    }
  }

  return (
    <Popover open={open} onOpenChange={(nextOpen) => {
      setOpen(nextOpen)
      if (!nextOpen) {
        setSearch('')
        setReason('')
      }
    }}>
      <PopoverTrigger render={trigger} />
      <PopoverContent align="start" className="w-96 gap-0 p-0">
        <PopoverHeader className="border-b px-4 py-3">
          <PopoverTitle>{t('workspace.addRelationship')}</PopoverTitle>
        </PopoverHeader>
        <div className="space-y-4 p-4">
          <div className="space-y-2">
            <label htmlFor={`relationship-type-${nodeId}`} className="block text-sm font-semibold">{t('common.type')}</label>
            <NativeSelect id={`relationship-type-${nodeId}`} value={type} onChange={(event) => setType(event.target.value)}>
              {workItemRelationshipTypes.map((option) => <NativeSelectOption key={option} value={option}>{translateRelationshipType(option, t)}</NativeSelectOption>)}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <label htmlFor={`relationship-direction-${nodeId}`} className="block text-sm font-semibold">{t('workspace.direction')}</label>
            <NativeSelect id={`relationship-direction-${nodeId}`} value={direction} onChange={(event) => setDirection(event.target.value as 'OUTGOING' | 'INCOMING')}>
              <NativeSelectOption value="OUTGOING">{t('workspace.thisItemToSelected')}</NativeSelectOption>
              <NativeSelectOption value="INCOMING">{t('workspace.selectedToThisItem')}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <label htmlFor={`relationship-reason-${nodeId}`} className="block text-sm font-semibold">{t('workspace.reason')} <span className="font-normal text-muted-foreground">({t('workspace.optional')})</span></label>
            <Input id={`relationship-reason-${nodeId}`} value={reason} onChange={(event) => setReason(event.target.value)} />
          </div>
          <div className="space-y-2">
            <label htmlFor={`relationship-search-${nodeId}`} className="block text-sm font-semibold">{t('common.workItem')}</label>
            <Input id={`relationship-search-${nodeId}`} value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t('common.search')} />
          </div>
          <div className="max-h-52 overflow-auto">
            {options.length === 0 ? (
              <div className="px-2 py-5 text-center text-sm text-muted-foreground">{t('workspace.noAvailableWorkItems')}</div>
            ) : options.map((item) => (
              <button
                key={item.id}
                type="button"
                className="flex min-h-9 w-full items-center gap-2 rounded-sm px-2 text-left text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
                disabled={addingId !== null || relationshipUi.isSaving}
                onClick={() => void addRelationship(item.id)}
              >
                {addingId === item.id ? <Loader2 className="h-4 w-4 shrink-0 animate-spin" /> : <FileText className="h-4 w-4 shrink-0" />}
                <span className="min-w-0 flex-1 truncate">{item.title}</span>
              </button>
            ))}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function TreeRowContent({
  node,
  depth,
  expandedNodeIds,
  selectedNodeId,
  highlightedNodeId,
  selectedContextNodeIds,
  dimUnrelatedNodes,
  isSaving,
  decidingProposalChangeId,
  loadedChildrenParentIds,
  loadingChildrenNodeIds,
  childrenPageInfoByParentId,
  blockerUi,
  entriesByWorkItemId,
  contentOrderByParent,
  acceptedAnswerByQuestionId,
  userLabels,
  teamLabels,
  selectedEntryId,
  isAiReviewAvailable,
  canReorder,
  canMoveUp,
  canMoveDown,
  onToggle,
  onLoadMoreChildren,
  onSelect,
  onSelectEntry,
  onAddChild,
  onFocus,
  onMove,
  onDelete,
  onEditTitle,
  onCreateEntry,
  onReviewNewEntry,
  onAcceptNewEntryReview,
  onRejectNewEntryReview,
  onUpdateEntry,
  onReviewEntry,
  onAcceptEntryReview,
  onRejectEntryReview,
  onAcceptAnswer,
  onMoveContentOrder,
  onMoveInContentOrder,
  onDeleteEntry,
  autoEditTitleNodeId,
  onAutoEditTitleStarted,
  onDecideProposal,
}: {
  node: TreeNode
  depth: number
  expandedNodeIds: Set<string>
  selectedNodeId: string | null
  highlightedNodeId: string | null
  selectedContextNodeIds: Set<string>
  dimUnrelatedNodes: boolean
  isSaving: boolean
  decidingProposalChangeId: string | null
  loadedChildrenParentIds: Set<string>
  loadingChildrenNodeIds: Set<string>
  childrenPageInfoByParentId: Map<string, NodePageInfo>
  blockerUi: BlockerUi
  entriesByWorkItemId: Map<string, Entry[]>
  contentOrderByParent: Map<string, ContentOrderItem[]>
  acceptedAnswerByQuestionId: Map<string, string>
  userLabels: Map<string, string>
  teamLabels: Map<string, string>
  selectedEntryId: string | null
  isAiReviewAvailable: boolean
  canReorder: boolean
  canMoveUp: boolean
  canMoveDown: boolean
  onToggle: (nodeId: string) => void
  onLoadMoreChildren: (parentId: string) => void
  onSelect: (node: ProjectNode) => void
  onSelectEntry: (entry: Entry) => void
  onAddChild: (parentId: string, type?: string) => void
  onFocus: (node: TreeNode) => void
  onMove: (node: TreeNode) => void
  onDelete: (nodeId: string) => void
  onEditTitle: (node: ProjectNode, title: string) => Promise<void>
  onCreateEntry: (workItemId: string, type: string, body: string) => Promise<void>
  onReviewNewEntry: (workItemId: string, type: string, body: string, instruction?: string) => Promise<EntryAiReview>
  onAcceptNewEntryReview: (workItemId: string, type: string, review: EntryAiReview) => Promise<void>
  onRejectNewEntryReview: (workItemId: string, type: string, review: EntryAiReview) => Promise<void>
  onUpdateEntry: (entry: Entry, type: string, body: string) => Promise<void>
  onReviewEntry: (entry: Entry, type: string, body: string, instruction?: string) => Promise<EntryAiReview>
  onAcceptEntryReview: (entry: Entry, review: EntryAiReview) => Promise<void>
  onRejectEntryReview: (entry: Entry, review: EntryAiReview) => Promise<void>
  onAcceptAnswer: (questionId: string, entryId: string) => Promise<void>
  onMoveContentOrder: (parentWorkItemId: string | null, entityType: 'WORK_ITEM' | 'ENTRY', entityId: string, offset: number) => Promise<void>
  onMoveInContentOrder: (offset: number) => void
  onDeleteEntry: (entryId: string) => Promise<void>
  autoEditTitleNodeId: string | null
  onAutoEditTitleStarted: () => void
  onDecideProposal: (proposal: TreeNodeProposal, decision: 'ACCEPT' | 'REJECT') => void
}) {
  const { t } = useTranslation()
  const [isEditingTitle, setIsEditingTitle] = useState(false)
  const [draftTitle, setDraftTitle] = useState(node.title)
  const [isSavingTitle, setIsSavingTitle] = useState(false)
  const [entryComposerRequested, setEntryComposerRequested] = useState(false)
  const [showEntries, setShowEntries] = useState(false)
  const isExpanded = expandedNodeIds.has(node.id)
  const isSelected = selectedNodeId === node.id
  const isChatHighlighted = highlightedNodeId === node.id
  const isInSelectedContext = selectedContextNodeIds.has(node.id)
  const isDimmed = dimUnrelatedNodes && !isInSelectedContext && !isChatHighlighted
  const isLoadingChildren = loadingChildrenNodeIds.has(node.id)
  const hasLoadedChildren = loadedChildrenParentIds.has(node.id)
  const childPageInfo = childrenPageInfoByParentId.get(node.id)
  const hasMoreChildren = childPageInfo ? childPageInfo.page + 1 < childPageInfo.totalPages : false
  const canReorderContent = canReorder && hasLoadedChildren && !isLoadingChildren && !hasMoreChildren
  const proposal = node.proposal
  const canExpand = !proposal
  const isDecidingProposal = decidingProposalChangeId === proposal?.changeId
  const status = nodeFieldValue(node, 'status')
  const dueDate = formatWorkItemDueDate(nodeFieldValue(node, 'dueDate'))
  const priority = String(nodeFieldValue(node, 'priority') ?? '').trim().toUpperCase()
  const assigneeIds = [
    ...parseStringArrayValue(nodeFieldValue(node, 'assigneeUserIds')).map((id) => ({ id, label: userLabels.get(id) ?? t('common.unknownUser'), type: 'user' })),
    ...parseStringArrayValue(nodeFieldValue(node, 'assigneeTeamIds')).map((id) => ({ id, label: teamLabels.get(id) ?? t('common.unknownTeam'), type: 'team' })),
  ]
  const blockerCount = blockerUi.relationshipsByNodeId.get(node.id)?.length ?? 0
  const entryCount = entriesByWorkItemId.get(node.id)?.length ?? 0
  const orderedContent = orderedWorkItemContent(
    node,
    entriesByWorkItemId.get(node.id) ?? [],
    contentOrderByParent.get(contentParentKey(node.id)) ?? [],
  )

  useEffect(() => {
    if (autoEditTitleNodeId === node.id && !proposal) {
      setDraftTitle('')
      setIsEditingTitle(true)
      onAutoEditTitleStarted()
    }
  }, [autoEditTitleNodeId, node.id, onAutoEditTitleStarted, proposal])

  function beginTitleEdit() {
    setDraftTitle(node.title)
    setIsEditingTitle(true)
  }

  async function saveTitle() {
    const title = draftTitle.trim()
    if (!title) {
      toast.error(t('workspace.itemTitleRequired'))
      return
    }
    if (title === node.title) {
      setIsEditingTitle(false)
      return
    }

    setIsSavingTitle(true)
    try {
      await onEditTitle(node, title)
      setIsEditingTitle(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedUpdateTitle'))
    } finally {
      setIsSavingTitle(false)
    }
  }

  return (
    <div className="space-y-1">
      <div
        className={cn(
          'group/work-item-row flex min-h-[50px] min-w-0 items-start gap-2 rounded-md px-2 py-1 text-sm text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground',
          isSelected ? 'bg-primary/5 text-foreground ring-1 ring-inset ring-primary/35' : null,
          isChatHighlighted ? 'bg-primary/10 text-foreground ring-2 ring-primary/50' : null,
          isDimmed ? 'opacity-50' : null,
          proposal ? 'border border-primary/30 bg-primary/5 text-foreground hover:bg-primary/10' : null,
        )}
        style={{ marginLeft: `${depth * treeDepthIndentPx}px` }}
        data-work-item-id={node.id}
      >
        <button
          type="button"
          className="mt-0.5 grid h-5 w-7 shrink-0 place-items-center rounded-sm hover:bg-background"
          onClick={() => onToggle(node.id)}
          disabled={!canExpand}
          aria-label={isExpanded ? t('workspace.collapseItem') : t('workspace.expandItem')}
        >
          {isLoadingChildren ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : canExpand ? (
            isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />
          ) : (
            <CircleSmall className="h-3.5 w-3.5" />
          )}
        </button>
        {isEditingTitle ? (
          <form
            className="min-w-0 flex-1"
            onSubmit={(event) => {
              event.preventDefault()
              void saveTitle()
            }}
          >
            <div className="flex min-w-0 items-center gap-1.5">
              <div className="h-5 min-w-0 flex-1 rounded-md border bg-background px-1 transition-colors focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500/20">
                <Input
                  autoFocus
                  className="h-5 w-full border-0 bg-transparent px-1 text-sm font-medium shadow-none focus-visible:border-0 focus-visible:ring-0"
                  value={draftTitle}
                  onChange={(event) => setDraftTitle(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Escape') {
                      event.preventDefault()
                      setIsEditingTitle(false)
                    }
                  }}
                  disabled={isSavingTitle}
                  aria-label={t('common.title')}
                />
              </div>
              <div className="flex shrink-0 items-center gap-0.5">
                <Button type="submit" size="icon-xs" variant="ghost" className="h-5 w-5" disabled={isSavingTitle} aria-label={t('workspace.saveTitle')} title={t('workspace.saveTitle')}>
                  {isSavingTitle ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
                </Button>
                <Button
                  type="button"
                  size="icon-xs"
                  variant="ghost"
                  className="h-5 w-5"
                  disabled={isSavingTitle}
                  onClick={() => setIsEditingTitle(false)}
                  aria-label={t('workspace.cancelTitleEdit')}
                  title={t('common.cancel')}
                >
                  <X />
                </Button>
              </div>
            </div>
            <div className="mt-0.5 flex min-h-5 min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap text-[11px] leading-4 text-muted-foreground">
              <Badge variant="outline" className={cn('h-5 shrink-0 px-1.5 text-[10px] font-medium uppercase tracking-wide', workItemTypeBadgeClass(node.type))}>
                {translateWorkItemType(node.type, t)}
              </Badge>
              {!isDefaultWorkItemStatus(status) ? (
                <Badge variant="secondary" className="h-5 shrink-0 px-1.5 text-[10px] font-medium">
                  {translateStatus(String(status), t)}
                </Badge>
              ) : null}
              {dueDate ? (
                <Badge
                  variant={dueDate.isOverdue ? 'destructive' : dueDate.isDueSoon ? 'default' : 'outline'}
                  className="h-5 shrink-0 px-1.5 text-[10px] font-medium"
                  title={t('workspace.due', { date: dueDate.title })}
                >
                  {t('workspace.due', { date: dueDate.label })}
                </Badge>
              ) : null}
              {priority === 'HIGH' || priority === 'URGENT' ? (
                <Badge variant={priority === 'URGENT' ? 'destructive' : 'default'} className="h-5 shrink-0 px-1.5 text-[10px] font-medium">
                  {translatePriority(priority, t)}
                </Badge>
              ) : null}
              {assigneeIds.length > 0 ? (
                <div className="flex shrink-0 -space-x-1" aria-label={t('common.assignees')}>
                  {assigneeIds.slice(0, 3).map((assignee) => (
                    <span
                      key={`${assignee.type}-${assignee.id}`}
                      className="grid size-4 place-items-center rounded-full border-2 border-background bg-muted text-[8px] font-medium text-muted-foreground"
                      title={`${assignee.label} (${assignee.type})`}
                    >
                      {avatarInitials(assignee.label)}
                    </span>
                  ))}
                  {assigneeIds.length > 3 ? (
                    <span className="grid size-4 place-items-center rounded-full border-2 border-background bg-muted text-[8px] font-medium text-muted-foreground" title={t('workspace.moreAssignees', { count: assigneeIds.length - 3 })}>
                      +{assigneeIds.length - 3}
                    </span>
                  ) : null}
                </div>
              ) : null}
              {entryCount > 0 ? (
                <Button
                  type="button"
                  size="xs"
                  variant="ghost"
                  className={cn('h-5 shrink-0 gap-1 px-1 text-[11px]', showEntries ? 'bg-primary/10 text-primary' : 'text-muted-foreground')}
                  onClick={() => {
                    if (!showEntries && !isExpanded) {
                      onToggle(node.id)
                    }
                    setShowEntries((current) => !current)
                  }}
                  aria-expanded={showEntries}
                  aria-label={showEntries ? t('workspace.hideUpdates', { count: entryCount }) : t('workspace.showUpdates', { count: entryCount })}
                  title={showEntries ? t('workspace.hideUpdates', { count: entryCount }) : t('workspace.showUpdates', { count: entryCount })}
                >
                  <MessageSquareText className="h-3 w-3" />
                  <span>{entryCount}</span>
                </Button>
              ) : null}
              {blockerCount > 0 ? (
                <BlockerPopover
                  nodeId={node.id}
                  blockerUi={blockerUi}
                  trigger={(
                    <Button
                      type="button"
                      size="xs"
                      variant="outline"
                      className="h-5 shrink-0 gap-1 border-destructive/40 px-1 text-[11px] font-normal text-destructive hover:bg-destructive/10 hover:text-destructive"
                      aria-label={t('workspace.blockersCount', { count: blockerCount })}
                      title={t('workspace.viewBlockers')}
                    >
                      <OctagonAlert className="h-3 w-3" />
                      <span>{blockerCount}</span>
                    </Button>
                  )}
                />
              ) : null}
            </div>
          </form>
        ) : (
          <div className="min-w-0 flex-1">
            <button
              type="button"
              className="flex min-w-0 w-full items-center text-left leading-5"
              onClick={() => onSelect(node)}
              onDoubleClick={() => {
                if (!proposal && !isSaving) {
                  beginTitleEdit()
                }
              }}
              title={node.title}
            >
              <span
                className={cn(
                  'min-w-0 truncate text-sm font-medium text-foreground',
                  proposal?.action === 'DELETE' ? 'line-through decoration-destructive decoration-2' : null,
                )}
              >
                {node.title}
              </span>
            </button>
            <div className="mt-0.5 flex min-h-5 min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap text-[11px] leading-4 text-muted-foreground">
              <Badge variant="outline" className={cn('h-5 shrink-0 px-1.5 text-[10px] font-medium uppercase tracking-wide', workItemTypeBadgeClass(node.type))}>
                {translateWorkItemType(node.type, t)}
              </Badge>
              {!isDefaultWorkItemStatus(status) ? (
                <Badge variant="secondary" className="h-5 shrink-0 px-1.5 text-[10px] font-medium">
                  {translateStatus(String(status), t)}
                </Badge>
              ) : null}
              {dueDate ? (
                <Badge
                  variant={dueDate.isOverdue ? 'destructive' : dueDate.isDueSoon ? 'default' : 'outline'}
                  className="h-5 shrink-0 px-1.5 text-[10px] font-medium"
                  title={t('workspace.due', { date: dueDate.title })}
                >
                  {t('workspace.due', { date: dueDate.label })}
                </Badge>
              ) : null}
              {priority === 'HIGH' || priority === 'URGENT' ? (
                <Badge variant={priority === 'URGENT' ? 'destructive' : 'default'} className="h-5 shrink-0 px-1.5 text-[10px] font-medium">
                  {translatePriority(priority, t)}
                </Badge>
              ) : null}
              {assigneeIds.length > 0 ? (
                <div className="flex shrink-0 -space-x-1" aria-label={t('common.assignees')}>
                  {assigneeIds.slice(0, 3).map((assignee) => (
                    <span
                      key={`${assignee.type}-${assignee.id}`}
                      className="grid size-4 place-items-center rounded-full border-2 border-background bg-muted text-[8px] font-medium text-muted-foreground"
                      title={`${assignee.label} (${assignee.type})`}
                    >
                      {avatarInitials(assignee.label)}
                    </span>
                  ))}
                  {assigneeIds.length > 3 ? (
                    <span className="grid size-4 place-items-center rounded-full border-2 border-background bg-muted text-[8px] font-medium text-muted-foreground" title={t('workspace.moreAssignees', { count: assigneeIds.length - 3 })}>
                      +{assigneeIds.length - 3}
                    </span>
                  ) : null}
                </div>
              ) : null}
              {entryCount > 0 ? (
                <Button
                  type="button"
                  size="xs"
                  variant="ghost"
                  className={cn('h-5 shrink-0 gap-1 px-1 text-[11px]', showEntries ? 'bg-primary/10 text-primary' : 'text-muted-foreground')}
                  onClick={() => {
                    if (!showEntries && !isExpanded) {
                      onToggle(node.id)
                    }
                    setShowEntries((current) => !current)
                  }}
                  aria-expanded={showEntries}
                  aria-label={showEntries ? t('workspace.hideUpdates', { count: entryCount }) : t('workspace.showUpdates', { count: entryCount })}
                  title={showEntries ? t('workspace.hideUpdates', { count: entryCount }) : t('workspace.showUpdates', { count: entryCount })}
                >
                  <MessageSquareText className="h-3 w-3" />
                  <span>{entryCount}</span>
                </Button>
              ) : null}
              {blockerCount > 0 ? (
                <BlockerPopover
                  nodeId={node.id}
                  blockerUi={blockerUi}
                  trigger={(
                    <Button
                      type="button"
                      size="xs"
                      variant="outline"
                      className="h-5 shrink-0 gap-1 border-destructive/40 px-1 text-[11px] font-normal text-destructive hover:bg-destructive/10 hover:text-destructive"
                      aria-label={t('workspace.blockersCount', { count: blockerCount })}
                      title={t('workspace.viewBlockers')}
                    >
                      <OctagonAlert className="h-3 w-3" />
                      <span>{blockerCount}</span>
                    </Button>
                  )}
                />
              ) : null}
              {proposal ? (
                <span className="flex shrink-0 items-center gap-1 text-primary" title={proposal.summary}>
                  <Bot className="h-3 w-3" />
                  <span className="font-medium">AI</span>
                  <Badge
                    variant={proposal.action === 'DELETE' ? 'destructive' : proposal.action === 'UPDATE' ? 'secondary' : 'default'}
                    className="h-5 px-1.5 text-[10px]"
                  >
                    {translateProposalAction(proposal.action, t)}
                  </Badge>
                </span>
              ) : null}
            </div>
          </div>
        )}
        {proposal ? (
          <div className="ml-auto flex shrink-0 items-center gap-1">
            {proposal.action === 'DELETE' ? (
              <DeleteConfirmPopover
                title={t('workspace.applyPermanentDeletion')}
                description={t('workspace.deleteSuggestionDescription')}
                disabled={isDecidingProposal}
                trigger={<Button type="button" size="icon-xs" variant="ghost" disabled={isDecidingProposal} aria-label={t('workspace.acceptDeleteProposal')} title={t('workspace.acceptDeleteProposal')}><Check /></Button>}
                onConfirm={() => onDecideProposal(proposal, 'ACCEPT')}
              />
            ) : (
              <Button
                type="button"
                size="icon-xs"
                variant="ghost"
                onClick={() => onDecideProposal(proposal, 'ACCEPT')}
                disabled={isDecidingProposal}
                aria-label={t('workspace.acceptProposal')}
                title={t('workspace.acceptProposal')}
              >
                {isDecidingProposal ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
              </Button>
            )}
            <Button
              type="button"
              size="icon-xs"
              variant="ghost"
              onClick={() => onDecideProposal(proposal, 'REJECT')}
              disabled={isDecidingProposal}
              aria-label={t('workspace.rejectProposal')}
              title={t('workspace.rejectProposal')}
              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
            >
              <X />
            </Button>
          </div>
        ) : !isEditingTitle ? (
          <div
            className={cn(
              'ml-auto flex w-7 shrink-0 items-center justify-end opacity-0 transition-opacity group-focus-within/work-item-row:opacity-100 group-hover/work-item-row:opacity-100',
              isSelected ? 'opacity-100' : 'pointer-events-none group-focus-within/work-item-row:pointer-events-auto group-hover/work-item-row:pointer-events-auto',
            )}
          >
            <DropdownMenu>
              <DropdownMenuTrigger
                render={(
                  <Button
                    type="button"
                    size="icon-xs"
                    variant="ghost"
                    disabled={isSaving}
                    aria-label={t('workspace.actionsFor', { title: node.title })}
                    title={t('workspace.itemActions')}
                  >
                    <MoreHorizontal />
                  </Button>
                )}
              />
              <DropdownMenuContent align="end" className="w-48">
                {canReorder && canMoveUp ? (
                  <DropdownMenuItem onClick={() => onMoveInContentOrder(-1)}>
                    <ArrowUp className="h-4 w-4" /> {t('workspace.moveUp')}
                  </DropdownMenuItem>
                ) : null}
                {canReorder && canMoveDown ? (
                  <DropdownMenuItem onClick={() => onMoveInContentOrder(1)}>
                    <ArrowDown className="h-4 w-4" /> {t('workspace.moveDown')}
                  </DropdownMenuItem>
                ) : null}
                <BlockerPopover
                  nodeId={node.id}
                  blockerUi={blockerUi}
                  trigger={(
                    <DropdownMenuItem>
                      <OctagonAlert className="h-4 w-4" /> {blockerCount > 0 ? t('workspace.manageBlockers') : t('workspace.addBlocker')}
                    </DropdownMenuItem>
                  )}
                />
                <DropdownMenuItem onClick={beginTitleEdit}>
                  <Pencil className="h-4 w-4" /> {t('workspace.editTitle')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onFocus(node)}>
                  <Focus className="h-4 w-4" /> {t('workspace.focus')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onMove(node)}>
                  <MoveRight className="h-4 w-4" /> {t('workspace.move')}
                </DropdownMenuItem>
                <DeleteConfirmPopover
                  title={t('workspace.deleteItem')}
                  description={t('workspace.deleteItemDescription')}
                  trigger={(
                    <DropdownMenuItem variant="destructive" onClick={(event) => event.stopPropagation()}>
                      <Trash2 className="h-4 w-4" /> {t('common.delete')}
                    </DropdownMenuItem>
                  )}
                  onConfirm={() => onDelete(node.id)}
                />
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        ) : null}
      </div>
      {canExpand && isExpanded ? (
        <div
          className={cn(
            'space-y-1 border-l-2 pl-3 transition-colors',
            isSelected ? 'border-primary/50' : 'border-border/80',
          )}
          style={{ marginLeft: `${(depth + 1) * treeDepthIndentPx}px` }}
          data-work-item-children
        >
          {!hasLoadedChildren && isLoadingChildren ? (
            <div className="flex min-h-10 items-center gap-2 px-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              {t('workspace.loadingSubItems')}
            </div>
          ) : null}
          <WorkItemEntries
            content={orderedContent}
            renderChild={(child, contentIndex, totalContent) => (
              <TreeRow
                key={child.id}
                node={child}
                depth={0}
                expandedNodeIds={expandedNodeIds}
                selectedNodeId={selectedNodeId}
                highlightedNodeId={highlightedNodeId}
                selectedContextNodeIds={selectedContextNodeIds}
                dimUnrelatedNodes={dimUnrelatedNodes}
                isSaving={isSaving}
                decidingProposalChangeId={decidingProposalChangeId}
                loadedChildrenParentIds={loadedChildrenParentIds}
                loadingChildrenNodeIds={loadingChildrenNodeIds}
                childrenPageInfoByParentId={childrenPageInfoByParentId}
                blockerUi={blockerUi}
                entriesByWorkItemId={entriesByWorkItemId}
                contentOrderByParent={contentOrderByParent}
                acceptedAnswerByQuestionId={acceptedAnswerByQuestionId}
                userLabels={userLabels}
                teamLabels={teamLabels}
                selectedEntryId={selectedEntryId}
                isAiReviewAvailable={isAiReviewAvailable}
                canReorder={canReorderContent}
                canMoveUp={contentIndex > 0}
                canMoveDown={contentIndex < totalContent - 1}
                onToggle={onToggle}
                onLoadMoreChildren={onLoadMoreChildren}
                onSelect={onSelect}
                onSelectEntry={onSelectEntry}
                onAddChild={onAddChild}
                onFocus={onFocus}
                onMove={onMove}
                onDelete={onDelete}
                onEditTitle={onEditTitle}
                onCreateEntry={onCreateEntry}
                onReviewNewEntry={onReviewNewEntry}
                onAcceptNewEntryReview={onAcceptNewEntryReview}
                onRejectNewEntryReview={onRejectNewEntryReview}
                onUpdateEntry={onUpdateEntry}
                onReviewEntry={onReviewEntry}
                onAcceptEntryReview={onAcceptEntryReview}
                onRejectEntryReview={onRejectEntryReview}
                onAcceptAnswer={onAcceptAnswer}
                onMoveContentOrder={onMoveContentOrder}
                onMoveInContentOrder={(offset) => { void onMoveContentOrder(node.id, 'WORK_ITEM', child.id, offset) }}
                onDeleteEntry={onDeleteEntry}
                autoEditTitleNodeId={autoEditTitleNodeId}
                onAutoEditTitleStarted={onAutoEditTitleStarted}
                onDecideProposal={onDecideProposal}
              />
            )}
            canReorder={canReorderContent && !isSaving}
            highlightConnectors={isSelected}
            showEntries={showEntries}
            selectedEntryId={selectedEntryId}
            startAdding={entryComposerRequested}
            defaultType={node.type.toUpperCase() === 'QUESTION' ? 'ANSWER' : 'COMMENT'}
            composerPlaceholder={node.type.toUpperCase() === 'QUESTION' ? t('workspace.writeAnswer') : t('workspace.writeUpdate')}
            isQuestion={node.type.toUpperCase() === 'QUESTION'}
            acceptedAnswerEntryId={acceptedAnswerByQuestionId.get(node.id) ?? null}
            isAiReviewAvailable={isAiReviewAvailable}
            onEntryComposerOpened={() => setEntryComposerRequested(false)}
            onSelect={onSelectEntry}
            onCreate={(type, body) => onCreateEntry(node.id, type, body)}
            onReviewNew={(type, body, instruction) => onReviewNewEntry(node.id, type, body, instruction)}
            onAcceptNewReview={(type, review) => onAcceptNewEntryReview(node.id, type, review)}
            onRejectNewReview={(type, review) => onRejectNewEntryReview(node.id, type, review)}
            onUpdate={onUpdateEntry}
            onReview={onReviewEntry}
            onAcceptReview={onAcceptEntryReview}
            onRejectReview={onRejectEntryReview}
            onAcceptAnswer={(entryId) => onAcceptAnswer(node.id, entryId)}
            onMoveContent={(entityType, entityId, offset) => onMoveContentOrder(node.id, entityType, entityId, offset)}
            onDelete={onDeleteEntry}
          />
          {hasMoreChildren ? (
            <div className="py-1">
              <Button
                type="button"
                size="xs"
                variant="ghost"
                onClick={() => onLoadMoreChildren(node.id)}
                disabled={isLoadingChildren}
              >
                {isLoadingChildren ? <Loader2 className="h-3 w-3 animate-spin" /> : null}
                {t('workspace.loadMoreSubItems')}
              </Button>
            </div>
          ) : null}
          {isSelected ? <div>
            <DropdownMenu>
              <DropdownMenuTrigger
                render={(
                  <Button type="button" size="xs" variant="ghost" className="gap-1 text-muted-foreground" disabled={isSaving}>
                    <Plus className="h-3.5 w-3.5" /> Add
                  </Button>
                )}
              />
              <DropdownMenuContent align="start" className="w-52">
                <DropdownMenuItem onClick={() => {
                  if (!isExpanded) {
                    onToggle(node.id)
                  }
                  setShowEntries(true)
                  setEntryComposerRequested(true)
                }}>
                    <MessageSquarePlus className="h-4 w-4" /> {node.type.toUpperCase() === 'QUESTION' ? t('workspace.writeAnswer') : t('workspace.writeUpdate')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onAddChild(node.id, 'TASK')}>
                  <ListTodo className="h-4 w-4" /> {t('workspace.addTask')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onAddChild(node.id, 'QUESTION')}>
                  <CircleHelp className="h-4 w-4" /> {t('workspace.askQuestion')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onAddChild(node.id, 'APPROVAL')}>
                  <ClipboardCheck className="h-4 w-4" /> {t('workspace.requestApproval')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div> : null}
        </div>
      ) : null}
    </div>
  )
}

const TreeRow = memo(TreeRowContent)

function PendingProposalNodeRow({
  node,
  selectedNodeId,
  decidingProposalChangeId,
  onSelect,
  onDecideProposal,
}: {
  node: PendingProposalNode
  selectedNodeId: string | null
  decidingProposalChangeId: string | null
  onSelect: (node: ProjectNode) => void
  onDecideProposal: (proposal: TreeNodeProposal, decision: 'ACCEPT' | 'REJECT') => void
}) {
  const { t } = useTranslation()
  const isSelected = selectedNodeId === node.id
  const isDecidingProposal = decidingProposalChangeId === node.proposal.changeId

  return (
    <div
      className={cn(
        'group flex min-h-11 items-center gap-2 rounded-md border bg-background px-2 py-1.5 text-sm transition-colors hover:bg-muted/40',
        isSelected ? 'border-foreground/30 bg-muted/40 text-foreground shadow-[inset_0_0_0_1px_var(--foreground)]/15' : 'border-primary/30 text-muted-foreground',
      )}
    >
      <FileText className="h-4 w-4 shrink-0" />
      <button type="button" className="min-w-0 flex-1 text-left" onClick={() => onSelect(node)} title={node.title}>
        <div className="flex min-w-0 items-center gap-2">
          <Badge variant="outline" className={cn('shrink-0 font-medium uppercase', workItemTypeBadgeClass(node.type))}>
            {translateWorkItemType(node.type, t)}
          </Badge>
          <span
            className={cn(
              'min-w-0 truncate text-[15px] font-medium text-foreground',
              node.proposal.action === 'DELETE' ? 'line-through decoration-destructive decoration-2' : null,
            )}
          >
            {node.title}
          </span>
        </div>
        <div className="mt-1 flex min-w-0 items-center gap-1.5 overflow-hidden text-xs text-primary">
          <Bot className="h-3.5 w-3.5 shrink-0" />
          <span className="shrink-0 font-medium">{t('workspace.aiProposed')}</span>
          <Badge
            variant={node.proposal.action === 'DELETE' ? 'destructive' : node.proposal.action === 'UPDATE' ? 'secondary' : 'default'}
            className="h-5 shrink-0 px-1.5 text-[10px]"
          >
            {translateProposalAction(node.proposal.action, t)}
          </Badge>
          <span className="min-w-0 truncate text-muted-foreground" title={node.proposal.summary}>{node.proposal.summary}</span>
        </div>
        <div className="mt-0.5 truncate text-xs text-muted-foreground" title={node.placementReason}>
          {node.placementReason}
        </div>
      </button>
      {node.proposal.action === 'DELETE' ? (
        <DeleteConfirmPopover
          title={t('workspace.applyPermanentDeletion')}
          description={t('workspace.deleteSuggestionDescription')}
          disabled={isDecidingProposal}
          trigger={<Button type="button" size="icon-xs" variant="ghost" disabled={isDecidingProposal} aria-label={t('workspace.acceptDeleteProposal')} title={t('workspace.acceptDeleteProposal')}><Check /></Button>}
          onConfirm={() => onDecideProposal(node.proposal, 'ACCEPT')}
        />
      ) : (
        <Button
          type="button"
          size="icon-xs"
          variant="ghost"
          onClick={() => onDecideProposal(node.proposal, 'ACCEPT')}
          disabled={isDecidingProposal}
          aria-label={t('workspace.acceptProposal')}
          title={t('workspace.acceptProposal')}
        >
          {isDecidingProposal ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check />}
        </Button>
      )}
      <Button
        type="button"
        size="icon-xs"
        variant="ghost"
        onClick={() => onDecideProposal(node.proposal, 'REJECT')}
        disabled={isDecidingProposal}
        aria-label={t('workspace.rejectProposal')}
        title={t('workspace.rejectProposal')}
        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
      >
        <X />
      </Button>
    </div>
  )
}

function ProposalUpdateDiff({ node }: { node: TreeNode }) {
  const { t } = useTranslation()
  if (node.proposal?.action !== 'UPDATE') {
    return null
  }

  const previousNode = node.proposal.previousNode ?? node
  const proposedNode = node.proposal.proposedNode

  if (!proposedNode) {
    return (
      <div className="rounded-md border bg-muted/20 p-3">
        <div className="flex items-center justify-between gap-2">
          <div>
            <p className="text-sm font-medium">{t('workspace.proposedUpdate')}</p>
            <p className="text-xs text-muted-foreground">{t('workspace.proposedValuesUnavailable')}</p>
          </div>
          <Badge variant="secondary">{t('workspace.updateAction')}</Badge>
        </div>
      </div>
    )
  }

  const fieldDiffs = buildProposalFieldDiffs(previousNode.fields ?? [], proposedNode.fields ?? [], t)
  const parentChanged = (previousNode.parentNodeId ?? null) !== (proposedNode.parentNodeId ?? null)
  const summaryRows = [
    previousNode.type !== proposedNode.type
      ? { label: t('common.type'), previousValue: translateWorkItemType(previousNode.type, t), nextValue: translateWorkItemType(proposedNode.type, t) }
      : null,
    previousNode.title !== proposedNode.title
      ? { label: t('common.title'), previousValue: previousNode.title, nextValue: proposedNode.title }
      : null,
    parentChanged
      ? {
        label: t('workspace.parent'),
        previousValue: previousNode.parentNodeId ? t('workspace.nestedItem') : t('workspace.projectLevel'),
        nextValue: proposedNode.parentNodeId ? t('workspace.nestedItem') : t('workspace.projectLevel'),
      }
      : null,
  ].filter((row): row is { label: string; previousValue: string; nextValue: string } => Boolean(row))

  return (
    <div className="rounded-md border bg-muted/20 p-3">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium">{t('workspace.proposedUpdate')}</p>
          <p className="text-xs text-muted-foreground">{t('workspace.reviewOldNewValues')}</p>
        </div>
        <Badge variant="secondary">{t('workspace.updateAction')}</Badge>
      </div>

      {summaryRows.length > 0 ? (
        <div className="space-y-2">
          {summaryRows.map((row) => (
            <div key={row.label} className="grid gap-2 rounded-md border bg-background p-2 text-sm sm:grid-cols-[90px_minmax(0,1fr)_minmax(0,1fr)]">
              <div className="font-medium text-muted-foreground">{row.label}</div>
              <div className="min-w-0">
                <div className="text-xs text-muted-foreground">{t('workspace.old')}</div>
                <div className="break-words">{row.previousValue}</div>
              </div>
              <div className="min-w-0">
                <div className="text-xs text-muted-foreground">{t('workspace.new')}</div>
                <div className="break-words font-medium text-foreground">{row.nextValue}</div>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {fieldDiffs.length > 0 ? (
        <div className="mt-3 space-y-2">
          <p className="text-xs font-medium uppercase text-muted-foreground">{t('workspace.fields')}</p>
          {fieldDiffs.map((diff) => (
            <div key={diff.name} className="grid gap-2 rounded-md border bg-background p-2 text-sm sm:grid-cols-[90px_minmax(0,1fr)_minmax(0,1fr)]">
              <div className="min-w-0">
                <div className="font-medium text-muted-foreground">{diff.label}</div>
                <Badge
                  variant={diff.status === 'removed' ? 'destructive' : diff.status === 'added' ? 'default' : 'secondary'}
                  className="mt-1"
                >
                  {diff.status === 'added' ? t('workspace.added') : diff.status === 'removed' ? t('workspace.removed') : t('workspace.changed')}
                </Badge>
              </div>
              <div className="min-w-0">
                <div className="text-xs text-muted-foreground">{t('workspace.old')}</div>
                <div className="break-words">{diff.previousValue}</div>
              </div>
              <div className="min-w-0">
                <div className="text-xs text-muted-foreground">{t('workspace.new')}</div>
                <div className="break-words font-medium text-foreground">{diff.nextValue}</div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-3 rounded-md border bg-background p-2 text-sm text-muted-foreground">
          {t('workspace.noChangedFields')}
        </div>
      )}
    </div>
  )
}

function ProposalAddDetails({ node, nodeTitleById }: { node: TreeNode; nodeTitleById: Map<string, string> }) {
  const { t } = useTranslation()
  const rows = [
    { label: t('common.title'), value: node.title },
    { label: t('common.type'), value: translateWorkItemType(node.type, t) },
    { label: t('common.status'), value: nodeFieldValue(node, 'status') ? translateStatus(String(nodeFieldValue(node, 'status')), t) : t('common.notSet') },
    { label: t('common.dueDate'), value: formatProposalValue(nodeFieldValue(node, 'dueDate'), t) },
    { label: t('common.priority'), value: nodeFieldValue(node, 'priority') ? translatePriority(String(nodeFieldValue(node, 'priority')), t) : t('common.notSet') },
    { label: t('common.assignees'), value: formatProposalValue([
      ...parseStringArrayValue(nodeFieldValue(node, 'assigneeUserIds')),
      ...parseStringArrayValue(nodeFieldValue(node, 'assigneeTeamIds')),
    ], t) },
    { label: t('workspace.parent'), value: node.parentNodeId ? nodeTitleById.get(node.parentNodeId) ?? t('workspace.existingWorkItem') : t('workspace.projectRoot') },
  ]

  return (
    <div className="rounded-md border bg-muted/20 p-3">
      <div className="mb-3">
        <p className="text-sm font-medium">{t('workspace.proposedNewWorkItem')}</p>
        <p className="text-xs text-muted-foreground">{t('workspace.reviewDetailsBeforeAdding')}</p>
      </div>
      <div className="grid gap-2 text-sm sm:grid-cols-2">
        {rows.map((row) => (
          <div key={row.label} className="min-w-0 rounded-md border bg-background p-2">
            <div className="text-xs font-medium text-muted-foreground">{row.label}</div>
            <div className="mt-0.5 break-words font-medium">{row.value}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

function WorkspaceProposalPanel({
  node,
  changes,
  isDeciding,
  nodeTitleById,
  onDecide,
}: {
  node: TreeNode
  changes: Array<{ proposalId: string; change: GraphChangeProposalChange }>
  isDeciding: boolean
  nodeTitleById: Map<string, string>
  onDecide: (changes: Array<{ proposalId: string; change: GraphChangeProposalChange }>, decision: 'ACCEPT' | 'REJECT') => void
}) {
  const { t } = useTranslation()
  if (changes.length === 0) return null
  const entryChanges = changes.filter(({ change }) => change.entityType === 'ENTRY')
  const relationshipChanges = changes.filter(({ change }) => change.entityType === 'EDGE')
  const otherNodeChanges = changes.filter(({ change }) => change.entityType === 'NODE' && change.action !== 'UPDATE')
  const containsDelete = changes.some(({ change }) => change.action === 'DELETE')
  const acceptButton = (
    <Button type="button" size="sm" disabled={isDeciding} onClick={containsDelete ? undefined : () => onDecide(changes, 'ACCEPT')}>
      {isDeciding ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
      {t('workspace.acceptSuggestionButton')}
    </Button>
  )

  return (
    <section className="space-y-3 border-b bg-primary/5 px-4 py-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-primary"><Bot className="h-4 w-4" /> {t('workspace.aiSuggestion')}</div>
        <Badge variant="secondary">{changes.length} {changes.length === 1 ? t('workspace.change') : t('workspace.changes', { count: changes.length })}</Badge>
      </div>

      {node.proposal?.action === 'ADD' ? <ProposalAddDetails node={node} nodeTitleById={nodeTitleById} /> : null}
      {node.proposal?.action === 'UPDATE' ? <ProposalUpdateDiff node={node} /> : null}

      {otherNodeChanges.map(({ change }) => (
        <div key={change.id} className="flex items-start gap-2 rounded-md border bg-background p-2 text-sm">
          <Badge variant={change.action === 'DELETE' ? 'destructive' : 'default'}>{translateProposalAction(change.action, t)}</Badge>
          <span className={cn('break-words', change.action === 'DELETE' && 'line-through')}>{change.summary}</span>
        </div>
      ))}

      {entryChanges.length > 0 ? (
        <div className="space-y-2">
          <p className="text-xs font-medium uppercase text-muted-foreground">{t('workspace.entries')}</p>
          {entryChanges.map(({ change }) => (
            <div key={change.id} className="space-y-2 rounded-md border bg-background p-2 text-sm">
              <div className="flex items-center gap-2"><Badge variant={change.action === 'DELETE' ? 'destructive' : 'secondary'}>{translateProposalAction(change.action, t)}</Badge><span>{change.summary}</span></div>
              {change.previousEntry && change.action === 'UPDATE' ? (
                <div className="grid gap-2 sm:grid-cols-2">
                  <div><div className="text-xs text-muted-foreground">{t('workspace.old')}</div><p className="whitespace-pre-wrap break-words">{change.previousEntry.body}</p></div>
                  <div><div className="text-xs text-muted-foreground">{t('workspace.new')}</div><p className="whitespace-pre-wrap break-words font-medium">{change.entry?.body}</p></div>
                </div>
              ) : <p className="whitespace-pre-wrap break-words">{(change.entry ?? change.previousEntry)?.body}</p>}
            </div>
          ))}
        </div>
      ) : null}

      {relationshipChanges.length > 0 ? (
        <div className="space-y-2">
          <p className="text-xs font-medium uppercase text-muted-foreground">{t('common.relationship')}</p>
          {relationshipChanges.map(({ change }) => {
            const relationship = change.relationship ?? change.previousRelationship
            const endpointLabel = (type: string, id: string) => type === 'WORK_ITEM' ? (nodeTitleById.get(id) ?? t('common.workItem')) : t('common.entry')
            return (
              <div key={change.id} className="flex items-start gap-2 rounded-md border bg-background p-2 text-sm">
                <Badge variant={change.action === 'DELETE' ? 'destructive' : 'secondary'}>{translateProposalAction(change.action, t)}</Badge>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {relationship ? <Badge variant="outline" className={cn('font-medium', relationshipTypeBadgeClass(relationship.type))}>{translateRelationshipType(relationship.type, t)}</Badge> : null}
                    <span className="break-words font-medium">{change.summary}</span>
                  </div>
                  {relationship ? <div className="mt-1 break-words text-xs text-muted-foreground">{endpointLabel(relationship.fromEntityType, relationship.fromEntityId)} → {endpointLabel(relationship.toEntityType, relationship.toEntityId)}</div> : null}
                  {relationship?.reason ? <div className="mt-0.5 break-words text-xs text-muted-foreground">{relationship.reason}</div> : null}
                </div>
              </div>
            )
          })}
        </div>
      ) : null}

      <div className="flex gap-2">
        {containsDelete ? (
          <DeleteConfirmPopover
            title={t('workspace.applyPermanentDeletion')}
            description={t('workspace.deleteSuggestionRecords')}
            disabled={isDeciding}
            trigger={acceptButton}
            onConfirm={() => onDecide(changes, 'ACCEPT')}
          />
        ) : acceptButton}
        <Button type="button" size="sm" variant="outline" disabled={isDeciding} onClick={() => onDecide(changes, 'REJECT')}>{t('workspace.rejectSuggestion')}</Button>
      </div>
    </section>
  )
}

type ProjectWorkspacePageProps = {
  currentUser: AuthUser | null
}

export default function ProjectWorkspacePage({ currentUser }: ProjectWorkspacePageProps) {
  const { t } = useTranslation()
  const location = useLocation()
  const { projectId } = useParams()
  const [searchParams] = useSearchParams()
  const { artifactRefreshKey } = useOutletContext<ProjectWorkspaceOutletContext>()
  const requestedWorkItemId = searchParams.get('workItemId')?.trim() || null
  const [project, setProject] = useState<Project | null>(null)
  const [workspacePanelLayout] = useState(readWorkspacePanelLayout)
  const inspectorPanelRef = usePanelRef()
  const [isInspectorCollapsed, setIsInspectorCollapsed] = useState(readWorkspaceInspectorCollapsed)
  const [nodes, setNodes] = useState<ProjectNode[]>([])
  const [entries, setEntries] = useState<Entry[]>([])
  const [relationships, setRelationships] = useState<Relationship[]>([])
  const [graphChangeProposals, setGraphChangeProposals] = useState<GraphChangeProposal[]>([])
  const [referenceUsers, setReferenceUsers] = useState<User[]>([])
  const [referenceTeams, setReferenceTeams] = useState<Team[]>([])
  const [projectMemberUserIds, setProjectMemberUserIds] = useState<Set<string>>(() => new Set())
  const [projectTeamIds, setProjectTeamIds] = useState<Set<string>>(() => new Set())
  const [newAssigneeType, setNewAssigneeType] = useState<'USER' | 'TEAM'>('USER')
  const [newAssigneeId, setNewAssigneeId] = useState('')
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [selectedEntryId, setSelectedEntryId] = useState<string | null>(null)
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(() => new Set())
  const [loadedChildrenParentIds, setLoadedChildrenParentIds] = useState<Set<string>>(() => new Set())
  const [loadingChildrenNodeIds, setLoadingChildrenNodeIds] = useState<Set<string>>(() => new Set())
  const [isExpandingSelectedSubtree, setIsExpandingSelectedSubtree] = useState(false)
  const [rootPageInfo, setRootPageInfo] = useState<NodePageInfo | null>(null)
  const [childrenPageInfoByParentId, setChildrenPageInfoByParentId] = useState<Map<string, NodePageInfo>>(() => new Map())
  const [isLoadingMoreRoots, setIsLoadingMoreRoots] = useState(false)
  const [form, setForm] = useState<NodeFormState>(newNodeDefaults)
  const [entryInspectorType, setEntryInspectorType] = useState('COMMENT')
  const [entryInspectorBody, setEntryInspectorBody] = useState('')
  const [entryInspectorReview, setEntryInspectorReview] = useState<EntryAiReview | null>(null)
  const [entryInspectorReviewFeedback, setEntryInspectorReviewFeedback] = useState('')
  const [workItemInspectorReview, setWorkItemInspectorReview] = useState<WorkItemAiReview | null>(null)
  const [workItemInspectorReviewFeedback, setWorkItemInspectorReviewFeedback] = useState('')
  const [isMoveDialogOpen, setIsMoveDialogOpen] = useState(false)
  const [moveTargetContentKey, setMoveTargetContentKey] = useState('')
  const [moveQuery, setMoveQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [decidingProposalChangeId, setDecidingProposalChangeId] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isLlmAvailable, setIsLlmAvailable] = useState(false)
  const [subscribedWorkItemIds, setSubscribedWorkItemIds] = useState<ReadonlySet<string>>(new Set())
  const [workItemSearchQuery, setWorkItemSearchQuery] = useState('')
  const [appliedWorkItemSearchQuery, setAppliedWorkItemSearchQuery] = useState('')
  const [workItemFilterConditions, setWorkItemFilterConditions] = useState<WorkItemFilterCondition[]>([])
  const [appliedWorkItemFilterConditions, setAppliedWorkItemFilterConditions] = useState<WorkItemFilterCondition[]>([])
  const [isRunningWorkItemFilters, setIsRunningWorkItemFilters] = useState(false)
  const [createdSortDirection, setCreatedSortDirection] = useState<CreatedSortDirection>(null)
  const [isRunningCreatedSort, setIsRunningCreatedSort] = useState(false)
  const [focusedNodeId, setFocusedNodeId] = useState<string | null>(null)
  const [chatHighlightedNodeId, setChatHighlightedNodeId] = useState<string | null>(null)
  const chatHighlightTimerRef = useRef<number | null>(null)
  const [inspectorMode, setInspectorMode] = useState<InspectorMode>('task')
  const [autoEditTitleNodeId, setAutoEditTitleNodeId] = useState<string | null>(null)
  const [blockerWorkItems, setBlockerWorkItems] = useState<WorkItem[]>([])
  const [isSavingBlocker, setIsSavingBlocker] = useState(false)
  const nodesRef = useRef(nodes)
  nodesRef.current = nodes

  useEffect(() => () => {
    if (chatHighlightTimerRef.current !== null) {
      window.clearTimeout(chatHighlightTimerRef.current)
    }
  }, [])

  useEffect(() => {
    const panel = inspectorPanelRef.current
    if (!panel) {
      return
    }
    if (isInspectorCollapsed && !panel.isCollapsed()) {
      panel.collapse()
    } else if (!isInspectorCollapsed && panel.isCollapsed()) {
      panel.expand()
    }
  }, [inspectorPanelRef, isInspectorCollapsed])

  useEffect(() => {
    if (!chatHighlightedNodeId) {
      return
    }
    const scrollTimer = window.setTimeout(() => {
      const row = [...document.querySelectorAll<HTMLElement>('[data-work-item-id]')]
        .find((element) => element.dataset.workItemId === chatHighlightedNodeId)
      row?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 0)
    return () => window.clearTimeout(scrollTimer)
  }, [chatHighlightedNodeId])

  const displayedNodes = useMemo(() => mergeProposalNodes(nodes, graphChangeProposals), [nodes, graphChangeProposals])
  const pendingProposalItems = useMemo(() => pendingProposalNodes(nodes, graphChangeProposals, t), [nodes, graphChangeProposals, t])
  const contentOrderByParent = useMemo(() => groupContentOrderByParent(displayedNodes, entries), [displayedNodes, entries])
  const tree = useMemo(() => {
    const builtTree = buildTree(displayedNodes, createdSortDirection)
    return createdSortDirection ? builtTree : sortTreeByContentOrder(builtTree, contentOrderByParent)
  }, [createdSortDirection, displayedNodes, contentOrderByParent])
  const focusedNodePath = useMemo(() => findTreeNodePath(tree, focusedNodeId), [focusedNodeId, tree])
  const focusedNode = focusedNodePath.at(-1) ?? null
  const focusedTree = useMemo(() => focusedNode ? [focusedNode] : tree, [focusedNode, tree])
  const filteredTree = useMemo(
    () => filterTreeByWorkItemFilters(focusedTree, appliedWorkItemFilterConditions),
    [focusedTree, appliedWorkItemFilterConditions],
  )
  const filteredPendingProposalItems = useMemo(
    () => focusedNode ? [] : pendingProposalItems,
    [focusedNode, pendingProposalItems],
  )
  const displayedExpandedNodeIds = useMemo(
    () => appliedWorkItemSearchQuery || appliedWorkItemFilterConditions.length > 0 ? collectExpandableTreeNodeIds(filteredTree) : expandedNodeIds,
    [appliedWorkItemFilterConditions.length, appliedWorkItemSearchQuery, expandedNodeIds, filteredTree],
  )
  const selectedNode = useMemo(
    () => findTreeNode(tree, selectedNodeId) ?? pendingProposalItems.find((node) => node.id === selectedNodeId) ?? null,
    [pendingProposalItems, tree, selectedNodeId],
  )
  const selectedEntry = useMemo(
    () => entries.find((entry) => entry.id === selectedEntryId) ?? null,
    [entries, selectedEntryId],
  )
  const selectedWorkspaceProposalChanges = useMemo(
    () => selectedNode ? openWorkspaceChangesForNode(graphChangeProposals, selectedNode.id) : [],
    [graphChangeProposals, selectedNode],
  )
  const hasUnsavedWorkItemChanges = useMemo(
    () => Boolean(selectedNode && !selectedNode.proposal && formFingerprint(form) !== formFingerprint(createFormState(selectedNode))),
    [form, selectedNode],
  )
  const flattenedTree = useMemo(() => flattenTree(tree), [tree])
  const nodeTitleById = useMemo(() => new Map(displayedNodes.map((node) => [node.id, node.title])), [displayedNodes])
  const entriesByWorkItemId = useMemo(() => {
    const result = new Map<string, Entry[]>()
    entries.forEach((entry) => result.set(entry.workItemId, [...(result.get(entry.workItemId) ?? []), entry]))
    return result
  }, [entries])
  const acceptedAnswerByQuestionId = useMemo(() => new Map(
    relationships
      .filter((relationship) => relationship.type === 'ACCEPTED_ANSWER' && relationship.fromEntityType === 'WORK_ITEM' && relationship.toEntityType === 'ENTRY')
      .map((relationship) => [relationship.fromEntityId, relationship.toEntityId]),
  ), [relationships])
  const blockedByRelationshipsByNodeId = useMemo(() => {
    const result = new Map<string, Relationship[]>()
    relationships
      .filter((relationship) => relationship.type === 'BLOCKED_BY' && relationship.fromEntityType === 'WORK_ITEM' && relationship.toEntityType === 'WORK_ITEM')
      .forEach((relationship) => result.set(relationship.fromEntityId, [...(result.get(relationship.fromEntityId) ?? []), relationship]))
    return result
  }, [relationships])
  const workItemRelationshipsByNodeId = useMemo(() => {
    const result = new Map<string, Relationship[]>()
    relationships
      .filter((relationship) => relationship.type !== 'ACCEPTED_ANSWER')
      .filter((relationship) => relationship.fromEntityType === 'WORK_ITEM' && relationship.toEntityType === 'WORK_ITEM')
      .forEach((relationship) => {
        result.set(relationship.fromEntityId, [...(result.get(relationship.fromEntityId) ?? []), relationship])
        result.set(relationship.toEntityId, [...(result.get(relationship.toEntityId) ?? []), relationship])
      })
    return result
  }, [relationships])
  const blockerTitleById = useMemo(() => new Map([
    ...blockerWorkItems.map((item) => [item.id, item.title] as const),
    ...displayedNodes.map((node) => [node.id, node.title] as const),
  ]), [blockerWorkItems, displayedNodes])
  const userLabels = useMemo(() => new Map(referenceUsers.map((user) => [user.id, referenceUserLabel(user)])), [referenceUsers])
  const teamLabels = useMemo(() => new Map(referenceTeams.map((team) => [team.id, referenceTeamLabel(team)])), [referenceTeams])
  const referenceUserOptions = useMemo(() => {
    const options = referenceUsers.map((user) => ({ id: user.id, label: referenceUserLabel(user) }))
    if (currentUser && projectMemberUserIds.has(currentUser.id) && !options.some((user) => user.id === currentUser.id)) {
      options.unshift({
        id: currentUser.id,
        label: currentUser.displayName?.trim() || currentUser.username || currentUser.email || 'Unnamed user',
      })
    }
    return options
  }, [currentUser, projectMemberUserIds, referenceUsers])
  const referenceTeamOptions = useMemo(() => (
    referenceTeams.map((team) => ({ id: team.id, label: referenceTeamLabel(team) }))
  ), [referenceTeams])
  const assigneeUserOptions = useMemo(() => (
    referenceUserOptions.filter((user) => projectMemberUserIds.has(user.id))
  ), [projectMemberUserIds, referenceUserOptions])
  const assigneeTeamOptions = useMemo(() => (
    referenceTeamOptions.filter((team) => projectTeamIds.has(team.id))
  ), [projectTeamIds, referenceTeamOptions])
  const assignedUserIds = useMemo(
    () => parseStringArrayValue(formFieldValue(form.fields, 'assigneeUserIds')),
    [form.fields],
  )
  const assignedTeamIds = useMemo(
    () => parseStringArrayValue(formFieldValue(form.fields, 'assigneeTeamIds')),
    [form.fields],
  )
  const availableAssigneeOptions = useMemo(() => {
    const assignedIds = new Set(newAssigneeType === 'USER' ? assignedUserIds : assignedTeamIds)
    const options = newAssigneeType === 'USER' ? assigneeUserOptions : assigneeTeamOptions
    return options.filter((option) => !assignedIds.has(option.id))
  }, [assignedTeamIds, assignedUserIds, assigneeTeamOptions, assigneeUserOptions, newAssigneeType])
  const hasActiveWorkItemFilters = appliedWorkItemFilterConditions.length > 0 || Boolean(appliedWorkItemSearchQuery)
  const selectedTreeNode = useMemo(() => findTreeNode(tree, selectedNodeId), [selectedNodeId, tree])
  const selectedContextNodeIds = useMemo(() => {
    if (!selectedTreeNode) {
      return new Set<string>()
    }
    const contextIds = new Set(findTreeNodePath(tree, selectedTreeNode.id).map((node) => node.id))
    collectDescendantIds(selectedTreeNode.id, tree).forEach((nodeId) => contextIds.add(nodeId))
    return contextIds
  }, [selectedTreeNode, tree])
  const dimUnrelatedNodes = Boolean(selectedTreeNode && !hasActiveWorkItemFilters && !focusedNode)
  const hasDraftWorkItemFilters = workItemFilterConditions.length > 0 || Boolean(workItemSearchQuery.trim())
  const hasVisibleWorkItemProposals = displayedNodes.some((node) => Boolean((node as ProjectNode & { proposal?: TreeNodeProposal }).proposal))
  const areRootItemsFullyLoaded = Boolean(rootPageInfo && rootPageInfo.page + 1 >= rootPageInfo.totalPages && !isLoadingMoreRoots)
  const canReorderContent = !hasActiveWorkItemFilters && !createdSortDirection && !focusedNode && !hasVisibleWorkItemProposals && areRootItemsFullyLoaded
  const canReorderSelectedContent = Boolean(selectedNode && canReorderContent && (
    selectedNode.parentNodeId == null
      ? areRootItemsFullyLoaded
      : loadedChildrenParentIds.has(selectedNode.parentNodeId)
        && !loadingChildrenNodeIds.has(selectedNode.parentNodeId)
        && Boolean(childrenPageInfoByParentId.get(selectedNode.parentNodeId))
        && (childrenPageInfoByParentId.get(selectedNode.parentNodeId)?.page ?? -1) + 1 >= (childrenPageInfoByParentId.get(selectedNode.parentNodeId)?.totalPages ?? 0)
  ))
  const assigneeUserLabelById = useMemo(() => new Map(assigneeUserOptions.map((user) => [user.id, user.label])), [assigneeUserOptions])
  const assigneeTeamLabelById = useMemo(() => new Map(assigneeTeamOptions.map((team) => [team.id, team.label])), [assigneeTeamOptions])
  const invalidMoveDestinationIds = useMemo(
    () => new Set(selectedNodeId ? collectDescendantIds(selectedNodeId, tree) : []),
    [selectedNodeId, tree],
  )
  const moveTargetOptions = useMemo(() => {
    const normalizedQuery = moveQuery.trim().toLowerCase()
    const workItems = flattenedTree
      .filter((node) => !invalidMoveDestinationIds.has(node.id))
      .map((node) => ({ entityType: 'WORK_ITEM' as const, entityId: node.id, parentWorkItemId: node.parentNodeId ?? null, label: node.title, detail: translateWorkItemType(node.type, t), depth: node.depth }))
    const updates = entries
      .filter((entry) => !invalidMoveDestinationIds.has(entry.workItemId))
      .map((entry) => ({ entityType: 'ENTRY' as const, entityId: entry.id, parentWorkItemId: entry.workItemId, label: entry.body.trim().replace(/\s+/g, ' ').slice(0, 100) || t('workspace.untitledUpdate'), detail: t('workspace.update'), depth: (flattenedTree.find((node) => node.id === entry.workItemId)?.depth ?? 0) + 1 }))
    return [...workItems, ...updates].filter((item) => !normalizedQuery || [item.label, item.detail, item.entityId].some((value) => value.toLowerCase().includes(normalizedQuery)))
  }, [entries, flattenedTree, invalidMoveDestinationIds, moveQuery])
  const handleInlineTitleUpdate = useCallback(async (node: ProjectNode, title: string) => {
    if (!projectId) {
      throw new Error('Project is unavailable.')
    }

    const updated = await updateNode(projectId, node.id, {
      projectId,
      parentNodeId: node.parentNodeId ?? null,
      sortIndex: node.sortIndex ?? 0,
      type: node.type,
      title,
      fields: node.fields ?? [],
    })

    setNodes((current) => current.map((currentNode) => (
      currentNode.id === updated.id
        ? { ...updated, childrenCount: currentNode.childrenCount ?? updated.childrenCount }
        : currentNode
    )))
  }, [projectId])
  const handleCreateEntry = useCallback(async (workItemId: string, type: string, body: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const created = await createEntry(projectId, { workItemId, type, body })
    setEntries((current) => [...current, created])
    setSelectedEntryId(created.id)
    setSelectedNodeId(null)
    setInspectorMode('task')
  }, [projectId])
  const handleReviewNewEntryWithAi = useCallback(async (workItemId: string, type: string, body: string, instruction?: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    return reviewNewEntryWithAi(projectId, { workItemId, type, body }, instruction)
  }, [projectId])
  const handleAcceptNewEntryAiReview = useCallback(async (workItemId: string, type: string, review: EntryAiReview) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const created = await acceptNewEntryAiReview(projectId, { workItemId, type }, review.originalBody, review.proposedBody)
    setEntries((current) => [...current, created])
    setSelectedEntryId(created.id)
    setSelectedNodeId(null)
    setInspectorMode('task')
  }, [projectId])
  const handleRejectNewEntryAiReview = useCallback(async (workItemId: string, type: string, review: EntryAiReview) => {
    if (!projectId) throw new Error('Project is unavailable.')
    await rejectNewEntryAiReview(projectId, { workItemId, type }, review.originalBody, review.proposedBody)
  }, [projectId])
  const handleUpdateEntry = useCallback(async (entry: Entry, type: string, body: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const updated = await updateEntry(projectId, entry.id, { workItemId: entry.workItemId, type, body })
    setEntries((current) => current.map((currentEntry) => currentEntry.id === updated.id ? updated : currentEntry))
    setSelectedEntryId(updated.id)
    setSelectedNodeId(null)
    setInspectorMode('task')
  }, [projectId])
  const handleReviewEntryWithAi = useCallback(async (entry: Entry, type: string, body: string, instruction?: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    return reviewEntryWithAi(projectId, entry.id, body, type, instruction)
  }, [projectId])
  const handleAcceptEntryAiReview = useCallback(async (entry: Entry, review: EntryAiReview) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const updated = await acceptEntryAiReview(projectId, entry.id, review.originalBody, review.proposedBody, review.proposedType ?? review.entryType)
    setEntries((current) => current.map((currentEntry) => currentEntry.id === updated.id ? updated : currentEntry))
    setSelectedEntryId(updated.id)
    setSelectedNodeId(null)
    setInspectorMode('task')
  }, [projectId])
  const handleRejectEntryAiReview = useCallback(async (entry: Entry, review: EntryAiReview) => {
    if (!projectId) throw new Error('Project is unavailable.')
    await rejectEntryAiReview(projectId, entry.id, review.originalBody, review.proposedBody)
  }, [projectId])
  const handleAcceptAnswer = useCallback(async (questionId: string, entryId: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const acceptedAnswer = await createRelationship(projectId, {
      fromEntityType: 'WORK_ITEM',
      fromEntityId: questionId,
      toEntityType: 'ENTRY',
      toEntityId: entryId,
      type: 'ACCEPTED_ANSWER',
      reason: null,
      sourceEntryId: entryId,
    })
    setRelationships((current) => [
      ...current.filter((relationship) => relationship.type !== 'ACCEPTED_ANSWER' || relationship.fromEntityId !== questionId),
      acceptedAnswer,
    ])
    setNodes((current) => current.map((node) => node.id === questionId ? withNodeFieldValue(node, 'status', 'Status', 'ANSWERED') : node))
    if (selectedNodeId === questionId) {
      setForm((current) => {
        const hasStatus = current.fields.some((field) => field.name === 'status')
        return {
          ...current,
          fields: hasStatus
            ? current.fields.map((field) => field.name === 'status' ? { ...field, value: 'ANSWERED' } : field)
            : [...current.fields, { clientId: createDraftId(), name: 'status', label: 'Status', dataType: 'text', value: 'ANSWERED', visibleInTree: true }],
        }
      })
    }
    toast.success(t('workspace.acceptedAnswerUpdated'))
  }, [projectId, selectedNodeId])
  const refreshBlockers = useCallback(() => {
    if (!projectId) return
    void getWorkspace(projectId)
      .then((workspace) => {
        setBlockerWorkItems(workspace.workItems.map((item) => item.workItem))
        setRelationships(workspace.relationships)
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : t('workspace.failedRefreshBlockers')))
  }, [projectId])
  const handleAddBlocker = useCallback(async (nodeId: string, blockerId: string) => {
    if (!projectId) return
    setIsSavingBlocker(true)
    try {
      const blocker = await createRelationship(projectId, {
        fromEntityType: 'WORK_ITEM',
        fromEntityId: nodeId,
        toEntityType: 'WORK_ITEM',
        toEntityId: blockerId,
        type: 'BLOCKED_BY',
        reason: null,
        sourceEntryId: null,
      })
      setRelationships((current) => [...current, blocker])
      toast.success(t('workspace.blockerAdded'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedAddBlocker'))
    } finally {
      setIsSavingBlocker(false)
    }
  }, [projectId])
  const handleRemoveBlocker = useCallback(async (relationshipId: string) => {
    if (!projectId) return
    setIsSavingBlocker(true)
    try {
      await deleteRelationship(projectId, relationshipId)
      setRelationships((current) => current.filter((relationship) => relationship.id !== relationshipId))
      toast.success(t('workspace.blockerRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedRemoveBlocker'))
    } finally {
      setIsSavingBlocker(false)
    }
  }, [projectId])
  const handleUpdateBlockerReason = useCallback(async (relationshipId: string, reason: string | null) => {
    if (!projectId) return
    setIsSavingBlocker(true)
    try {
      const updated = await updateRelationshipReason(projectId, relationshipId, reason)
      setRelationships((current) => current.map((relationship) => relationship.id === updated.id ? updated : relationship))
      toast.success(reason ? t('workspace.blockerReasonUpdated') : t('workspace.blockerReasonRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedUpdateBlockerReason'))
    } finally {
      setIsSavingBlocker(false)
    }
  }, [projectId])
  const blockerUi = useMemo<BlockerUi>(() => ({
    relationshipsByNodeId: blockedByRelationshipsByNodeId,
    workItems: blockerWorkItems,
    titleById: blockerTitleById,
    isSaving: isSavingBlocker,
    onRefresh: refreshBlockers,
    onAdd: handleAddBlocker,
    onRemove: handleRemoveBlocker,
    onUpdateReason: handleUpdateBlockerReason,
  }), [blockedByRelationshipsByNodeId, blockerTitleById, blockerWorkItems, handleAddBlocker, handleRemoveBlocker, handleUpdateBlockerReason, isSavingBlocker, refreshBlockers])
  const handleAddWorkItemRelationship = useCallback(async (
    nodeId: string,
    relatedNodeId: string,
    type: string,
    direction: 'OUTGOING' | 'INCOMING',
    reason: string | null,
  ) => {
    if (!projectId) return
    setIsSavingBlocker(true)
    try {
      const relationship = await createRelationship(projectId, {
        fromEntityType: 'WORK_ITEM',
        fromEntityId: direction === 'OUTGOING' ? nodeId : relatedNodeId,
        toEntityType: 'WORK_ITEM',
        toEntityId: direction === 'OUTGOING' ? relatedNodeId : nodeId,
        type,
        reason,
        sourceEntryId: null,
      })
      setRelationships((current) => [...current, relationship])
      toast.success(t('workspace.relationshipAdded'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedAddRelationship'))
    } finally {
      setIsSavingBlocker(false)
    }
  }, [projectId])
  const handleRemoveWorkItemRelationship = useCallback(async (relationshipId: string) => {
    if (!projectId) return
    setIsSavingBlocker(true)
    try {
      await deleteRelationship(projectId, relationshipId)
      setRelationships((current) => current.filter((relationship) => relationship.id !== relationshipId))
      toast.success(t('workspace.relationshipRemoved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedRemoveRelationship'))
    } finally {
      setIsSavingBlocker(false)
    }
  }, [projectId])
  const workItemRelationshipUi = useMemo<WorkItemRelationshipUi>(() => ({
    workItems: blockerWorkItems,
    isSaving: isSavingBlocker,
    onAdd: handleAddWorkItemRelationship,
  }), [blockerWorkItems, handleAddWorkItemRelationship, isSavingBlocker])
  const handleSaveInspectorEntry = useCallback(async () => {
    if (!projectId || !selectedEntry) return
    const body = entryInspectorBody.trim()
    if (!body) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      const updated = await updateEntry(projectId, selectedEntry.id, { workItemId: selectedEntry.workItemId, type: entryInspectorType, body })
      setEntries((current) => current.map((entry) => entry.id === updated.id ? updated : entry))
      toast.success(t('workspace.updateSaved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSaveUpdate'))
    } finally {
      setIsSaving(false)
    }
  }, [entryInspectorBody, entryInspectorType, projectId, selectedEntry])
  const handleReviewInspectorEntryWithAi = useCallback(async () => {
    if (!projectId || !selectedEntry) return
    const body = entryInspectorBody.trim()
    if (!body) {
      toast.error(t('workspace.updateRequired'))
      return
    }
    setIsSaving(true)
    try {
      const review = await reviewEntryWithAi(projectId, selectedEntry.id, body, entryInspectorType)
      setEntryInspectorReview({ ...review, entryType: entryInspectorType })
      setEntryInspectorReviewFeedback('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedReviewUpdate'))
    } finally {
      setIsSaving(false)
    }
  }, [entryInspectorBody, entryInspectorType, projectId, selectedEntry])
  const handleAcceptInspectorEntryReview = useCallback(async () => {
    if (!projectId || !selectedEntry || !entryInspectorReview) return
    setIsSaving(true)
    try {
      const updated = await acceptEntryAiReview(projectId, selectedEntry.id, entryInspectorReview.originalBody, entryInspectorReview.proposedBody, entryInspectorReview.proposedType ?? entryInspectorReview.entryType)
      setEntries((current) => current.map((entry) => entry.id === updated.id ? updated : entry))
      setEntryInspectorReview(null)
      setEntryInspectorReviewFeedback('')
      toast.success(t('workspace.suggestionAccepted'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedAcceptSuggestion'))
    } finally {
      setIsSaving(false)
    }
  }, [entryInspectorReview, projectId, selectedEntry])
  const handleRejectInspectorEntryReview = useCallback(async () => {
    if (!projectId || !selectedEntry || !entryInspectorReview) return
    setIsSaving(true)
    try {
      await rejectEntryAiReview(projectId, selectedEntry.id, entryInspectorReview.originalBody, entryInspectorReview.proposedBody)
      setEntryInspectorReview(null)
      setEntryInspectorReviewFeedback('')
      toast.success(t('workspace.suggestionRejected'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedRejectSuggestion'))
    } finally {
      setIsSaving(false)
    }
  }, [entryInspectorReview, projectId, selectedEntry])
  const handleRefineInspectorEntryReview = useCallback(async () => {
    if (!projectId || !selectedEntry || !entryInspectorReview) return
    setIsSaving(true)
    try {
      const nextReview = await reviewEntryWithAi(
        projectId,
        selectedEntry.id,
        entryInspectorReview.proposedBody,
        entryInspectorReview.proposedType ?? entryInspectorReview.entryType ?? entryInspectorType,
        entryInspectorReviewFeedback,
      )
      setEntryInspectorReview({ ...nextReview, entryType: entryInspectorReview.proposedType ?? entryInspectorReview.entryType ?? entryInspectorType })
      setEntryInspectorReviewFeedback('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSuggestionUpdate'))
    } finally {
      setIsSaving(false)
    }
  }, [entryInspectorReview, entryInspectorReviewFeedback, entryInspectorType, projectId, selectedEntry])
  function keepEditingInspectorEntrySuggestion() {
    if (!entryInspectorReview) return
    setEntryInspectorBody(entryInspectorReview.proposedBody)
    setEntryInspectorType(entryInspectorReview.proposedType ?? entryInspectorReview.entryType ?? entryInspectorType)
    setEntryInspectorReview(null)
    setEntryInspectorReviewFeedback('')
  }
  const handleDeleteEntry = useCallback(async (entryId: string) => {
    if (!projectId) throw new Error('Project is unavailable.')
    const affectedQuestionIds = new Set(relationships
      .filter((relationship) => relationship.type === 'ACCEPTED_ANSWER' && relationship.toEntityType === 'ENTRY' && relationship.toEntityId === entryId)
      .map((relationship) => relationship.fromEntityId))
    await deleteEntry(projectId, entryId)
    setEntries((current) => current.filter((entry) => entry.id !== entryId))
    setRelationships((current) => current.filter((relationship) => (
      relationship.fromEntityId !== entryId
      && relationship.toEntityId !== entryId
      && relationship.sourceEntryId !== entryId
    )))
    if (affectedQuestionIds.size > 0) {
      setNodes((current) => current.map((node) => affectedQuestionIds.has(node.id) ? withNodeFieldValue(node, 'status', 'Status', 'OPEN') : node))
      if (selectedNodeId && affectedQuestionIds.has(selectedNodeId)) {
        setForm((current) => ({
          ...current,
          fields: current.fields.map((field) => field.name === 'status' ? { ...field, value: 'OPEN' } : field),
        }))
      }
    }
    if (selectedEntryId === entryId) clearSelectedNode()
  }, [projectId, relationships, selectedEntryId, selectedNodeId])
  const handleRunWorkItemFilters = useCallback(async () => {
    if (!projectId) return
    setIsRunningWorkItemFilters(true)
    try {
      const searchQuery = workItemSearchQuery.trim()
      const allProjectNodes = await listNodes(projectId, searchQuery)
      setNodes(allProjectNodes)
      setRootPageInfo(null)
      setChildrenPageInfoByParentId(new Map())
      setLoadedChildrenParentIds(new Set(allProjectNodes.filter((node) => node.childrenCount).map((node) => node.id)))
      setAppliedWorkItemFilterConditions(workItemFilterConditions)
      setAppliedWorkItemSearchQuery(searchQuery)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedRunFilters'))
    } finally {
      setIsRunningWorkItemFilters(false)
    }
  }, [projectId, workItemFilterConditions, workItemSearchQuery])
  const handleClearWorkItemFilters = useCallback(async () => {
    if (!projectId) return
    setIsRunningWorkItemFilters(true)
    try {
      const allProjectNodes = await listNodes(projectId)
      setNodes(allProjectNodes)
      setRootPageInfo(null)
      setChildrenPageInfoByParentId(new Map())
      setLoadedChildrenParentIds(new Set(allProjectNodes.filter((node) => node.childrenCount).map((node) => node.id)))
      setWorkItemSearchQuery('')
      setAppliedWorkItemSearchQuery('')
      setWorkItemFilterConditions([])
      setAppliedWorkItemFilterConditions([])
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedClearFilters'))
    } finally {
      setIsRunningWorkItemFilters(false)
    }
  }, [projectId])
  const handleRunCreatedSort = useCallback(async () => {
    if (!projectId) return
    const nextDirection: Exclude<CreatedSortDirection, null> = createdSortDirection === 'DESC' ? 'ASC' : 'DESC'
    setIsRunningCreatedSort(true)
    try {
      const allProjectNodes = await listNodes(projectId)
      setNodes(allProjectNodes)
      setRootPageInfo(null)
      setChildrenPageInfoByParentId(new Map())
      setLoadedChildrenParentIds(new Set(allProjectNodes.filter((node) => node.childrenCount).map((node) => node.id)))
      setCreatedSortDirection(nextDirection)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSort'))
    } finally {
      setIsRunningCreatedSort(false)
    }
  }, [createdSortDirection, projectId])
  useEffect(() => {
    if (!projectId) {
      return
    }

    const currentProjectId = projectId
    let isMounted = true

    async function loadPage() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const [nextProject, nextRootNodes, nextWorkspace, nextGraphChangeProposals, nextLlmStatus, nextUsers, nextTeams, nextProjectMembers, nextProjectTeams] = await Promise.all([
          getProject(currentProjectId),
          listTreeNodes(currentProjectId, { page: 0, size: treePageSize }),
          getWorkspace(currentProjectId),
          listGraphChangeProposals(currentProjectId),
          getLlmStatus().catch(() => ({ provider: 'none', available: false })),
          loadSelectableUsers().catch(() => []),
          listTeams().catch(() => []),
          listProjectMembers(currentProjectId).catch(() => []),
          listProjectTeams(currentProjectId).catch(() => []),
        ])

        if (!isMounted) {
          return
        }

        const proposalContextNodes = await loadProposalContextNodes(
          currentProjectId,
          nextGraphChangeProposals,
          nextRootNodes.items,
        )
        const projectTeamMembers = await Promise.all(
          nextProjectTeams.map((team) => listTeamMembers(team.teamId).catch(() => [])),
        )
        let nextNodes = proposalContextNodes
        let initialSelectedNode: ProjectNode | null = null
        let initialExpandedNodeIds = [
          ...proposalParentIds(nextGraphChangeProposals),
          ...proposalContextNodes.map((node) => node.parentNodeId).filter((nodeId): nodeId is string => Boolean(nodeId)),
        ]
        let initialLoadedChildrenParentIds = new Set<string>()
        let initialChildrenPageInfoByParentId = new Map<string, NodePageInfo>()

        if (requestedWorkItemId) {
          const requestedNodePath = await loadRequestedWorkItemTreePath(currentProjectId, requestedWorkItemId, nextNodes)

          if (requestedNodePath.selectedNode) {
            nextNodes = requestedNodePath.nodes
            initialSelectedNode = requestedNodePath.selectedNode
            initialExpandedNodeIds = [...initialExpandedNodeIds, ...requestedNodePath.expandedNodeIds]
            initialLoadedChildrenParentIds = requestedNodePath.loadedChildrenParentIds
            initialChildrenPageInfoByParentId = requestedNodePath.childrenPageInfoByParentId
          }
        }

        if (!isMounted) {
          return
        }

        setProject(nextProject)
        setNodes(nextNodes)
        setEntries(nextWorkspace.entries)
        setRelationships(nextWorkspace.relationships)
        setBlockerWorkItems(nextWorkspace.workItems.map((item) => item.workItem))
        setRootPageInfo(pageInfoFromResponse(nextRootNodes))
        setReferenceUsers(nextUsers)
        setReferenceTeams(nextTeams)
        setProjectMemberUserIds(new Set([
          ...nextProjectMembers.map((member) => member.userId),
          ...projectTeamMembers.flatMap((members) => members.map((member) => member.userId)),
        ]))
        setProjectTeamIds(new Set(nextProjectTeams.map((team) => team.teamId)))
        setGraphChangeProposals(nextGraphChangeProposals)
        setIsLlmAvailable(nextLlmStatus.available)
        setExpandedNodeIds(new Set(initialExpandedNodeIds))
        setLoadedChildrenParentIds(initialLoadedChildrenParentIds)
        setChildrenPageInfoByParentId(initialChildrenPageInfoByParentId)

        setSelectedNodeId(initialSelectedNode?.id ?? null)
        setForm(createFormState(initialSelectedNode))
        if (initialSelectedNode) {
          setInspectorMode('task')
          highlightChatReference(initialSelectedNode.id)
        }
      } catch (error) {
        if (isMounted) {
          setProject(null)
          setErrorMessage(error instanceof Error ? error.message : 'Failed to load project.')
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadPage()

    return () => {
      isMounted = false
    }
  }, [projectId, requestedWorkItemId])

  useEffect(() => {
    if (selectedNode) {
      const selectTimer = window.setTimeout(() => {
        setForm(createFormState(selectedNode))
        setWorkItemInspectorReview(null)
        setWorkItemInspectorReviewFeedback('')
      }, 0)

      return () => window.clearTimeout(selectTimer)
    }
  }, [selectedNode])
  useEffect(() => {
    if (selectedEntry) {
      setEntryInspectorType(selectedEntry.type)
      setEntryInspectorBody(selectedEntry.body)
      setEntryInspectorReview(null)
      setEntryInspectorReviewFeedback('')
    }
  }, [selectedEntry])
  useEffect(() => {
    if (focusedNodeId && !findTreeNode(tree, focusedNodeId)) {
      setFocusedNodeId(null)
    }
  }, [focusedNodeId, tree])
  useEffect(() => {
    if (!projectId || !selectedNodeId) {
      return
    }
    let cancelled = false
    getSubscriptionStatus(projectId, selectedNodeId)
      .then((status) => {
        if (cancelled) {
          return
        }
        setSubscribedWorkItemIds((current) => {
          const next = new Set(current)
          if (status.subscribed) {
            next.add(selectedNodeId)
          } else {
            next.delete(selectedNodeId)
          }
          return next
        })
      })
      .catch(() => {
        if (cancelled) {
          return
        }
        setSubscribedWorkItemIds((current) => {
          const next = new Set(current)
          next.delete(selectedNodeId)
          return next
        })
      })
    return () => {
      cancelled = true
    }
  }, [projectId, selectedNodeId])

  if (!projectId) {
    return <Navigate to="/app/projects" replace />
  }

  function workspaceDestination(path: string) {
    const params = new URLSearchParams(location.search)
    if (!params.get('chatSessionId')) {
      return path
    }
    params.set('chatPanel', params.get('chatPanel') === 'closed' ? 'closed' : 'open')
    return `${path}?${params.toString()}`
  }

  function toggleInspectorPanel() {
    const nextCollapsed = !isInspectorCollapsed
    setIsInspectorCollapsed(nextCollapsed)
    try {
      window.localStorage.setItem(workspaceInspectorCollapsedStorageKey, String(nextCollapsed))
    } catch {
      // Layout persistence is optional when browser storage is unavailable.
    }
  }

  function selectNode(node: ProjectNode) {
    if (selectedNodeId === node.id) {
      clearSelectedNode()
      return
    }
    setSelectedNodeId(node.id)
    setSelectedEntryId(null)
    setForm(createFormState(node))
    setInspectorMode('task')
  }

  function selectEntry(entry: Entry) {
    if (selectedEntryId === entry.id) {
      clearSelectedNode()
      return
    }
    setSelectedEntryId(entry.id)
    setSelectedNodeId(null)
    setInspectorMode('task')
  }

  function clearSelectedNode() {
    setSelectedNodeId(null)
    setSelectedEntryId(null)
    setForm(newNodeDefaults)
  }

  const isSelectedWorkItemSubscribed = selectedNodeId ? subscribedWorkItemIds.has(selectedNodeId) : false

  async function handleToggleSelectedWorkItemSubscription() {
    if (!projectId || !selectedNodeId || selectedNode?.proposal) {
      return
    }
    try {
      if (isSelectedWorkItemSubscribed) {
        const status = await unsubscribeWorkItem(projectId, selectedNodeId)
        setSubscribedWorkItemIds((current) => {
          const next = new Set(current)
          if (status.subscribed) {
            next.add(selectedNodeId)
          } else {
            next.delete(selectedNodeId)
          }
          return next
        })
        toast.success(t('workspace.unsubscribed'))
      } else {
        const status = await subscribeWorkItem(projectId, selectedNodeId)
        setSubscribedWorkItemIds((current) => {
          const next = new Set(current)
          if (status.subscribed) {
            next.add(selectedNodeId)
          } else {
            next.delete(selectedNodeId)
          }
          return next
        })
        toast.success(t('workspace.subscribed'))
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedUpdateSubscription'))
    }
  }

  async function loadRootNodes(page = 0, append = false) {
    if (!projectId) {
      return
    }

    const response = await listTreeNodes(projectId, { page, size: treePageSize })
    setRootPageInfo(pageInfoFromResponse(response))
    setNodes((current) => (append ? mergeNodesById(current, response.items) : response.items))
    return response
  }

  async function loadChildren(parentNodeId: string, page = 0, append = false) {
    if (!projectId) {
      return
    }

    setLoadingChildrenNodeIds((current) => new Set(current).add(parentNodeId))

    try {
      const response = await listTreeNodes(projectId, { parentNodeId, page, size: treePageSize })
      setChildrenPageInfoByParentId((current) => {
        const next = new Map(current)
        next.set(parentNodeId, pageInfoFromResponse(response))
        return next
      })
      setLoadedChildrenParentIds((current) => new Set(current).add(parentNodeId))
      setNodes((current) => {
        const retainedNodes = append ? current : current.filter((node) => node.parentNodeId !== parentNodeId)
        return mergeNodesById(retainedNodes, response.items)
      })
      return response
    } finally {
      setLoadingChildrenNodeIds((current) => {
        const next = new Set(current)
        next.delete(parentNodeId)
        return next
      })
    }
  }

  async function toggleNode(nodeId: string) {
    const isExpanded = expandedNodeIds.has(nodeId)
    if (isExpanded) {
      setExpandedNodeIds((current) => {
        const next = new Set(current)
        next.delete(nodeId)
        return next
      })
      return
    }

    setExpandedNodeIds((current) => new Set(current).add(nodeId))
    if (!loadedChildrenParentIds.has(nodeId) && !loadingChildrenNodeIds.has(nodeId)) {
      try {
        await loadChildren(nodeId)
      } catch (error) {
        setExpandedNodeIds((current) => {
          const next = new Set(current)
          next.delete(nodeId)
          return next
        })
        toast.error(error instanceof Error ? error.message : t('workspace.failedLoadSubItems'))
      }
    }
  }

  async function handleExpandSelectedSubtree() {
    if (!projectId || !selectedNode || selectedNode.proposal || isExpandingSelectedSubtree) {
      return
    }

    setIsExpandingSelectedSubtree(true)
    try {
      const response = await listWorkItemSubtree(projectId, selectedNode.id, {
        maxDepth: treeSubtreeMaxDepth,
        maxItems: treeSubtreeMaxItems,
      })
      const loadedParentIds = new Set<string>([
        selectedNode.id,
        ...response.items
          .map((node) => node.parentNodeId)
          .filter((parentNodeId): parentNodeId is string => Boolean(parentNodeId)),
      ])

      setNodes((current) => mergeNodesById(current, response.items))
      setLoadedChildrenParentIds((current) => {
        const next = new Set(current)
        loadedParentIds.forEach((parentNodeId) => {
          if (response.truncated) {
            next.delete(parentNodeId)
          } else {
            next.add(parentNodeId)
          }
        })
        return next
      })
      setChildrenPageInfoByParentId((current) => {
        const next = new Map(current)
        loadedParentIds.forEach((parentNodeId) => next.delete(parentNodeId))
        return next
      })
      setExpandedNodeIds((current) => new Set([...current, ...loadedParentIds]))
      if (response.truncated) {
        toast.info(t('workspace.expandedFirst', { count: treeSubtreeMaxItems.toLocaleString() }))
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedExpandSubItems'))
    } finally {
      setIsExpandingSelectedSubtree(false)
    }
  }

  function handleCollapseSelectedSubtree() {
    if (!selectedNode || selectedNode.proposal) {
      return
    }

    const subtreeNodeIds = new Set(collectDescendantIds(selectedNode.id, tree))
    setExpandedNodeIds((current) => new Set([...current].filter((nodeId) => !subtreeNodeIds.has(nodeId))))
  }

  async function loadMoreRootNodes() {
    if (!rootPageInfo || rootPageInfo.page + 1 >= rootPageInfo.totalPages || isLoadingMoreRoots) {
      return
    }

    setIsLoadingMoreRoots(true)
    try {
      await loadRootNodes(rootPageInfo.page + 1, true)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedLoadMore'))
    } finally {
      setIsLoadingMoreRoots(false)
    }
  }

  async function loadMoreChildren(parentNodeId: string) {
    const pageInfo = childrenPageInfoByParentId.get(parentNodeId)
    if (!pageInfo || pageInfo.page + 1 >= pageInfo.totalPages || loadingChildrenNodeIds.has(parentNodeId)) {
      return
    }

    try {
      await loadChildren(parentNodeId, pageInfo.page + 1, true)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedLoadMoreSubItems'))
    }
  }

  async function reloadLoadedTree(nextSelectedNodeId?: string, extraParentNodeIds: string[] = []) {
    if (!projectId) {
      return
    }

    const previouslyExpandedNodeIds = [...new Set([...expandedNodeIds, ...extraParentNodeIds])]
    const rootResponse = await loadRootNodes(0)
    const childResponses = await Promise.all(previouslyExpandedNodeIds.map((parentNodeId) => loadChildren(parentNodeId).catch(() => null)))

    const selectedId = nextSelectedNodeId ?? selectedNodeId
    const availableNodes = mergeNodesById(
      rootResponse?.items ?? [],
      childResponses.flatMap((response) => response?.items ?? []),
    )
    const nextSelectedNode = availableNodes.find((node) => node.id === selectedId) ?? null
    setSelectedNodeId(nextSelectedNode?.id ?? null)
    setForm(createFormState(nextSelectedNode))
  }

  const reloadGraphChangeProposals = useCallback(async () => {
    if (!projectId) {
      return
    }

    const nextGraphChangeProposals = await listGraphChangeProposals(projectId)
    const proposalContextNodes = await loadProposalContextNodes(projectId, nextGraphChangeProposals, nodesRef.current)
    setNodes((current) => mergeNodesById(current, proposalContextNodes))
    setGraphChangeProposals(nextGraphChangeProposals)
    setExpandedNodeIds((current) => new Set([
      ...current,
      ...proposalParentIds(nextGraphChangeProposals),
    ]))
  }, [projectId])

  useEffect(() => {
    if (artifactRefreshKey === 0 || !projectId) {
      return
    }

    void reloadGraphChangeProposals().catch((error) => {
      toast.error(error instanceof Error ? error.message : t('workspace.failedRefreshSuggestions'))
    })
  }, [artifactRefreshKey, projectId, reloadGraphChangeProposals])

  async function reloadWorkspaceRelationships() {
    if (!projectId) {
      return
    }

    const workspace = await getWorkspace(projectId)
    setEntries(workspace.entries)
    setRelationships(workspace.relationships)
    setBlockerWorkItems(workspace.workItems.map((item) => item.workItem))
  }

  async function reloadTreeAndProposals(nextSelectedNodeId?: string) {
    await Promise.all([
      reloadLoadedTree(nextSelectedNodeId),
      reloadGraphChangeProposals(),
      reloadWorkspaceRelationships(),
    ])
  }

  function updateManagedWorkItemField(
    name: string,
    label: string,
    dataType: ProjectNodeFieldDataType,
    value: NodeFormField['value'],
    visibleInTree: boolean,
  ) {
    setForm((current) => {
      const existing = current.fields.find((field) => field.name === name)
      if (existing) {
        return {
          ...current,
          fields: current.fields.map((field) => (field.name === name ? { ...field, value } : field)),
        }
      }
      return {
        ...current,
        fields: [...current.fields, { clientId: createDraftId(), name, label, dataType, value, visibleInTree }],
      }
    })
  }

  function handleAddAssignee() {
    if (!newAssigneeId) {
      return
    }

    const isUser = newAssigneeType === 'USER'
    const fieldName = isUser ? 'assigneeUserIds' : 'assigneeTeamIds'
    const label = isUser ? 'Assigned users' : 'Assigned teams'
    const dataType: ProjectNodeFieldDataType = isUser ? 'user' : 'team'
    const assignedIds = isUser ? assignedUserIds : assignedTeamIds
    updateManagedWorkItemField(fieldName, label, dataType, [...assignedIds, newAssigneeId], false)
    setNewAssigneeId('')
  }

  function removeAssignee(type: 'USER' | 'TEAM', assigneeId: string) {
    const isUser = type === 'USER'
    const fieldName = isUser ? 'assigneeUserIds' : 'assigneeTeamIds'
    const label = isUser ? 'Assigned users' : 'Assigned teams'
    const dataType: ProjectNodeFieldDataType = isUser ? 'user' : 'team'
    const assignedIds = isUser ? assignedUserIds : assignedTeamIds
    updateManagedWorkItemField(fieldName, label, dataType, assignedIds.filter((id) => id !== assigneeId), false)
  }

  function handleTypeChange(nextType: string) {
    setForm((current) => {
      const currentStatus = normalizedStatus(formFieldValue(current.fields, 'status'))
      const allowedStatuses = statusOptionsForType(nextType, t)
      const nextStatus = allowedStatuses.some((option) => option.value === currentStatus)
        ? currentStatus
        : defaultStatusForType(nextType)
      const fieldsWithCompatibleStatus = current.fields.some((field) => field.name === 'status')
        ? current.fields.map((field) => field.name === 'status' ? { ...field, value: nextStatus } : field)
        : [...current.fields, { clientId: createDraftId(), name: 'status', label: 'Status', dataType: 'text' as const, value: nextStatus, visibleInTree: true }]

      return { ...current, type: nextType, fields: fieldsWithCompatibleStatus }
    })
  }

  function focusNode(node: TreeNode) {
    setFocusedNodeId(node.id)
    setExpandedNodeIds((current) => new Set(current).add(node.id))
  }

  function highlightChatReference(workItemId: string) {
    if (chatHighlightTimerRef.current !== null) {
      window.clearTimeout(chatHighlightTimerRef.current)
    }
    setChatHighlightedNodeId(workItemId)
    chatHighlightTimerRef.current = window.setTimeout(() => {
      setChatHighlightedNodeId((current) => current === workItemId ? null : current)
      chatHighlightTimerRef.current = null
    }, 1800)
  }

  async function handleCreateNode(parentId?: string, type = 'TASK') {
    if (!projectId) {
      return
    }

    setIsSaving(true)

    try {
      const created = await createNode(projectId, {
        projectId,
        parentNodeId: parentId ?? null,
        type,
        title: t('workspace.untitledItem'),
        fields: [{ name: 'status', label: 'Status', dataType: 'text', value: defaultStatusForType(type), visibleInTree: true }],
      })
      if (parentId) {
        setExpandedNodeIds((current) => new Set(current).add(parentId))
        setLoadedChildrenParentIds((current) => new Set(current).add(parentId))
        setChildrenPageInfoByParentId((current) => {
          const next = new Map(current)
          const currentPageInfo = next.get(parentId)
          if (currentPageInfo) {
            next.set(parentId, {
              ...currentPageInfo,
              totalItems: currentPageInfo.totalItems + 1,
              totalPages: Math.ceil((currentPageInfo.totalItems + 1) / currentPageInfo.size),
            })
          }
          return next
        })
        setNodes((current) => mergeNodesById(
          current.map((node) => (
            node.id === parentId
              ? { ...node, childrenCount: (node.childrenCount ?? 0) + 1 }
              : node
          )),
          [created],
        ))
      } else {
        setRootPageInfo((current) => current ? {
          ...current,
          totalItems: current.totalItems + 1,
          totalPages: Math.ceil((current.totalItems + 1) / current.size),
        } : current)
        setNodes((current) => mergeNodesById(current, [created]))
      }

      setSelectedNodeId(created.id)
      setForm(createFormState(created))
      setInspectorMode('task')
      setAutoEditTitleNodeId(created.id)
      toast.success(parentId ? t('workspace.subItemCreated') : t('workspace.itemCreated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedCreateItem'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleSaveNode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!projectId || !selectedNode) {
      return
    }

    if (selectedNode.proposal) {
      toast.error(t('workspace.acceptRejectFromRow'))
      return
    }

    const type = form.type.trim()
    const title = form.title.trim()
    if (!type || !title) {
      toast.error(t('workspace.titleRequired'))
      return
    }

    const fieldNames = new Set<string>()
    for (const field of form.fields) {
      const normalizedName = field.name.trim()
      if (!normalizedName) {
        toast.error(t('workspace.fieldNameRequired'))
        return
      }

      const normalizedKey = normalizedName.toLowerCase()
      if (fieldNames.has(normalizedKey)) {
        toast.error(t('workspace.uniqueFieldNames'))
        return
      }
      fieldNames.add(normalizedKey)

      if (field.dataType === 'number') {
        const rawValue = String(field.value ?? '').trim()
        if (rawValue && Number.isNaN(Number(rawValue))) {
          toast.error(t('workspace.invalidNumber', { field: field.label || field.name }))
          return
        }
      }
    }

    setIsSaving(true)

    try {
      const updated = await updateNode(projectId, selectedNode.id, {
        projectId,
        parentNodeId: selectedNode.parentNodeId ?? null,
        sortIndex: selectedNode.sortIndex ?? 0,
        type,
        title,
        fields: serializeFields(form.fields),
      })
      setNodes((current) => current.map((node) => (
        node.id === updated.id ? { ...updated, childrenCount: node.childrenCount ?? updated.childrenCount } : node
      )))
      setSelectedNodeId(updated.id)
      setForm(createFormState(updated))
      toast.success(t('workspace.itemUpdated'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedUpdateItem'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleReviewWorkItemWithAi() {
    if (!projectId || !selectedNode || selectedNode.proposal) return
    const title = form.title.trim()
    const type = form.type.trim()
    if (!title || !type) {
      toast.error(t('workspace.titleRequired'))
      return
    }
    setIsSaving(true)
    try {
      const review = await reviewWorkItemWithAi(projectId, selectedNode.id, {
        title,
        type: type as WorkItem['type'],
        status: (normalizedStatus(formFieldValue(form.fields, 'status')) || defaultStatusForType(type)).replaceAll(' ', '_') as WorkItem['status'],
        dueDate: String(formFieldValue(form.fields, 'dueDate') ?? '').trim() || null,
        priority: String(formFieldValue(form.fields, 'priority') ?? '').trim() || null,
        assignees: [
          ...parseStringArrayValue(formFieldValue(form.fields, 'assigneeUserIds')).map((assigneeId) => ({ assigneeType: 'USER' as const, assigneeId })),
          ...parseStringArrayValue(formFieldValue(form.fields, 'assigneeTeamIds')).map((assigneeId) => ({ assigneeType: 'TEAM' as const, assigneeId })),
        ],
      })
      setWorkItemInspectorReview(review)
      setWorkItemInspectorReviewFeedback('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSuggestion'))
    } finally {
      setIsSaving(false)
    }
  }

  function applyWorkItemAiReview(review: WorkItemAiReview) {
    const updateField = (fields: NodeFormField[], name: string, label: string, value: NodeFormField['value']) => {
      const existing = fields.find((field) => field.name === name)
      return existing
        ? fields.map((field) => field.name === name ? { ...field, value } : field)
        : [...fields, { clientId: createDraftId(), name, label, dataType: 'text' as const, value, visibleInTree: true }]
    }
    setForm((current) => {
      let fields = updateField(current.fields, 'status', 'Status', review.proposedStatus)
      fields = updateField(fields, 'dueDate', 'Due date', review.proposedDueDate ?? '')
      fields = updateField(fields, 'priority', 'Priority', review.proposedPriority ?? '')
      fields = updateField(fields, 'assigneeUserIds', 'Assignee users', review.proposedAssignees.filter((assignee) => assignee.assigneeType === 'USER').map((assignee) => assignee.assigneeId))
      fields = updateField(fields, 'assigneeTeamIds', 'Assignee teams', review.proposedAssignees.filter((assignee) => assignee.assigneeType === 'TEAM').map((assignee) => assignee.assigneeId))
      return { ...current, title: review.proposedTitle, type: review.proposedType, fields }
    })
    setWorkItemInspectorReview(null)
    setWorkItemInspectorReviewFeedback('')
    toast.success(t('workspace.saveSuggestion'))
  }

  async function handleAddSuggestedBlockers(review: WorkItemAiReview) {
    if (!projectId || !selectedNode) return
    const existingBlockerIds = new Set((blockedByRelationshipsByNodeId.get(selectedNode.id) ?? []).map((relationship) => relationship.toEntityId))
    const suggestions = review.proposedBlockers.filter((blocker) => !existingBlockerIds.has(blocker.workItemId))
    if (suggestions.length === 0) return
    setIsSaving(true)
    try {
      const added: Relationship[] = []
      for (const suggestion of suggestions) {
        added.push(await createRelationship(projectId, {
          fromEntityType: 'WORK_ITEM',
          fromEntityId: selectedNode.id,
          toEntityType: 'WORK_ITEM',
          toEntityId: suggestion.workItemId,
          type: 'BLOCKED_BY',
          reason: suggestion.reason,
          sourceEntryId: null,
        }))
      }
      setRelationships((current) => [...current, ...added])
      setWorkItemInspectorReview((current) => current ? { ...current, proposedBlockers: current.proposedBlockers.filter((blocker) => !suggestions.some((suggestion) => suggestion.workItemId === blocker.workItemId)) } : null)
      toast.success(t('workspace.blockersAdded', { count: added.length }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedAddSuggestedBlockers'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleRefineWorkItemAiReview() {
    if (!projectId || !selectedNode || !workItemInspectorReview) return
    setIsSaving(true)
    try {
      const nextReview = await reviewWorkItemWithAi(projectId, selectedNode.id, {
        title: workItemInspectorReview.proposedTitle,
        type: workItemInspectorReview.proposedType,
        status: workItemInspectorReview.proposedStatus,
        dueDate: workItemInspectorReview.proposedDueDate,
        priority: workItemInspectorReview.proposedPriority,
        assignees: workItemInspectorReview.proposedAssignees,
        instruction: workItemInspectorReviewFeedback,
      })
      setWorkItemInspectorReview(nextReview)
      setWorkItemInspectorReviewFeedback('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedSuggestionUpdate'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleDeleteNode(nodeId: string) {
    if (!projectId) {
      return
    }

    const deleteIds = collectDescendantIds(nodeId, tree)
    const deleteSet = new Set(deleteIds)
    if (deleteIds.length === 0) {
      return
    }

    setIsSaving(true)

    try {
      const targetNode = findTreeNode(tree, nodeId)
      await deleteNode(projectId, nodeId)
      setEntries((current) => current.filter((entry) => !deleteSet.has(entry.workItemId)))
      setNodes((current) => {
        const nextNodes = removeLoadedSubtree(current, deleteSet)
        if (!targetNode?.parentNodeId) {
          return nextNodes
        }
        return nextNodes.map((node) => (
          node.id === targetNode.parentNodeId
            ? { ...node, childrenCount: Math.max(0, (node.childrenCount ?? 0) - 1) }
            : node
        ))
      })
      if (!targetNode?.parentNodeId) {
        setRootPageInfo((current) => current ? {
          ...current,
          totalItems: Math.max(0, current.totalItems - 1),
          totalPages: Math.ceil(Math.max(0, current.totalItems - 1) / current.size),
        } : current)
      } else {
        const parentNodeId = targetNode.parentNodeId
        setChildrenPageInfoByParentId((current) => {
          const next = new Map(current)
          const currentPageInfo = next.get(parentNodeId)
          if (currentPageInfo) {
            const totalItems = Math.max(0, currentPageInfo.totalItems - 1)
            next.set(parentNodeId, {
              ...currentPageInfo,
              totalItems,
              totalPages: Math.ceil(totalItems / currentPageInfo.size),
            })
          }
          return next
        })
      }
      if (selectedNodeId && deleteSet.has(selectedNodeId)) {
        clearSelectedNode()
      }
      toast.success(t('workspace.itemDeleted'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedDeleteItem'))
    } finally {
      setIsSaving(false)
    }
  }

  function openMoveDialog(node?: ProjectNode) {
    const nodeToMove = node ?? selectedNode
    if (!nodeToMove || (nodeToMove as ProjectNode & { proposal?: TreeNodeProposal }).proposal) {
      return
    }

    setSelectedNodeId(nodeToMove.id)
    setMoveTargetContentKey('')
    setMoveQuery('')
    setIsMoveDialogOpen(true)
  }

  async function handleMoveNode() {
    if (!projectId || !selectedNode) {
      return
    }

    const target = moveTargetOptions.find((item) => contentEntityKey(item.entityType, item.entityId) === moveTargetContentKey)
    const destinationParentId = target?.parentWorkItemId ?? null

    setIsSaving(true)

    try {
      const moved = await moveWorkItemInContentOrder(projectId, selectedNode.id, destinationParentId, target)
      if (destinationParentId) {
        setExpandedNodeIds((current) => new Set(current).add(destinationParentId))
      }
      await reloadLoadedTree(selectedNode.id, destinationParentId ? [destinationParentId] : [])
      setNodes((current) => mergeNodesById(current, [{ ...moved, childrenCount: selectedNode.childrenCount }]))
      setSelectedNodeId(moved.id)
      setForm(createFormState(moved))
      setIsMoveDialogOpen(false)
      toast.success(target ? t('workspace.itemMoved') : t('workspace.itemMovedToEnd'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedMoveItem'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleMoveContentItem(parentWorkItemId: string | null, entityType: 'WORK_ITEM' | 'ENTRY', entityId: string, offset: number) {
    if (!projectId || isSaving) {
      return
    }
    const siblings = [...(contentOrderByParent.get(contentParentKey(parentWorkItemId)) ?? [])]
    const currentIndex = siblings.findIndex((item) => item.entityType === entityType && item.entityId === entityId)
    if (currentIndex < 0) {
      return
    }
    const nextIndex = currentIndex + offset
    if (nextIndex < 0 || nextIndex >= siblings.length) {
      return
    }
    const reordered = [...siblings]
    ;[reordered[currentIndex], reordered[nextIndex]] = [reordered[nextIndex], reordered[currentIndex]]
    setIsSaving(true)
    try {
      const updated = await reorderContentItems(projectId, parentWorkItemId, reordered.map((item) => ({ entityType: item.entityType, entityId: item.entityId })))
      const updatedSortIndexes = new Map(updated.map((item) => [contentEntityKey(item.entityType, item.entityId), item.sortIndex]))
      setNodes((current) => current.map((node) => {
        const sortIndex = updatedSortIndexes.get(contentEntityKey('WORK_ITEM', node.id))
        return sortIndex == null ? node : { ...node, sortIndex }
      }))
      setEntries((current) => current.map((entry) => {
        const sortIndex = updatedSortIndexes.get(contentEntityKey('ENTRY', entry.id))
        return sortIndex == null ? entry : { ...entry, sortIndex }
      }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.failedReorder'))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleMoveNodeByOffset(node: ProjectNode, offset: number) {
    if ((node as ProjectNode & { proposal?: TreeNodeProposal }).proposal) return
    await handleMoveContentItem(node.parentNodeId ?? null, 'WORK_ITEM', node.id, offset)
  }

  async function handleMoveSelectedNodeByOffset(offset: number) {
    if (!selectedNode) {
      return
    }

    await handleMoveNodeByOffset(selectedNode, offset)
  }

  async function handleDecideProposal(proposal: TreeNodeProposal, decision: 'ACCEPT' | 'REJECT') {
    const related = openWorkspaceChangesForNode(graphChangeProposals, proposal.targetId)
    const selectedChange = graphChangeProposals.flatMap((item) => item.changes).find((change) => change.id === proposal.changeId)
    if (related.length === 0 && !selectedChange) {
      toast.error(t('workspace.proposalUnavailable'))
      return
    }
    await handleDecideWorkspaceChanges(
      related.length > 0 ? related : [{ proposalId: proposal.proposalId, change: selectedChange! }],
      decision,
    )
  }

  async function handleDecideWorkspaceChanges(
    proposalChanges: Array<{ proposalId: string; change: GraphChangeProposalChange }>,
    decision: 'ACCEPT' | 'REJECT',
  ) {
    if (!projectId || decidingProposalChangeId || proposalChanges.length === 0) return
    const allOpenChanges = graphChangeProposals.flatMap((proposal) => proposal.changes
      .filter((change) => isOpenProposalStatus(change.status))
      .map((change) => ({ proposalId: proposal.id, change })))
    const proposedNodeAddById = new Map(allOpenChanges
      .filter(({ change }) => change.entityType === 'NODE' && change.action === 'ADD')
      .map((item) => [item.change.targetId, item]))
    const expandedChanges = [...proposalChanges]
    const includedChangeIds = new Set(expandedChanges.map(({ change }) => change.id))

    if (decision === 'ACCEPT') {
      const includeRequiredParent = (parentId: string | null | undefined) => {
        if (!parentId || nodes.some((node) => node.id === parentId)) return
        const parentChange = proposedNodeAddById.get(parentId)
        if (!parentChange || includedChangeIds.has(parentChange.change.id)) return
        includedChangeIds.add(parentChange.change.id)
        includeRequiredParent(parentChange.change.node?.parentNodeId)
        expandedChanges.push(parentChange)
      }
      for (const { change } of [...expandedChanges]) {
        if (change.entityType === 'NODE' && change.action === 'ADD') includeRequiredParent(change.node?.parentNodeId)
        if (change.entityType === 'ENTRY' && change.action === 'ADD') includeRequiredParent(change.entry?.workItemId)
      }
    }

    const plannedNodeIds = new Set(expandedChanges
      .filter(({ change }) => change.entityType === 'NODE' && change.action === 'ADD')
      .map(({ change }) => change.targetId))
    const plannedEntryIds = new Set(expandedChanges
      .filter(({ change }) => change.entityType === 'ENTRY' && change.action === 'ADD')
      .map(({ change }) => change.targetId))
    const canonicalNodeIds = new Set(nodes.map((node) => node.id))
    const canonicalEntryIds = new Set(entries.map((entry) => entry.id))
    const entityWillExist = (type: string, id: string) => type === 'WORK_ITEM'
      ? canonicalNodeIds.has(id) || plannedNodeIds.has(id)
      : canonicalEntryIds.has(id) || plannedEntryIds.has(id)
    const readyChanges = decision === 'ACCEPT' ? expandedChanges.filter(({ change }) => {
      if (change.entityType !== 'EDGE' || change.action !== 'ADD') return true
      const relationship = change.relationship
      return Boolean(relationship
        && entityWillExist(relationship.fromEntityType, relationship.fromEntityId)
        && entityWillExist(relationship.toEntityType, relationship.toEntityId)
        && (!relationship.sourceEntryId || entityWillExist('ENTRY', relationship.sourceEntryId)))
    }) : expandedChanges
    const deferredCount = expandedChanges.length - readyChanges.length
    const nodeAddDepth = (change: GraphChangeProposalChange) => {
      let depth = 0
      let parentId = change.node?.parentNodeId
      const visited = new Set<string>()
      while (parentId && plannedNodeIds.has(parentId) && !visited.has(parentId)) {
        visited.add(parentId)
        depth += 1
        parentId = proposedNodeAddById.get(parentId)?.change.node?.parentNodeId
      }
      return depth
    }
    const ordered = [...readyChanges].sort((left, right) => {
      if (decision === 'REJECT') return left.change.sortIndex - right.change.sortIndex
      const weight = (change: GraphChangeProposalChange) => {
        if (change.entityType === 'NODE' && change.action === 'ADD') return nodeAddDepth(change)
        if (change.entityType === 'NODE' && change.action === 'UPDATE') return 50
        if (change.entityType === 'ENTRY' && change.action !== 'DELETE') return 100
        if (change.entityType === 'EDGE' && change.action !== 'DELETE') return 200
        if (change.entityType === 'EDGE') return 300
        if (change.entityType === 'ENTRY') return 400
        return 500
      }
      return weight(left.change) - weight(right.change) || left.change.sortIndex - right.change.sortIndex
    })
    if (ordered.length === 0) {
      toast.info(t('workspace.acceptItemsFirst'))
      return
    }
    setDecidingProposalChangeId(ordered[0].change.id)
    try {
      for (const { proposalId, change } of ordered) {
        await decideGraphChangeProposal(projectId, proposalId, change.id, change.entityType, decision)
      }
      await reloadTreeAndProposals(selectedNodeId ?? undefined)
      toast.success(decision === 'ACCEPT'
        ? deferredCount > 0
          ? t('workspace.changesAcceptedWithPending')
          : t('workspace.suggestionAccepted')
        : t('workspace.suggestionRejected'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.proposalDecisionFailed', { decision: decision.toLowerCase() }))
    } finally {
      setDecidingProposalChangeId(null)
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">{t('workspace.pageTitle')}</h1>
        </div>
        <div className="flex min-w-0 flex-1 items-center justify-center overflow-auto p-6">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      </div>
    )
  }

  if (!project || errorMessage) {
    return (
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
          <h1 className="text-xl font-semibold leading-none tracking-normal">{t('workspace.pageTitle')}</h1>
        </div>
        <div className="min-w-0 flex-1 overflow-auto p-4 md:p-6">
          <div className="rounded-md border bg-background">
            <Empty className="min-h-[50vh] border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FolderOpen />
                </EmptyMedia>
                <EmptyTitle>{t('workspace.projectNotFound')}</EmptyTitle>
              </EmptyHeader>
              <div className="flex justify-center">
                <NavLink to={workspaceDestination('/app/projects')} className={buttonVariants()}>
                  {t('workspace.backToProjects')}
                </NavLink>
              </div>
            </Empty>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center justify-between gap-3 border-b px-4 py-3 md:px-6">
        <h1 className="flex min-w-0 items-center gap-2 text-xl font-semibold leading-none tracking-normal">
          <NavLink to={workspaceDestination('/app/projects')} className="shrink-0 text-muted-foreground hover:text-foreground">
            {t('workspace.pageTitle')}
          </NavLink>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="flex min-w-0 items-center gap-1.5">
            <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="truncate">{formatProjectTitle(project, t('common.untitledProject'))}</span>
          </span>
        </h1>
        <Button render={<NavLink to={workspaceDestination(`/app/projects/${project.id}/settings`)} />} variant="ghost" size="icon-sm" aria-label={t('workspace.projectSettings')}>
          <Settings className="h-4 w-4" />
        </Button>
      </div>

      <ResizablePanelGroup
        orientation="horizontal"
        defaultLayout={workspacePanelLayout}
        onLayoutChanged={(layout, meta) => {
          if (!meta.isUserInteraction) return
          if (Number(layout['project-inspector']) < 10) return
          try {
            window.localStorage.setItem(workspacePanelLayoutStorageKey, JSON.stringify(layout))
          } catch {
            // Layout persistence is optional when browser storage is unavailable.
          }
        }}
        className="min-h-0 min-w-0 flex-1 overflow-hidden border-b"
      >
        <ResizablePanel id="project-tree" defaultSize="70" minSize="45">
          <section className="flex h-full min-h-0 flex-col overflow-hidden bg-background">
          <div className="flex min-h-12 shrink-0 items-center gap-2 border-b p-3">
            <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto overflow-y-hidden">
            <Popover>
              <PopoverTrigger
                render={(
                  <Button type="button" size="sm" variant={hasActiveWorkItemFilters ? 'default' : 'outline'} className="gap-2">
                    <Filter className="h-4 w-4" />
                    {t('workspace.filters')}{hasActiveWorkItemFilters ? ` (${appliedWorkItemFilterConditions.length + (appliedWorkItemSearchQuery ? 1 : 0)})` : ''}
                  </Button>
                )}
              />
              <PopoverContent align="start" className="w-[34rem] gap-0 p-0">
                <PopoverHeader className="border-b px-4 py-3"><PopoverTitle>{t('workspace.filterWorkItems')}</PopoverTitle></PopoverHeader>
                <div className="space-y-5 px-4 py-4">
                  <div className="space-y-2">
                    <label htmlFor="work-item-filter-search" className="block text-sm font-semibold">{t('common.search')}</label>
                    <div className="relative">
                      <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <Input id="work-item-filter-search" className="h-9 pl-10" value={workItemSearchQuery} onChange={(event) => setWorkItemSearchQuery(event.target.value)} placeholder={t('common.search')} />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="block text-sm font-semibold">{t('workspace.conditions')}</label>
                    <div className="space-y-2">
                      {workItemFilterConditions.map((condition, index) => (
                        <div key={condition.id} className="flex min-h-9 items-center gap-2">
                          {index === 0 ? (
                            <span className="w-14 shrink-0 text-xs font-medium text-muted-foreground">{t('workspace.where')}</span>
                          ) : (
                            <NativeSelect
                              className="h-9 w-14 shrink-0"
                              value={condition.operator}
                              onChange={(event) => setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, operator: event.target.value as WorkItemFilterOperator } : item))}
                              aria-label={t('workspace.conditionOperator')}
                            >
                              <NativeSelectOption value="AND">{t('workspace.and')}</NativeSelectOption>
                              <NativeSelectOption value="OR">{t('workspace.or')}</NativeSelectOption>
                            </NativeSelect>
                          )}
                          <NativeSelect
                            className="h-9 w-32"
                            value={condition.field}
                            onChange={(event) => {
                              const field = event.target.value as WorkItemFilterField
                              const value = field === 'STATUS' ? 'OPEN' : field === 'PRIORITY' ? 'MEDIUM' : field === 'DUE_DATE' ? 'OVERDUE' : ''
                              setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, field, value } : item))
                            }}
                          >
                            <NativeSelectOption value="STATUS">{t('common.status')}</NativeSelectOption>
                            <NativeSelectOption value="PRIORITY">{t('common.priority')}</NativeSelectOption>
                            <NativeSelectOption value="DUE_DATE">{t('common.dueDate')}</NativeSelectOption>
                            <NativeSelectOption value="ASSIGNEE">{t('workspace.assignee')}</NativeSelectOption>
                          </NativeSelect>
                          {condition.field === 'STATUS' ? (
                            <NativeSelect className="h-9 min-w-0 flex-1" value={condition.value} onChange={(event) => setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, value: event.target.value } : item))}>
                              {['OPEN', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'WAITING', 'ANSWERED', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((status) => <NativeSelectOption key={status} value={status}>{status.replaceAll('_', ' ')}</NativeSelectOption>)}
                            </NativeSelect>
                          ) : condition.field === 'PRIORITY' ? (
                            <NativeSelect className="h-9 min-w-0 flex-1" value={condition.value} onChange={(event) => setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, value: event.target.value } : item))}>
                              {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((priority) => <NativeSelectOption key={priority} value={priority}>{translatePriority(priority, t)}</NativeSelectOption>)}
                            </NativeSelect>
                          ) : condition.field === 'DUE_DATE' ? (
                            <NativeSelect className="h-9 min-w-0 flex-1" value={condition.value} onChange={(event) => setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, value: event.target.value } : item))}>
                              <NativeSelectOption value="OVERDUE">{t('workspace.overdue')}</NativeSelectOption><NativeSelectOption value="TODAY">{t('workspace.dueToday')}</NativeSelectOption><NativeSelectOption value="NEXT_7_DAYS">{t('workspace.next7Days')}</NativeSelectOption><NativeSelectOption value="NONE">{t('common.noDueDate')}</NativeSelectOption>
                            </NativeSelect>
                          ) : (
                            <NativeSelect className="h-9 min-w-0 flex-1" value={condition.value} onChange={(event) => setWorkItemFilterConditions((current) => current.map((item) => item.id === condition.id ? { ...item, value: event.target.value } : item))}>
                              <NativeSelectOption value="">{t('workspace.selectAssignee')}</NativeSelectOption>
                              {assigneeUserOptions.map((user) => <NativeSelectOption key={`user-${user.id}`} value={`USER:${user.id}`}>{user.label}</NativeSelectOption>)}
                              {assigneeTeamOptions.map((team) => <NativeSelectOption key={`team-${team.id}`} value={`TEAM:${team.id}`}>{team.label} (team)</NativeSelectOption>)}
                            </NativeSelect>
                          )}
                          <Button type="button" variant="ghost" size="icon-xs" onClick={() => setWorkItemFilterConditions((current) => current.filter((item) => item.id !== condition.id))} aria-label={t('workspace.removeCondition')}><X /></Button>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="flex items-center justify-between gap-2 border-t bg-muted/30 px-4 py-3">
                  <Button type="button" size="sm" variant="outline" className="gap-1.5" onClick={() => setWorkItemFilterConditions((current) => [...current, { id: `${Date.now()}-${Math.random()}`, field: 'STATUS', value: 'OPEN', operator: 'AND' }])}>
                    <Plus className="h-3.5 w-3.5" /> {t('workspace.addCondition')}
                  </Button>
                  <div className="flex items-center gap-1">
                    {hasDraftWorkItemFilters || hasActiveWorkItemFilters ? <Button type="button" size="sm" variant="ghost" onClick={() => void handleClearWorkItemFilters()} disabled={isRunningWorkItemFilters}>{t('common.clear')}</Button> : null}
                    <Button type="button" size="sm" onClick={() => void handleRunWorkItemFilters()} disabled={isRunningWorkItemFilters}>
                      {isRunningWorkItemFilters ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                      {t('workspace.runFilters')}
                    </Button>
                  </div>
                </div>
              </PopoverContent>
            </Popover>
            <Button
              type="button"
              size="sm"
              variant={createdSortDirection ? 'default' : 'outline'}
              className="gap-2"
              onClick={() => void handleRunCreatedSort()}
              disabled={isRunningCreatedSort}
              title={createdSortDirection === 'ASC' ? t('workspace.oldestFirst') : t('workspace.newestFirst')}
            >
              {isRunningCreatedSort ? <Loader2 className="h-4 w-4 animate-spin" /> : createdSortDirection === 'ASC' ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
              {t('workspace.created')}
            </Button>
            <div className="order-first">
            <DropdownMenu>
              <DropdownMenuTrigger
                render={(
                  <Button type="button" size="sm" className="gap-2" disabled={isSaving}>
                    <Plus className="h-4 w-4" />
                    {focusedNode ? t('workspace.addSubItem') : t('workspace.newItem')}
                  </Button>
                )}
              />
              <DropdownMenuContent align="start" className="w-48">
                <DropdownMenuItem onClick={() => void handleCreateNode(focusedNodeId ?? undefined, 'TASK')}>
                  <ListTodo className="h-4 w-4" /> {t('workspace.task')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => void handleCreateNode(focusedNodeId ?? undefined, 'QUESTION')}>
                  <CircleHelp className="h-4 w-4" /> {t('workspace.askQuestion')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => void handleCreateNode(focusedNodeId ?? undefined, 'APPROVAL')}>
                  <ClipboardCheck className="h-4 w-4" /> {t('workspace.requestApproval')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            </div>
            </div>
            <Tooltip>
              <TooltipTrigger
                render={(
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    className="shrink-0"
                    onClick={toggleInspectorPanel}
                    aria-label={isInspectorCollapsed ? t('workspace.showInspector') : t('workspace.hideInspector')}
                  />
                )}
              >
                {isInspectorCollapsed ? <PanelRightOpen className="h-4 w-4" /> : <PanelRightClose className="h-4 w-4" />}
              </TooltipTrigger>
              <TooltipContent>{isInspectorCollapsed ? t('workspace.showInspector') : t('workspace.hideInspector')}</TooltipContent>
            </Tooltip>
          </div>

          <div className="flex min-h-0 flex-1 flex-col p-2">
            {focusedNode ? (
              <div className="mb-2 flex min-h-9 flex-wrap items-center justify-between gap-2 rounded-md border bg-muted/30 px-2 py-1.5">
                <div className="flex min-w-0 items-center gap-1 text-sm">
                  <span className="shrink-0 text-muted-foreground">{t('workspace.focused')}</span>
                  <div className="flex min-w-0 items-center gap-1">
                    {focusedNodePath.map((node, index) => (
                      <span key={node.id} className="flex min-w-0 items-center gap-1">
                        {index > 0 ? <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" /> : null}
                        <button
                          type="button"
                          className={cn(
                            'min-w-0 truncate rounded-sm px-1 hover:bg-background',
                            index === focusedNodePath.length - 1 ? 'font-medium text-foreground' : 'text-muted-foreground',
                          )}
                          onClick={() => setFocusedNodeId(node.id)}
                          title={node.title}
                        >
                          {node.title}
                        </button>
                      </span>
                    ))}
                  </div>
                </div>
                <Button type="button" variant="outline" size="xs" onClick={() => setFocusedNodeId(null)}>
                  {t('workspace.exitFocus')}
                </Button>
              </div>
            ) : null}
            {filteredTree.length === 0 && filteredPendingProposalItems.length === 0 ? (
              <Empty className="min-h-64 border-0">
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <FolderOpen />
                  </EmptyMedia>
                  <EmptyTitle>{t('workspace.noItems')}</EmptyTitle>
                </EmptyHeader>
              </Empty>
            ) : (
              <div className="min-h-0 flex-1 overflow-auto">
                {filteredPendingProposalItems.length > 0 ? (
                  <section className="mb-4 space-y-1">
                    <div className="flex min-h-8 items-center gap-2 px-2 text-xs font-medium uppercase text-muted-foreground">
                      <span className="truncate">{t('workspace.pendingProposals')}</span>
                      <Badge variant="outline" className="ml-auto">
                        {filteredPendingProposalItems.length}
                      </Badge>
                    </div>
                    <div className="space-y-1">
                      {filteredPendingProposalItems.map((node) => (
                        <PendingProposalNodeRow
                          key={`${node.proposal.changeId}-${node.id}`}
                          node={node}
                          selectedNodeId={selectedNodeId}
                          decidingProposalChangeId={decidingProposalChangeId}
                          onSelect={selectNode}
                          onDecideProposal={(proposal, decision) => void handleDecideProposal(proposal, decision)}
                        />
                      ))}
                    </div>
                  </section>
                ) : null}
                <div className="space-y-1">
                    {filteredTree.map((node, rootIndex) => (
                      <TreeRow
                        key={node.id}
                        node={node}
                        depth={0}
                        expandedNodeIds={displayedExpandedNodeIds}
                        selectedNodeId={selectedNodeId}
                        highlightedNodeId={chatHighlightedNodeId}
                        selectedContextNodeIds={selectedContextNodeIds}
                        dimUnrelatedNodes={dimUnrelatedNodes}
                        isSaving={isSaving}
                        decidingProposalChangeId={decidingProposalChangeId}
                        loadedChildrenParentIds={loadedChildrenParentIds}
                        loadingChildrenNodeIds={loadingChildrenNodeIds}
                        childrenPageInfoByParentId={childrenPageInfoByParentId}
                        blockerUi={blockerUi}
                        entriesByWorkItemId={entriesByWorkItemId}
                        contentOrderByParent={contentOrderByParent}
                        acceptedAnswerByQuestionId={acceptedAnswerByQuestionId}
                        userLabels={userLabels}
                        teamLabels={teamLabels}
                        selectedEntryId={selectedEntryId}
                        isAiReviewAvailable={isLlmAvailable}
                        canReorder={canReorderContent}
                        canMoveUp={rootIndex > 0}
                        canMoveDown={rootIndex < filteredTree.length - 1}
                        onToggle={(nodeId) => void toggleNode(nodeId)}
                        onLoadMoreChildren={(parentId) => void loadMoreChildren(parentId)}
                        onSelect={selectNode}
                        onSelectEntry={selectEntry}
                        onAddChild={(parentId, type) => void handleCreateNode(parentId, type)}
                        onFocus={focusNode}
                        onMove={(node) => openMoveDialog(node)}
                        onDelete={(nextNodeId) => void handleDeleteNode(nextNodeId)}
                        onEditTitle={handleInlineTitleUpdate}
                        onCreateEntry={handleCreateEntry}
                        onReviewNewEntry={handleReviewNewEntryWithAi}
                        onAcceptNewEntryReview={handleAcceptNewEntryAiReview}
                        onRejectNewEntryReview={handleRejectNewEntryAiReview}
                        onUpdateEntry={handleUpdateEntry}
                        onReviewEntry={handleReviewEntryWithAi}
                        onAcceptEntryReview={handleAcceptEntryAiReview}
                        onRejectEntryReview={handleRejectEntryAiReview}
                        onAcceptAnswer={handleAcceptAnswer}
                        onMoveContentOrder={handleMoveContentItem}
                        onMoveInContentOrder={(offset) => { void handleMoveContentItem(null, 'WORK_ITEM', node.id, offset) }}
                        onDeleteEntry={handleDeleteEntry}
                        autoEditTitleNodeId={autoEditTitleNodeId}
                        onAutoEditTitleStarted={() => setAutoEditTitleNodeId(null)}
                        onDecideProposal={(proposal, decision) => void handleDecideProposal(proposal, decision)}
                      />
                    ))}
                  </div>
                {rootPageInfo && rootPageInfo.page + 1 < rootPageInfo.totalPages ? (
                  <div className="flex justify-center py-2">
                    <Button type="button" size="sm" variant="outline" className="gap-2" onClick={() => void loadMoreRootNodes()} disabled={isLoadingMoreRoots}>
                      {isLoadingMoreRoots ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                      {t('workspace.loadMoreRoots')}
                    </Button>
                  </div>
                ) : null}
              </div>
            )}
          </div>
          </section>
        </ResizablePanel>

        <ResizableHandle withHandle />

        <ResizablePanel
          id="project-inspector"
          defaultSize="30"
          minSize="24"
          maxSize="55"
          collapsible
          collapsedSize={0}
          panelRef={inspectorPanelRef}
        >
          <aside className="flex h-full min-h-0 flex-col overflow-hidden bg-background">
            <div className="flex min-h-11 shrink-0 items-center justify-between gap-3 border-b px-3 py-2">
              <div className="inline-flex rounded-md border bg-background p-0.5" role="group" aria-label={t('workspace.inspectorMode')}>
                <Button
                  type="button"
                  size="sm"
                  variant={inspectorMode === 'task' ? 'default' : 'ghost'}
                  className={cn('gap-1 rounded-sm', inspectorMode === 'task' ? 'shadow-xs' : 'text-muted-foreground')}
                  disabled={!selectedNode && !selectedEntry}
                  onClick={() => setInspectorMode('task')}
                >
                  <FileText className="h-3.5 w-3.5" />
                  {t('workspace.details')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant={inspectorMode === 'history' ? 'default' : 'ghost'}
                  className={cn('gap-1 rounded-sm', inspectorMode === 'history' ? 'shadow-xs' : 'text-muted-foreground')}
                  disabled={!selectedNode}
                  onClick={() => setInspectorMode('history')}
                >
                  <History className="h-3.5 w-3.5" />
                  {t('workspace.history')}
                </Button>
              </div>
              {selectedNode ? (
                <div className="flex items-center gap-2">
                  <Tooltip>
                    <TooltipTrigger
                      render={
                        <Button
                          type="button"
                          size="icon-sm"
                          variant="outline"
                          disabled={Boolean(selectedNode.proposal) || isExpandingSelectedSubtree}
                          onClick={() => void handleExpandSelectedSubtree()}
                          aria-label={t('workspace.expandAll')}
                        />
                      }
                    >
                      {isExpandingSelectedSubtree ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronDown className="h-4 w-4" />}
                    </TooltipTrigger>
                    <TooltipContent>{t('workspace.expandAllHelp')}</TooltipContent>
                  </Tooltip>
                  <Tooltip>
                    <TooltipTrigger
                      render={
                        <Button
                          type="button"
                          size="icon-sm"
                          variant="outline"
                          disabled={Boolean(selectedNode.proposal) || isExpandingSelectedSubtree}
                          onClick={handleCollapseSelectedSubtree}
                          aria-label={t('workspace.collapse')}
                        />
                      }
                    >
                      <ChevronRight className="h-4 w-4" />
                    </TooltipTrigger>
                    <TooltipContent>{t('workspace.collapseHelp')}</TooltipContent>
                  </Tooltip>
                  <Tooltip>
                    <TooltipTrigger
                      render={
                        <Button
                          type="button"
                          size="icon-sm"
                          variant="outline"
                          className={cn(isSelectedWorkItemSubscribed && 'border-sky-200 bg-sky-50 text-sky-700 hover:bg-sky-100')}
                          disabled={Boolean(selectedNode.proposal)}
                          onClick={() => void handleToggleSelectedWorkItemSubscription()}
                          aria-label={isSelectedWorkItemSubscribed ? t('workspace.stopSubscription') : t('workspace.subscribe')}
                        />
                      }
                    >
                      {isSelectedWorkItemSubscribed ? (
                        <BookmarkCheck className="h-4 w-4" />
                      ) : (
                        <Bookmark className="h-4 w-4" />
                      )}
                    </TooltipTrigger>
                    <TooltipContent>{isSelectedWorkItemSubscribed ? t('workspace.stopSubscription') : t('workspace.subscribe')}</TooltipContent>
                  </Tooltip>
                </div>
              ) : null}
            </div>

            <div className="min-h-0 flex-1 overflow-hidden">
              {inspectorMode === 'task' && selectedNode ? (
              <form className="flex h-full min-h-0 flex-col overflow-hidden" onSubmit={handleSaveNode}>
                <div className="min-h-0 flex-1 overflow-auto">
                  <WorkspaceProposalPanel
                    node={selectedNode}
                    changes={selectedWorkspaceProposalChanges}
                    isDeciding={Boolean(decidingProposalChangeId)}
                    nodeTitleById={nodeTitleById}
                    onDecide={(changes, decision) => void handleDecideWorkspaceChanges(changes, decision)}
                  />

                  <div className="divide-y border-b">
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.type')}</label>
                      <NativeSelect value={form.type} onChange={(event) => handleTypeChange(event.target.value)} disabled={isSaving || Boolean(selectedNode.proposal)}>
                        {workItemTypeOptions.map((type) => (
                          <NativeSelectOption key={type} value={type}>{translateWorkItemType(type, t)}</NativeSelectOption>
                        ))}
                      </NativeSelect>
                    </div>
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)]">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.title')}</label>
                      <Textarea
                        rows={3}
                        value={form.title}
                        onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
                      />
                    </div>

                    {workItemInspectorReview ? (
                      <section className="space-y-3 bg-primary/5 px-4 py-3">
                        <div className="flex items-center gap-2 text-sm font-semibold text-primary"><Bot className="h-4 w-4" /> {t('workspace.aiSuggestion')}</div>
                        <div className="grid gap-1 text-sm">
                          <span>{t('common.title')}: <s>{workItemInspectorReview.originalTitle}</s> → <strong>{workItemInspectorReview.proposedTitle}</strong></span>
                          <span>{t('common.type')}: {translateWorkItemType(form.type, t)} → <strong>{translateWorkItemType(workItemInspectorReview.proposedType, t)}</strong></span>
                          <span>{t('common.status')}: {translateStatus(normalizedStatus(formFieldValue(form.fields, 'status')) || defaultStatusForType(form.type), t)} → <strong>{translateStatus(workItemInspectorReview.proposedStatus, t)}</strong></span>
                          <span>{t('common.dueDate')}: {String(formFieldValue(form.fields, 'dueDate') ?? t('common.notSet'))} → <strong>{workItemInspectorReview.proposedDueDate ?? t('common.notSet')}</strong></span>
                          <span>{t('common.priority')}: {formFieldValue(form.fields, 'priority') ? translatePriority(String(formFieldValue(form.fields, 'priority')), t) : t('common.notSet')} → <strong>{workItemInspectorReview.proposedPriority ? translatePriority(workItemInspectorReview.proposedPriority, t) : t('common.notSet')}</strong></span>
                          <span>{t('common.assignees')}: <strong>{workItemInspectorReview.proposedAssignees.length || t('common.none')}</strong></span>
                        </div>
                        {workItemInspectorReview.proposedBlockers.length > 0 ? (
                          <div className="space-y-1.5">
                            <span className="text-xs font-medium text-muted-foreground">{t('workspace.suggestedBlockers')}</span>
                            <div className="grid gap-1.5">
                              {workItemInspectorReview.proposedBlockers.map((blocker) => (
                                <div key={blocker.workItemId} className="flex items-start gap-2 rounded-md border border-destructive/25 bg-background/70 px-2 py-1.5 text-sm">
                                  <OctagonAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-destructive" />
                                  <div className="min-w-0">
                                    <div className="truncate font-medium">{blockerTitleById.get(blocker.workItemId) ?? blocker.workItemId}</div>
                                    {blocker.reason ? <div className="mt-0.5 break-words text-xs text-muted-foreground">{blocker.reason}</div> : null}
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        ) : null}
                        {workItemInspectorReview.rationale ? <p className="text-xs text-muted-foreground">{workItemInspectorReview.rationale}</p> : null}
                        <div className="flex gap-2">
                          <Input className="bg-white" value={workItemInspectorReviewFeedback} onChange={(event) => setWorkItemInspectorReviewFeedback(event.target.value)} placeholder={t('workspace.tellAi')} disabled={isSaving} />
                          <Button type="button" variant="outline" disabled={isSaving} onClick={() => void handleRefineWorkItemAiReview()}>{t('workspace.askAgain')}</Button>
                        </div>
                        <div className="flex gap-2">
                          <Button type="button" disabled={isSaving} onClick={() => applyWorkItemAiReview(workItemInspectorReview)}>{t('workspace.applyToDraft')}</Button>
                          {workItemInspectorReview.proposedBlockers.length > 0 ? (
                            <Button type="button" variant="outline" disabled={isSaving} onClick={() => void handleAddSuggestedBlockers(workItemInspectorReview)}>{t('workspace.addSuggestedBlockers')}</Button>
                          ) : null}
                          <Button type="button" variant="outline" disabled={isSaving} onClick={() => { setWorkItemInspectorReview(null); setWorkItemInspectorReviewFeedback('') }}>{t('workspace.rejectSuggestion')}</Button>
                        </div>
                      </section>
                    ) : null}

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.status')}</label>
                      <NativeSelect
                        value={normalizedStatus(formFieldValue(form.fields, 'status')) || defaultStatusForType(form.type)}
                        onChange={(event) => updateManagedWorkItemField('status', 'Status', 'text', event.target.value, true)}
                      >
                        {statusOptionsForType(form.type, t).map((status) => (
                          <NativeSelectOption key={status.value} value={status.value}>{status.label}</NativeSelectOption>
                        ))}
                      </NativeSelect>
                    </div>

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.dueDate')}</label>
                      <Input
                        type="date"
                        value={String(formFieldValue(form.fields, 'dueDate') ?? '')}
                        onChange={(event) => updateManagedWorkItemField('dueDate', 'Due date', 'date', event.target.value, true)}
                      />
                    </div>

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.priority')}</label>
                      <NativeSelect
                        value={String(formFieldValue(form.fields, 'priority') ?? '')}
                        onChange={(event) => updateManagedWorkItemField('priority', 'Priority', 'text', event.target.value, true)}
                      >
                        <NativeSelectOption value="">{t('common.noPriority')}</NativeSelectOption>
                        <NativeSelectOption value="LOW">{t('priority.low')}</NativeSelectOption>
                        <NativeSelectOption value="MEDIUM">{t('priority.medium')}</NativeSelectOption>
                        <NativeSelectOption value="HIGH">{t('priority.high')}</NativeSelectOption>
                        <NativeSelectOption value="URGENT">{t('priority.urgent')}</NativeSelectOption>
                      </NativeSelect>
                    </div>

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)]">
                      <label className="pt-2 text-sm font-semibold text-muted-foreground">{t('common.assignees')}</label>
                      <div className="grid gap-3">
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                          <NativeSelect
                            className="w-full sm:w-28"
                            value={newAssigneeType}
                            onChange={(event) => {
                              setNewAssigneeType(event.target.value as 'USER' | 'TEAM')
                              setNewAssigneeId('')
                            }}
                          >
                            <NativeSelectOption value="USER">{t('common.user')}</NativeSelectOption>
                            <NativeSelectOption value="TEAM">{t('common.team')}</NativeSelectOption>
                          </NativeSelect>
                          <NativeSelect
                            className="w-full sm:flex-1"
                            value={newAssigneeId}
                            onChange={(event) => setNewAssigneeId(event.target.value)}
                            disabled={availableAssigneeOptions.length === 0}
                          >
                            <NativeSelectOption value="">
                              {availableAssigneeOptions.length === 0
                                ? t('workspace.noAssigneesAvailable', { type: newAssigneeType === 'USER' ? t('common.users') : t('common.teams') })
                                : t('workspace.selectAssigneeType', { type: newAssigneeType === 'USER' ? t('common.user') : t('common.team') })}
                            </NativeSelectOption>
                            {availableAssigneeOptions.map((option) => (
                              <NativeSelectOption key={option.id} value={option.id}>{option.label}</NativeSelectOption>
                            ))}
                          </NativeSelect>
                          <Button type="button" className="gap-2" disabled={!newAssigneeId} onClick={handleAddAssignee}>
                            <Plus className="h-4 w-4" />
                            {t('common.add')}
                          </Button>
                        </div>

                        {assignedUserIds.length === 0 && assignedTeamIds.length === 0 ? (
                          <div className="rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">{t('workspace.noAssignees')}</div>
                        ) : (
                          <div className="space-y-2">
                            {assignedUserIds.map((userId) => (
                              <div key={`user-${userId}`} className="flex min-w-0 items-center justify-between gap-2 rounded-md border px-3 py-2">
                                <div className="flex min-w-0 items-center gap-2">
                                  <Badge variant="outline" className="shrink-0 border-sky-200 bg-sky-50 font-medium text-sky-700 dark:border-sky-900 dark:bg-sky-950/50 dark:text-sky-300">{t('common.user')}</Badge>
                                  <span className="truncate text-sm font-medium">{assigneeUserLabelById.get(userId) ?? userId}</span>
                                </div>
                                <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeAssignee('USER', userId)} aria-label={t('workspace.removeAssignee')} title={t('common.remove')}>
                                  <X className="h-4 w-4" />
                                </Button>
                              </div>
                            ))}
                            {assignedTeamIds.map((teamId) => (
                              <div key={`team-${teamId}`} className="flex min-w-0 items-center justify-between gap-2 rounded-md border px-3 py-2">
                                <div className="flex min-w-0 items-center gap-2">
                                  <Badge variant="outline" className="shrink-0 border-violet-200 bg-violet-50 font-medium text-violet-700 dark:border-violet-900 dark:bg-violet-950/50 dark:text-violet-300">{t('common.team')}</Badge>
                                  <span className="truncate text-sm font-medium">{assigneeTeamLabelById.get(teamId) ?? teamId}</span>
                                </div>
                                <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeAssignee('TEAM', teamId)} aria-label={t('workspace.removeAssignee')} title={t('common.remove')}>
                                  <X className="h-4 w-4" />
                                </Button>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-start">
                      <label className="pt-2 text-sm font-semibold text-muted-foreground">{t('common.relationship')}</label>
                      <div className="grid min-w-0 gap-2">
                        {(workItemRelationshipsByNodeId.get(selectedNode.id) ?? []).map((relationship) => {
                          const isOutgoing = relationship.fromEntityId === selectedNode.id
                          const relatedNodeId = isOutgoing ? relationship.toEntityId : relationship.fromEntityId
                          const relatedTitle = blockerTitleById.get(relatedNodeId) ?? relatedNodeId
                          const isBlocker = relationship.type === 'BLOCKED_BY'
                          return (
                            <span key={relationship.id} className={cn('flex min-w-0 items-center gap-2 rounded-md border py-1 pl-2 pr-1 text-sm', isBlocker && 'border-destructive/30 bg-destructive/5')}>
                              {isBlocker
                                ? <OctagonAlert className="h-3.5 w-3.5 shrink-0 text-destructive" />
                                : <MoveRight className={cn('h-3.5 w-3.5 shrink-0 text-muted-foreground', !isOutgoing && 'rotate-180')} />}
                              <Badge variant="outline" className={cn('shrink-0 font-medium', relationshipTypeBadgeClass(relationship.type))}>
                                {translateRelationshipType(relationship.type, t)}
                              </Badge>
                              <span className="min-w-0 flex-1 truncate text-muted-foreground" title={relationship.reason ?? undefined}>
                                {isOutgoing ? '→' : '←'} <span className="font-medium text-foreground">{relatedTitle}</span>
                              </span>
                              <Button
                                type="button"
                                size="icon-xs"
                                variant="ghost"
                                className="ml-auto"
                                disabled={isSavingBlocker}
                                onClick={() => void handleRemoveWorkItemRelationship(relationship.id)}
                                aria-label={t('workspace.removeRelationshipWith', { title: relatedTitle })}
                                title={t('workspace.removeRelationship')}
                              >
                                <X />
                              </Button>
                            </span>
                          )
                        })}
                        <div>
                          <WorkItemRelationshipPopover
                            nodeId={selectedNode.id}
                            relationshipUi={workItemRelationshipUi}
                            trigger={(
                              <Button type="button" size="sm" className="gap-1" disabled={Boolean(selectedNode.proposal)}>
                                <Plus className="h-3.5 w-3.5" /> {t('workspace.addRelationship')}
                              </Button>
                            )}
                          />
                        </div>
                      </div>
                    </div>

                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <span className="text-sm font-semibold text-muted-foreground">{t('common.created')}</span>
                      <span className="text-sm">{selectedNode.createdAt ? formatActivityDate(selectedNode.createdAt) : '—'}</span>
                    </div>
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <span className="text-sm font-semibold text-muted-foreground">{t('common.updated')}</span>
                      <span className="text-sm">{selectedNode.updatedAt ? formatActivityDate(selectedNode.updatedAt) : '—'}</span>
                    </div>
                  </div>

                </div>

                <div className="z-10 mt-auto flex shrink-0 flex-col gap-1.5 border-t bg-background/95 px-4 py-2.5 backdrop-blur">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-2">
                      <Button type="submit" className="shrink-0 gap-2" disabled={isSaving || Boolean(selectedNode.proposal) || !hasUnsavedWorkItemChanges} aria-label={t('common.save')} title={t('common.save')}>
                        {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                        {t('common.save')}
                      </Button>
                      {isLlmAvailable ? (
                        <Button type="button" variant="outline" className="shrink-0 gap-2" disabled={isSaving || Boolean(selectedNode.proposal)} onClick={() => void handleReviewWorkItemWithAi()}>
                          <Bot className="h-4 w-4" />
                          {t('workspace.aiReview')}
                        </Button>
                      ) : null}
                    </div>
                    <div className="ml-auto flex shrink-0 items-center gap-1">
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          render={(
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              disabled={isSaving || Boolean(selectedNode.proposal)}
                              aria-label={t('workspace.itemActions')}
                              title={t('common.actions')}
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          )}
                        />
                        <DropdownMenuContent align="end" className="w-44">
                          <DropdownMenuItem disabled={!canReorderSelectedContent} onClick={() => void handleMoveSelectedNodeByOffset(-1)}>
                            <ArrowUp className="h-4 w-4" />
                            {t('workspace.moveUp')}
                          </DropdownMenuItem>
                          <DropdownMenuItem disabled={!canReorderSelectedContent} onClick={() => void handleMoveSelectedNodeByOffset(1)}>
                            <ArrowDown className="h-4 w-4" />
                            {t('workspace.moveDown')}
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => void handleCreateNode(selectedNode.id)}>
                            <Plus className="h-4 w-4" />
                            {t('workspace.addSubItem')}
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => openMoveDialog()}>
                            <MoveRight className="h-4 w-4" />
                            {t('workspace.move')}
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => focusNode(selectedNode)}>
                            <Focus className="h-4 w-4" />
                            {t('workspace.focus')}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                      <DeleteConfirmPopover
                        title={t('workspace.deleteItem')}
                        description={t('workspace.deleteItemDescription')}
                        disabled={isSaving || Boolean(selectedNode.proposal)}
                        trigger={(
                          <Button type="button" variant="destructive" size="icon-sm" disabled={isSaving || Boolean(selectedNode.proposal)} aria-label={t('workspace.deleteItem')}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        )}
                        onConfirm={() => handleDeleteNode(selectedNode.id)}
                      />
                    </div>
                  </div>
                  <div className={cn('flex items-center gap-1.5 text-xs', hasUnsavedWorkItemChanges ? 'font-medium text-amber-700' : 'text-muted-foreground')} aria-live="polite">
                    {hasUnsavedWorkItemChanges ? <CircleAlert className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
                    <span>{hasUnsavedWorkItemChanges ? t('workspace.unsaved') : t('workspace.saved')}</span>
                  </div>
                </div>
              </form>
              ) : null}

              {inspectorMode === 'history' && selectedNode && projectId ? (
                <WorkItemHistoryPanel key={selectedNode.id} projectId={projectId} workItemId={selectedNode.id} userLabels={userLabels} />
              ) : null}

              {inspectorMode === 'task' && selectedEntry ? (
                <div className="flex h-full min-h-0 flex-col overflow-hidden">
                  <div className="min-h-0 flex-1 overflow-auto">
                    <div className="divide-y border-b">
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.type')}</label>
                      <NativeSelect value={entryInspectorType} onChange={(event) => setEntryInspectorType(event.target.value)} disabled={isSaving || Boolean(entryInspectorReview)}>
                        {entryTypeOptions.map((type) => <NativeSelectOption key={type} value={type}>{translateEntryType(type, t)}</NativeSelectOption>)}
                      </NativeSelect>
                    </div>
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <span className="text-sm font-semibold text-muted-foreground">{t('common.author')}</span>
                      <span className="text-sm">{entryAuthorLabel(selectedEntry, userLabels, t('common.unknownUser'))}</span>
                    </div>
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)]">
                      <label className="text-sm font-semibold text-muted-foreground">{t('common.content')}</label>
                      <Textarea
                        className="min-h-52 resize-y bg-white"
                        value={entryInspectorBody}
                        onChange={(event) => setEntryInspectorBody(event.target.value)}
                        disabled={isSaving || Boolean(entryInspectorReview)}
                        aria-label={t('workspace.updateContent')}
                      />
                    </div>
                    {entryInspectorReview ? (
                      <section className="space-y-3 bg-primary/5 px-4 py-3">
                        <div className="flex items-center gap-2 text-sm font-semibold text-primary"><Bot className="h-4 w-4" /> {t('workspace.aiSuggestion')}</div>
                        <div className="space-y-1">
                          <p className="text-xs font-medium text-muted-foreground">{t('workspace.yourDraft')}</p>
                          <p className="whitespace-pre-wrap break-words rounded-sm bg-background/70 p-2 text-sm">{entryInspectorReview.originalBody}</p>
                        </div>
                        <div className="space-y-1">
                          <p className="text-xs font-medium text-primary">{t('workspace.suggested')}</p>
                          <p className="whitespace-pre-wrap break-words rounded-sm bg-background/70 p-2 text-sm">{entryInspectorReview.proposedBody}</p>
                        </div>
                        {entryInspectorReview.proposedType && entryInspectorReview.proposedType !== (entryInspectorReview.entryType ?? entryInspectorType) ? (
                          <p className="text-sm text-primary">{t('workspace.classification')}: {translateEntryType(entryInspectorReview.entryType ?? entryInspectorType, t)} → {translateEntryType(entryInspectorReview.proposedType, t)}</p>
                        ) : null}
                        {entryInspectorReview.rationale ? <p className="whitespace-pre-wrap break-words text-xs text-muted-foreground">{entryInspectorReview.rationale}</p> : null}
                        <div className="flex gap-2">
                          <Input className="bg-white" value={entryInspectorReviewFeedback} onChange={(event) => setEntryInspectorReviewFeedback(event.target.value)} placeholder={t('workspace.tellAi')} disabled={isSaving} />
                          <Button type="button" variant="outline" disabled={isSaving} onClick={() => void handleRefineInspectorEntryReview()}>{t('workspace.askAgain')}</Button>
                        </div>
                      </section>
                    ) : null}
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <span className="text-sm font-semibold text-muted-foreground">{t('common.created')}</span>
                      <span className="text-sm">{formatActivityDate(selectedEntry.createdAt)}</span>
                    </div>
                    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center">
                      <span className="text-sm font-semibold text-muted-foreground">{t('common.updated')}</span>
                      <span className="text-sm">{selectedEntry.updatedAt ? formatActivityDate(selectedEntry.updatedAt) : '—'}</span>
                    </div>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center justify-between gap-3 border-t bg-background px-4 py-3">
                    {entryInspectorReview ? (
                      <>
                        <Button type="button" className="gap-2" disabled={isSaving} onClick={() => void handleAcceptInspectorEntryReview()}>
                          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} {t('workspace.acceptSuggestionButton')}
                        </Button>
                        <Button type="button" variant="outline" disabled={isSaving} onClick={keepEditingInspectorEntrySuggestion}>{t('workspace.keepEditing')}</Button>
                        <Button type="button" variant="outline" disabled={isSaving} onClick={() => void handleRejectInspectorEntryReview()}>{t('workspace.rejectSuggestion')}</Button>
                      </>
                    ) : (
                      <div className="flex items-center gap-2">
                        <Button type="button" className="gap-2" disabled={isSaving} onClick={() => void handleSaveInspectorEntry()}>
                          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} {t('common.save')}
                        </Button>
                        {isLlmAvailable ? (
                          <Button type="button" variant="outline" className="gap-2" disabled={isSaving} onClick={() => void handleReviewInspectorEntryWithAi()}>
                            <Bot className="h-4 w-4" /> {t('workspace.aiReview')}
                          </Button>
                        ) : null}
                      </div>
                    )}
                  </div>
                </div>
              ) : null}

              {inspectorMode === 'task' && !selectedNode && !selectedEntry ? (
                <Empty className="min-h-64 border-0">
                  <EmptyHeader>
                    <EmptyMedia variant="icon">
                      <FileText />
                    </EmptyMedia>
                    <EmptyTitle>{t('workspace.selectItem')}</EmptyTitle>
                  </EmptyHeader>
                </Empty>
              ) : null}

            </div>
          </aside>
        </ResizablePanel>
      </ResizablePanelGroup>

      <Dialog open={isMoveDialogOpen} onOpenChange={setIsMoveDialogOpen}>
        <DialogContent className="min-w-0 sm:max-w-2xl">
          <DialogHeader className="-mx-6 -mt-6 border-b px-6 pt-6 pb-5">
            <DialogTitle className="text-xl font-semibold">{t('workspace.moveItem')}</DialogTitle>
            <DialogDescription>{selectedNode ? t('workspace.choosePlacement', { title: selectedNode.title }) : t('workspace.chooseThisPlacement')}</DialogDescription>
          </DialogHeader>

          <div className="min-w-0 space-y-6">
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('workspace.moveBefore')}</label>
              <Input value={moveQuery} onChange={(event) => setMoveQuery(event.target.value)} placeholder={t('common.search')} />
            </div>
            <div className="min-w-0 w-full max-w-full max-h-80 overflow-x-hidden overflow-y-auto rounded-md border p-1">
              <button
                type="button"
                className={cn(
                  'flex min-h-8 w-full items-center gap-2 rounded-sm px-2 text-left text-sm hover:bg-muted',
                  moveTargetContentKey === '' ? 'bg-muted text-foreground' : 'text-muted-foreground',
                )}
                onClick={() => setMoveTargetContentKey('')}
              >
                <FolderOpen className="h-4 w-4" />
                {t('workspace.endOfProjectLevel')}
              </button>

              {moveTargetOptions.length === 0 ? (
                <div className="px-2 py-8 text-center text-sm text-muted-foreground">{t('workspace.noPlacementTargets')}</div>
              ) : (
                moveTargetOptions.map((item) => (
                  <button
                    key={contentEntityKey(item.entityType, item.entityId)}
                    type="button"
                    className={cn(
                      'flex min-h-8 min-w-0 w-full max-w-full items-center gap-2 overflow-hidden rounded-sm px-2 text-left text-sm hover:bg-muted',
                      moveTargetContentKey === contentEntityKey(item.entityType, item.entityId) ? 'bg-muted text-foreground' : 'text-muted-foreground',
                    )}
                    style={{ paddingLeft: `${item.depth * 16 + 8}px` }}
                    onClick={() => setMoveTargetContentKey(contentEntityKey(item.entityType, item.entityId))}
                  >
                    {item.entityType === 'WORK_ITEM' ? <FileText className="h-4 w-4 shrink-0" /> : <MessageSquarePlus className="h-4 w-4 shrink-0" />}
                    <span className="min-w-0 flex-1 truncate">{item.label}</span>
                    <span className="max-w-24 shrink-0 truncate text-xs text-muted-foreground">{item.detail}</span>
                  </button>
                ))
              )}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setIsMoveDialogOpen(false)} disabled={isSaving}>
              Cancel
            </Button>
            <Button type="button" className="gap-2" onClick={() => void handleMoveNode()} disabled={isSaving}>
              {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <MoveRight className="h-4 w-4" />}
              Move
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
