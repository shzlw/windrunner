import { useEffect, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ChevronLeft, ChevronRight, FileClock, Loader2, Search, X } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import { listAuditLogs, type AuditLog } from '@/lib/api'

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function entityTypeLabel(entityType: string, t: TFunction) {
  const key = {
    AUTH: 'audit.authentication',
    API_KEY: 'audit.apiKey',
    PROJECT: 'common.project',
    TEAM: 'common.team',
    TEAM_JOIN_REQUEST: 'audit.teamJoinRequest',
    USER: 'common.user',
    WORK_ITEM: 'common.workItem',
    ENTRY: 'common.entry',
    RELATIONSHIP: 'common.relationship',
  }[entityType]
  return key ? t(key) : entityType
}

function entityDisplayName(auditLog: AuditLog, t: TFunction) {
  if (auditLog.entityDisplayName?.trim()) {
    return auditLog.entityDisplayName.trim()
  }
  if (!auditLog.entityId) {
    return entityTypeLabel(auditLog.entityType, t)
  }
  return t('audit.unavailableRecord')
}

function projectDisplayName(auditLog: AuditLog, t: TFunction) {
  return auditLog.projectName?.trim() || (auditLog.projectId ? t('audit.unavailableProject') : t('common.notSet'))
}

function actorDisplayName(auditLog: AuditLog, t: TFunction) {
  return auditLog.actorDisplayName?.trim() || (auditLog.actorUserId ? t('audit.unavailableUser') : t('audit.system'))
}

function formatJson(value: string | null) {
  if (!value) {
    return null
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function actionVariant(action: string) {
  if (action === 'DELETE' || action === 'LOGIN_FAILURE') {
    return 'destructive' as const
  }
  if (action === 'CREATE' || action === 'LOGIN_SUCCESS') {
    return 'secondary' as const
  }
  return 'outline' as const
}

function actionLabel(action: string, t: TFunction) {
  const key = {
    CREATE: 'audit.actionCreate',
    UPDATE: 'audit.actionUpdate',
    DELETE: 'audit.actionDelete',
    LOGIN_SUCCESS: 'audit.actionLoginSuccess',
    LOGIN_FAILURE: 'audit.actionLoginFailure',
  }[action]
  return key ? t(key) : action
}

function outcomeLabel(outcome: string, t: TFunction) {
  if (outcome === 'SUCCESS') return t('audit.success')
  if (outcome === 'FAILURE') return t('audit.failure')
  return outcome
}

export default function AuditLogsPage() {
  const { t } = useTranslation()
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([])
  const [selectedAuditLogId, setSelectedAuditLogId] = useState<string | null>(null)
  const [selectedAuditLog, setSelectedAuditLog] = useState<AuditLog | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [pageSize, setPageSize] = useState(25)
  const [query, setQuery] = useState('')

  async function loadPage(nextPage: number) {
    setIsListLoading(true)

    try {
      const data = await listAuditLogs(nextPage, pageSize)
      setAuditLogs(data.items)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        setSelectedAuditLogId(null)
        setSelectedAuditLog(null)
        return
      }

      if (selectedAuditLogId && !data.items.some((auditLog) => auditLog.id === selectedAuditLogId)) {
        setSelectedAuditLogId(null)
        setSelectedAuditLog(null)
        setIsSheetOpen(false)
      }
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : t('audit.failedLoad'))
      setAuditLogs([])
      setTotalPages(0)
      setSelectedAuditLogId(null)
      setSelectedAuditLog(null)
    } finally {
      setIsListLoading(false)
    }
  }

  function selectAuditLog(auditLog: AuditLog) {
    setSelectedAuditLogId(auditLog.id)
    setIsDetailLoading(true)
    setSelectedAuditLog(auditLog)
    setIsSheetOpen(true)
    queueMicrotask(() => setIsDetailLoading(false))
  }

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage(page)
    })
  }, [page, pageSize])

  const normalizedQuery = query.trim().toLowerCase()
  const filteredAuditLogs = auditLogs.filter((auditLog) => {
    if (!normalizedQuery) {
      return true
    }

    return [
      auditLog.summary,
      auditLog.action,
      auditLog.entityType,
      auditLog.entityDisplayName,
      auditLog.entityId,
      auditLog.projectName,
      auditLog.projectId,
      auditLog.actorDisplayName,
      auditLog.actorUserId,
      auditLog.outcome,
    ]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLowerCase().includes(normalizedQuery))
  })

  const jsonSections = selectedAuditLog
    ? [
        [t('history.metadata'), formatJson(selectedAuditLog.metadataJson)],
        [t('history.before'), formatJson(selectedAuditLog.beforeJson)],
        [t('history.after'), formatJson(selectedAuditLog.afterJson)],
        [t('audit.changes'), formatJson(selectedAuditLog.changesJson)],
      ].filter((section): section is [string, string] => Boolean(section[1]))
    : []

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('audit.pageTitle')}</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-2 overflow-auto p-3 md:p-4">
        <div className="relative w-full sm:w-80">
          <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-10"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('common.search')}
          />
        </div>

        <div className="rounded-md border bg-background p-3">
          {isListLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : filteredAuditLogs.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FileClock />
                </EmptyMedia>
                <EmptyTitle>{t('audit.noLogs')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('audit.time')}</TableHead>
                    <TableHead>{t('audit.action')}</TableHead>
                    <TableHead>{t('audit.entity')}</TableHead>
                    <TableHead>{t('common.project')}</TableHead>
                    <TableHead>{t('audit.actor')}</TableHead>
                    <TableHead>{t('audit.outcome')}</TableHead>
                    <TableHead>{t('audit.summary')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredAuditLogs.map((auditLog) => {
                    const isSelected = auditLog.id === selectedAuditLogId
                    return (
                      <TableRow
                        key={auditLog.id}
                        data-state={isSelected ? 'selected' : undefined}
                        className="cursor-pointer"
                        onClick={() => selectAuditLog(auditLog)}
                      >
                        <TableCell className="whitespace-nowrap text-muted-foreground">{formatDate(auditLog.occurredAt)}</TableCell>
                        <TableCell>
                          <Badge variant={actionVariant(auditLog.action)}>{actionLabel(auditLog.action, t)}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="font-medium">{entityTypeLabel(auditLog.entityType, t)}</div>
                          <div className="max-w-[200px] truncate text-xs text-muted-foreground">{entityDisplayName(auditLog, t)}</div>
                        </TableCell>
                        <TableCell className="max-w-[180px] truncate text-xs text-muted-foreground">{projectDisplayName(auditLog, t)}</TableCell>
                        <TableCell className="max-w-[180px] truncate text-xs text-muted-foreground">{actorDisplayName(auditLog, t)}</TableCell>
                        <TableCell>
                          <Badge variant={auditLog.outcome === 'SUCCESS' ? 'outline' : 'destructive'}>{outcomeLabel(auditLog.outcome, t)}</Badge>
                        </TableCell>
                        <TableCell className="max-w-[360px] truncate">{auditLog.summary}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              <div className="flex justify-end border-t pt-2 text-sm">
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={page === 0 || isListLoading}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">{t('common.pageOf', { page: page + 1, total: Math.max(totalPages, 1) })}</span>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={isListLoading || totalPages === 0 || page >= totalPages - 1}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                  <div className="ml-3 border-l pl-3">
                    <NativeSelect
                      className="h-8 w-20"
                      value={String(pageSize)}
                      onChange={(event) => {
                        setPageSize(Number(event.target.value))
                        setPage(0)
                      }}
                      disabled={isListLoading}
                      aria-label={t('common.pageSize')}
                    >
                      <NativeSelectOption value="25">25</NativeSelectOption>
                      <NativeSelectOption value="50">50</NativeSelectOption>
                      <NativeSelectOption value="100">100</NativeSelectOption>
                    </NativeSelect>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      <Sheet open={isSheetOpen} onOpenChange={setIsSheetOpen}>
        <SheetContent side="right" className="!w-full overflow-y-auto p-0 sm:!max-w-2xl" showCloseButton={false}>
          <SheetHeader className="flex min-h-12 flex-row items-center justify-between gap-3 border-b px-4 py-2">
            <SheetTitle className="text-xl">{t('audit.details')}</SheetTitle>
            <SheetClose render={<Button variant="ghost" size="icon-sm" className="-mr-2" />} aria-label={t('common.close')}>
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-4 py-2">
            {isDetailLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : null}

            {selectedAuditLog ? (
              <div className="space-y-4">
                <dl className="space-y-3">
                  {[
                    [t('audit.time'), formatDate(selectedAuditLog.occurredAt)],
                    [t('audit.action'), actionLabel(selectedAuditLog.action, t)],
                    [t('audit.entity'), `${entityTypeLabel(selectedAuditLog.entityType, t)} · ${entityDisplayName(selectedAuditLog, t)}`],
                    [t('common.project'), projectDisplayName(selectedAuditLog, t)],
                    [t('audit.actor'), actorDisplayName(selectedAuditLog, t)],
                    [t('audit.outcome'), outcomeLabel(selectedAuditLog.outcome, t)],
                    [t('audit.summary'), selectedAuditLog.summary],
                    [t('audit.auditId'), selectedAuditLog.id],
                  ].map(([label, value]) => (
                    <div key={label} className="border-b pb-3 last:border-b-0">
                      <dt className="text-sm font-medium">{label}</dt>
                      <dd className="mt-1 break-words text-sm text-muted-foreground">{value}</dd>
                    </div>
                  ))}
                </dl>

                {jsonSections.map(([label, value]) => (
                  <section key={label} className="space-y-2 border-t pt-3">
                    <h3 className="text-sm font-medium">{label}</h3>
                    <pre className="max-h-80 overflow-auto rounded-md border bg-muted/30 p-3 text-xs leading-relaxed">
                      {value}
                    </pre>
                  </section>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t('audit.inspect')}</p>
            )}
          </div>
        </SheetContent>
      </Sheet>

    </div>
  )
}
