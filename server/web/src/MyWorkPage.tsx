import { useCallback, useEffect, useMemo, useState } from 'react'
import { NavLink } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ChevronLeft, ChevronRight, Columns3, Layers3, List, ListTodo, Loader2, Search } from 'lucide-react'
import { toast } from 'sonner'

import { listAssignedToMe, type AssignedWorkItem } from '@/lib/api'
import { cn } from '@/lib/utils'
import { workItemTypeBadgeClass } from '@/lib/typeBadges'

type AssignedView = 'list' | 'grouped' | 'board'

const STATUS_ORDER = ['OPEN', 'IN_PROGRESS', 'BLOCKED', 'WAITING', 'ANSWERED', 'PENDING', 'APPROVED', 'REJECTED', 'DONE', 'CANCELLED']
const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  WAITING: 'Waiting',
  ANSWERED: 'Answered',
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  DONE: 'Done',
  CANCELLED: 'Cancelled',
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatDueDate(value: string | null) {
  if (!value) return 'No due date'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
}

function normalizedStatus(value: string) {
  return value.trim().toUpperCase().replaceAll(' ', '_')
}

function displayStatus(value: string) {
  const normalized = normalizedStatus(value)
  return STATUS_LABELS[normalized] ?? normalized.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())
}

function isDone(item: AssignedWorkItem) {
  return normalizedStatus(item.status) === 'DONE'
}

function isOverdue(item: AssignedWorkItem) {
  if (!item.dueDate || isDone(item)) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(`${item.dueDate}T00:00:00`) < today
}

function statusBadgeClass(status: string) {
  const normalized = normalizedStatus(status)
  if (normalized === 'BLOCKED') return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300'
  if (normalized === 'DONE') return 'border-green-200 bg-green-50 text-green-700 dark:border-green-900 dark:bg-green-950/40 dark:text-green-300'
  if (normalized === 'IN_PROGRESS') return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-300'
  return 'border-border bg-muted/50 text-muted-foreground'
}

function priorityBadgeClass(priority: string | null) {
  if (priority?.toUpperCase() === 'HIGH') return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-300'
  return 'border-border bg-muted/50 text-muted-foreground'
}

function WorkItemLink({ item }: { item: AssignedWorkItem }) {
  return (
    <div className="min-w-0">
      <div className="flex min-w-0 items-center gap-2">
        <Badge variant="outline" className={cn('shrink-0 font-medium uppercase', workItemTypeBadgeClass(item.type))}>{item.type}</Badge>
        <NavLink to={`/app/projects/${item.projectId}?workItemId=${item.workItemId}`} className="line-clamp-2 font-medium hover:underline" title={item.title}>{item.title}</NavLink>
      </div>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  return <Badge variant="outline" className={cn('whitespace-nowrap', statusBadgeClass(status))}>{displayStatus(status)}</Badge>
}

function PriorityBadge({ priority }: { priority: string | null }) {
  if (!priority) return <span className="text-sm text-muted-foreground">No priority</span>
  return <Badge variant="outline" className={cn('whitespace-nowrap', priorityBadgeClass(priority))}>{priority}</Badge>
}

function SummaryStat({ label, value, tone }: { label: string; value: number; tone?: 'blue' | 'red' | 'orange' | 'amber' }) {
  const toneClass = tone === 'red'
    ? 'text-red-600 dark:text-red-400'
    : tone === 'orange'
      ? 'text-orange-600 dark:text-orange-400'
      : tone === 'amber'
        ? 'text-amber-600 dark:text-amber-400'
        : tone === 'blue'
          ? 'text-blue-600 dark:text-blue-400'
          : 'text-muted-foreground'

  return <span className="rounded-md border bg-muted/30 px-2.5 py-1.5 text-sm font-medium text-foreground"><span className={toneClass}>{label}</span> <span className="font-semibold">{value}</span></span>
}

export default function MyWorkPage() {
  const [items, setItems] = useState<AssignedWorkItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [isLoading, setIsLoading] = useState(true)
  const [view, setView] = useState<AssignedView>('list')
  const [search, setSearch] = useState('')
  const [projectFilter, setProjectFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [priorityFilter, setPriorityFilter] = useState('ALL')

  const loadPage = useCallback(async (nextPage: number) => {
    setIsLoading(true)
    try {
      const data = await listAssignedToMe(nextPage, pageSize)
      setItems(data.items)
      setTotalPages(data.totalPages)
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load assigned work items.')
      setItems([])
      setTotalPages(0)
    } finally {
      setIsLoading(false)
    }
  }, [pageSize])

  useEffect(() => {
    queueMicrotask(() => { void loadPage(page) })
  }, [loadPage, page])

  const projectOptions = useMemo(() => [...new Set(items.map((item) => item.projectName))].sort((left, right) => left.localeCompare(right)), [items])
  const statusOptions = useMemo(() => [...new Set(items.map((item) => item.status))].sort((left, right) => left.localeCompare(right)), [items])
  const priorityOptions = useMemo(() => [...new Set(items.map((item) => item.priority).filter((priority): priority is string => Boolean(priority)))].sort((left, right) => left.localeCompare(right)), [items])
  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase()
    return items.filter((item) => {
      const matchesSearch = !query || `${item.title} ${item.projectName} ${item.type}`.toLowerCase().includes(query)
      const matchesProject = projectFilter === 'ALL' || item.projectName === projectFilter
      const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
      const matchesPriority = priorityFilter === 'ALL' || item.priority === priorityFilter
      return matchesSearch && matchesProject && matchesStatus && matchesPriority
    })
  }, [items, priorityFilter, projectFilter, search, statusFilter])
  const groupedItems = useMemo(() => {
    const groups = new Map<string, AssignedWorkItem[]>()
    filteredItems.forEach((item) => groups.set(item.projectName, [...(groups.get(item.projectName) ?? []), item]))
    return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right))
  }, [filteredItems])
  const boardStatuses = useMemo(() => {
    const statuses = [...new Set(filteredItems.map((item) => normalizedStatus(item.status)))]
    return statuses.sort((left, right) => {
      const leftIndex = STATUS_ORDER.indexOf(left)
      const rightIndex = STATUS_ORDER.indexOf(right)
      return (leftIndex < 0 ? STATUS_ORDER.length : leftIndex) - (rightIndex < 0 ? STATUS_ORDER.length : rightIndex)
    })
  }, [filteredItems])
  const overdueCount = items.filter(isOverdue).length
  const blockedCount = items.filter((item) => normalizedStatus(item.status) === 'BLOCKED').length
  const highPriorityCount = items.filter((item) => item.priority?.toUpperCase() === 'HIGH').length

  const clearFilters = () => {
    setSearch('')
    setProjectFilter('ALL')
    setStatusFilter('ALL')
    setPriorityFilter('ALL')
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center border-b px-4 py-3 md:px-6"><h1 className="text-xl font-semibold leading-none tracking-normal">My Work</h1></div>
      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <div className="flex flex-wrap gap-2">
              <SummaryStat label="Showing" value={filteredItems.length} tone="blue" />
              <SummaryStat label="Overdue" value={overdueCount} tone="red" />
              <SummaryStat label="Blocked" value={blockedCount} tone="orange" />
              <SummaryStat label="High priority" value={highPriorityCount} tone="amber" />
            </div>
          </div>
          <div className="flex rounded-md border bg-muted/50 p-1" role="tablist" aria-label="My Work view">
            {([['list', List, 'List'], ['grouped', Layers3, 'Grouped'], ['board', Columns3, 'Board']] as const).map(([viewId, Icon, label]) => (
              <Button key={viewId} type="button" variant={view === viewId ? 'secondary' : 'ghost'} size="sm" role="tab" aria-selected={view === viewId} onClick={() => setView(viewId)} className="gap-1.5"><Icon className="h-4 w-4" />{label}</Button>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 bg-background py-1">
          <div className="relative w-full flex-none sm:w-64"><Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={search} onChange={(event) => setSearch(event.target.value)} aria-label="Search work items" placeholder="Search" className="pl-8" /></div>
          <NativeSelect value={projectFilter} onChange={(event) => setProjectFilter(event.target.value)} aria-label="Filter by project" className="w-40"><NativeSelectOption value="ALL">All projects</NativeSelectOption>{projectOptions.map((project) => <NativeSelectOption key={project} value={project}>{project}</NativeSelectOption>)}</NativeSelect>
          <NativeSelect value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} aria-label="Filter by status" className="w-40"><NativeSelectOption value="ALL">All statuses</NativeSelectOption>{statusOptions.map((status) => <NativeSelectOption key={status} value={status}>{displayStatus(status)}</NativeSelectOption>)}</NativeSelect>
          <NativeSelect value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)} aria-label="Filter by priority" className="w-36"><NativeSelectOption value="ALL">All priorities</NativeSelectOption>{priorityOptions.map((priority) => <NativeSelectOption key={priority} value={priority}>{priority}</NativeSelectOption>)}</NativeSelect>
          {(search || projectFilter !== 'ALL' || statusFilter !== 'ALL' || priorityFilter !== 'ALL') && <Button type="button" variant="ghost" size="sm" onClick={clearFilters}>Clear</Button>}
        </div>

        <div className="rounded-md border bg-background p-4">
          {isLoading ? <div className="flex min-h-64 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div> : items.length === 0 ? (
            <Empty className="min-h-64 border-0"><EmptyHeader><EmptyMedia variant="icon"><ListTodo /></EmptyMedia><EmptyTitle>Nothing assigned to you</EmptyTitle></EmptyHeader></Empty>
          ) : filteredItems.length === 0 ? (
            <Empty className="min-h-48 border-0"><EmptyHeader><EmptyTitle>No work items match these filters</EmptyTitle></EmptyHeader><Button type="button" variant="outline" onClick={clearFilters}>Clear filters</Button></Empty>
          ) : view === 'list' ? (
            <><div className="overflow-x-auto"><Table className="min-w-[760px]"><TableHeader><TableRow><TableHead>Work item</TableHead><TableHead>Project</TableHead><TableHead>Status</TableHead><TableHead>Priority</TableHead><TableHead>Due date</TableHead><TableHead>Updated</TableHead></TableRow></TableHeader><TableBody>{filteredItems.map((item) => <TableRow key={item.workItemId}><TableCell><WorkItemLink item={item} /></TableCell><TableCell><NavLink to={`/app/projects/${item.projectId}`} className="font-medium hover:underline">{item.projectName}</NavLink></TableCell><TableCell><StatusBadge status={item.status} /></TableCell><TableCell><PriorityBadge priority={item.priority} /></TableCell><TableCell className={cn('whitespace-nowrap', isOverdue(item) ? 'font-medium text-red-600 dark:text-red-400' : 'text-muted-foreground')}>{isOverdue(item) ? 'Overdue' : formatDueDate(item.dueDate)}</TableCell><TableCell className="whitespace-nowrap text-muted-foreground">{formatDate(item.updatedAt)}</TableCell></TableRow>)}</TableBody></Table></div><Pagination page={page} totalPages={totalPages} pageSize={pageSize} isLoading={isLoading} setPage={setPage} setPageSize={setPageSize} /></>
          ) : view === 'grouped' ? (
            <div className="divide-y">{groupedItems.map(([project, projectItems]) => <section key={project} className="py-2 first:pt-0 last:pb-0"><div className="flex items-center gap-2 px-1 py-2"><h2 className="text-sm font-semibold">{project}</h2><span className="text-xs text-muted-foreground">{projectItems.length} {projectItems.length === 1 ? 'item' : 'items'}</span></div><div className="space-y-1">{projectItems.map((item) => <div key={item.workItemId} className="flex flex-wrap items-center gap-3 rounded-md px-2 py-2 hover:bg-muted/50"><div className="min-w-[18rem] flex-1"><WorkItemLink item={item} /></div><StatusBadge status={item.status} /><PriorityBadge priority={item.priority} /><span className={cn('min-w-28 text-right text-sm', isOverdue(item) ? 'font-medium text-red-600 dark:text-red-400' : 'text-muted-foreground')}>{isOverdue(item) ? 'Overdue' : formatDueDate(item.dueDate)}</span></div>)}</div></section>)}</div>
          ) : (
            <div className="flex gap-3 overflow-x-auto pb-1">{boardStatuses.map((boardStatus) => { const laneItems = filteredItems.filter((item) => normalizedStatus(item.status) === boardStatus); return <section key={boardStatus} className="min-h-56 min-w-64 flex-1 rounded-md border bg-muted/30 p-2"><div className="flex items-center justify-between px-1 py-2"><h2 className="text-xs font-semibold uppercase tracking-wide">{displayStatus(boardStatus)}</h2><span className="text-xs text-muted-foreground">{laneItems.length}</span></div><div className="space-y-2">{laneItems.map((item) => <article key={item.workItemId} className="rounded-md border bg-background p-3 shadow-xs"><div className="flex justify-end"><PriorityBadge priority={item.priority} /></div><div className="mt-2 text-sm"><WorkItemLink item={item} /></div><div className="mt-2"><NavLink to={`/app/projects/${item.projectId}`} className="text-xs font-medium text-foreground hover:underline">{item.projectName}</NavLink></div><div className="mt-2 flex justify-end text-xs text-muted-foreground"><span className={cn(isOverdue(item) && 'font-medium text-red-600 dark:text-red-400')}>{isOverdue(item) ? 'Overdue' : formatDueDate(item.dueDate)}</span></div></article>)}{laneItems.length === 0 && <div className="px-1 py-8 text-center text-xs text-muted-foreground">No items</div>}</div></section> })}</div>
          )}
        </div>
      </div>
    </div>
  )
}

function Pagination({ page, totalPages, pageSize, isLoading, setPage, setPageSize }: { page: number; totalPages: number; pageSize: number; isLoading: boolean; setPage: (update: (current: number) => number) => void; setPageSize: (size: number) => void }) {
  return <div className="flex justify-end border-t pt-3 text-sm"><div className="flex items-center gap-2"><Button variant="outline" size="icon-sm" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0 || isLoading} aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button><span className="text-sm text-muted-foreground">Page {page + 1} of {Math.max(totalPages, 1)}</span><Button variant="outline" size="icon-sm" onClick={() => setPage((current) => current + 1)} disabled={isLoading || totalPages === 0 || page >= totalPages - 1} aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button><div className="ml-4 border-l pl-4"><NativeSelect className="h-8 w-20" value={String(pageSize)} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(() => 0) }} disabled={isLoading} aria-label="Page size"><NativeSelectOption value="25">25</NativeSelectOption><NativeSelectOption value="50">50</NativeSelectOption></NativeSelect></div></div></div>
}
