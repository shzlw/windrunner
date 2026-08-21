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

import { listAuditLogs, type AuditLog } from '@/lib/api'

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatValue(value: string | null | undefined) {
  return value && value.trim() ? value : 'Not set'
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

export default function AuditLogsPage() {
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
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load audit logs.')
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
      auditLog.entityId,
      auditLog.projectId,
      auditLog.actorUserId,
      auditLog.outcome,
    ]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLowerCase().includes(normalizedQuery))
  })

  const jsonSections = selectedAuditLog
    ? [
        ['Metadata', formatJson(selectedAuditLog.metadataJson)],
        ['Before', formatJson(selectedAuditLog.beforeJson)],
        ['After', formatJson(selectedAuditLog.afterJson)],
        ['Changes', formatJson(selectedAuditLog.changesJson)],
      ].filter((section): section is [string, string] => Boolean(section[1]))
    : []

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Audit Logs</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="relative w-full sm:w-80">
          <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-10"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>

        <div className="rounded-md border bg-background p-4">
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
                <EmptyTitle>No audit logs found</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Time</TableHead>
                    <TableHead>Action</TableHead>
                    <TableHead>Entity</TableHead>
                    <TableHead>Project</TableHead>
                    <TableHead>Actor</TableHead>
                    <TableHead>Outcome</TableHead>
                    <TableHead>Summary</TableHead>
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
                          <Badge variant={actionVariant(auditLog.action)}>{auditLog.action}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="font-medium">{auditLog.entityType}</div>
                          <div className="max-w-[160px] truncate font-mono text-[11px] text-muted-foreground">{formatValue(auditLog.entityId)}</div>
                        </TableCell>
                        <TableCell className="max-w-[160px] truncate font-mono text-[11px] text-muted-foreground">{formatValue(auditLog.projectId)}</TableCell>
                        <TableCell className="max-w-[160px] truncate font-mono text-[11px] text-muted-foreground">{formatValue(auditLog.actorUserId)}</TableCell>
                        <TableCell>
                          <Badge variant={auditLog.outcome === 'SUCCESS' ? 'outline' : 'destructive'}>{auditLog.outcome}</Badge>
                        </TableCell>
                        <TableCell className="max-w-[360px] truncate">{auditLog.summary}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              <div className="flex justify-end border-t pt-3 text-sm">
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={page === 0 || isListLoading}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">Page {page + 1} of {Math.max(totalPages, 1)}</span>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={isListLoading || totalPages === 0 || page >= totalPages - 1}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                  <div className="ml-4 border-l pl-4">
                    <NativeSelect
                      className="h-8 w-20"
                      value={String(pageSize)}
                      onChange={(event) => {
                        setPageSize(Number(event.target.value))
                        setPage(0)
                      }}
                      disabled={isListLoading}
                      aria-label="Page size"
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
            <SheetTitle className="text-xl">Audit log details</SheetTitle>
            <SheetClose render={<Button variant="ghost" size="icon-sm" className="-mr-2" />} aria-label="Close">
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-6 py-2">
            {isDetailLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : null}

            {selectedAuditLog ? (
              <div className="space-y-6">
                <dl className="space-y-4">
                  {[
                    ['Time', formatDate(selectedAuditLog.occurredAt)],
                    ['Action', selectedAuditLog.action],
                    ['Entity type', selectedAuditLog.entityType],
                    ['Entity ID', formatValue(selectedAuditLog.entityId)],
                    ['Project ID', formatValue(selectedAuditLog.projectId)],
                    ['Actor user ID', formatValue(selectedAuditLog.actorUserId)],
                    ['Outcome', selectedAuditLog.outcome],
                    ['Summary', selectedAuditLog.summary],
                    ['Audit ID', selectedAuditLog.id],
                  ].map(([label, value]) => (
                    <div key={label} className="border-b pb-3 last:border-b-0">
                      <dt className="text-sm font-medium">{label}</dt>
                      <dd className="mt-1 break-words text-sm text-muted-foreground">{value}</dd>
                    </div>
                  ))}
                </dl>

                {jsonSections.map(([label, value]) => (
                  <section key={label} className="space-y-2 border-t pt-4">
                    <h3 className="text-sm font-medium">{label}</h3>
                    <pre className="max-h-80 overflow-auto rounded-md border bg-muted/30 p-3 text-xs leading-relaxed">
                      {value}
                    </pre>
                  </section>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Select an audit log to inspect it.</p>
            )}
          </div>
        </SheetContent>
      </Sheet>

    </div>
  )
}
