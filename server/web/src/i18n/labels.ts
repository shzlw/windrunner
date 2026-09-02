import type { TFunction } from 'i18next'

function humanize(value: string) {
  return value.trim().replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())
}

function translatedLabel(value: string, t: TFunction, keys: Record<string, string>) {
  const key = keys[value.trim().toUpperCase()]
  return key ? t(key) : humanize(value)
}

export function translateWorkItemType(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    NOTE: 'workItemType.note',
    TASK: 'workItemType.task',
    QUESTION: 'workItemType.question',
    APPROVAL: 'workItemType.approval',
    REVIEW: 'workItemType.review',
    DECISION: 'workItemType.decision',
  })
}

export function translateEntryType(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    COMMENT: 'entryType.comment',
    INFORMATION: 'entryType.information',
    ANSWER: 'entryType.answer',
    EVIDENCE: 'entryType.evidence',
    PROPOSAL: 'entryType.proposal',
    RESOLUTION: 'entryType.resolution',
  })
}

export function translateRelationshipType(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    BLOCKED_BY: 'relationshipType.blockedBy',
    DEPENDS_ON: 'relationshipType.dependsOn',
    RELATED_TO: 'relationshipType.relatedTo',
    ANSWERS: 'relationshipType.answers',
    SUPPORTS: 'relationshipType.supports',
    CONTRADICTS: 'relationshipType.contradicts',
    RESOLVES: 'relationshipType.resolves',
    SUPERSEDES: 'relationshipType.supersedes',
  })
}

export function translatePriority(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    LOW: 'priority.low',
    MEDIUM: 'priority.medium',
    HIGH: 'priority.high',
    URGENT: 'priority.urgent',
  })
}

export function translateRole(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    ADMIN: 'role.admin',
    USER: 'role.user',
    OWNER: 'role.owner',
    MEMBER: 'role.member',
    VIEWER: 'role.viewer',
    EDITOR: 'role.editor',
    TEAM_OWNER: 'role.teamOwner',
    TEAM_MEMBER: 'role.teamMember',
  })
}

export function translateStatus(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    OPEN: 'status.open',
    IN_PROGRESS: 'status.inProgress',
    BLOCKED: 'status.blocked',
    WAITING: 'status.waiting',
    ANSWERED: 'status.answered',
    PENDING: 'status.pending',
    APPROVED: 'status.approved',
    REJECTED: 'status.rejected',
    DONE: 'status.done',
    DECIDED: 'status.decided',
    CANCELLED: 'status.cancelled',
    ACTIVE: 'status.active',
    INACTIVE: 'status.inactive',
    REVOKED: 'status.revoked',
  })
}

export function translateAuditAction(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    CREATE: 'audit.actionCreate',
    UPDATE: 'audit.actionUpdate',
    DELETE: 'audit.actionDelete',
    LOGIN_SUCCESS: 'audit.actionLoginSuccess',
    LOGIN_FAILURE: 'audit.actionLoginFailure',
  })
}

export function translateProposalAction(value: string, t: TFunction) {
  return translatedLabel(value, t, {
    ADD: 'workspace.actionAdd',
    UPDATE: 'workspace.actionUpdate',
    DELETE: 'workspace.actionDelete',
  })
}
