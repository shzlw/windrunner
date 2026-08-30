import { useCallback, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Bot, CheckCircle2, Cpu, Info, Loader2, RefreshCw, Timer, TrendingUp, XCircle, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  getLlmStatus,
  getLlmUsage,
  listGraphChangeProposals,
  listProjects,
  type GraphChangeProposal,
  type GraphChangeProposalChange,
  type LlmStatus,
  type LlmUsageSummary,
  type Project,
} from '@/lib/api'

type DateRangeKey = 'all' | '7d' | '30d' | '90d'

type ProjectProposalReport = {
  project: Project
  proposals: GraphChangeProposal[]
}

type ProjectImpact = {
  project: Project
  proposalsCreated: number
  changesProposed: number
  changesAccepted: number
  changesRejected: number
  changesNeedsUpdate: number
  changesPending: number
  nodeChangesAccepted: number
  edgeChangesAccepted: number
  acceptedNodeAdds: number
  acceptedNodeUpdates: number
  acceptedNodeDeletes: number
  acceptedEdgeAdds: number
  acceptedEdgeUpdates: number
  acceptedEdgeDeletes: number
  estimatedMinutesSaved: number
  decidedChanges: number
  acceptanceRate: number
}

const DATE_RANGE_OPTIONS: Array<{ value: DateRangeKey; label: string; days: number | null }> = [
  { value: 'all', label: 'All time', days: null },
  { value: '7d', label: 'Last 7 days', days: 7 },
  { value: '30d', label: 'Last 30 days', days: 30 },
  { value: '90d', label: 'Last 90 days', days: 90 },
]

const TIME_ASSUMPTIONS = {
  nodeAddMinutes: 2,
  nodeUpdateMinutes: 1,
  nodeDeleteMinutes: 0.5,
  edgeAddMinutes: 1,
  edgeUpdateMinutes: 1,
  edgeDeleteMinutes: 0.25,
}

const TIME_ASSUMPTION_ROWS: Array<[string, number]> = [
  ['Node add', TIME_ASSUMPTIONS.nodeAddMinutes],
  ['Node update', TIME_ASSUMPTIONS.nodeUpdateMinutes],
  ['Node delete', TIME_ASSUMPTIONS.nodeDeleteMinutes],
  ['Edge add', TIME_ASSUMPTIONS.edgeAddMinutes],
  ['Edge update', TIME_ASSUMPTIONS.edgeUpdateMinutes],
  ['Edge delete', TIME_ASSUMPTIONS.edgeDeleteMinutes],
]

function formatProjectTitle(project: Project) {
  return project.title?.trim() || project.name?.trim() || 'Untitled project'
}

function formatNumber(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value)
}

function formatPercent(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0, style: 'percent' }).format(value)
}

function formatHours(minutes: number) {
  if (minutes < 60) {
    return `${formatNumber(minutes)}m`
  }
  return `${formatNumber(minutes / 60)}h`
}

function formatCompact(value: number) {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

function formatDuration(milliseconds: number) {
  if (milliseconds < 1000) {
    return `${formatNumber(milliseconds)}ms`
  }
  if (milliseconds < 60_000) {
    return `${formatNumber(milliseconds / 1000)}s`
  }
  return `${formatNumber(milliseconds / 60_000)}m`
}

function isStatus(change: GraphChangeProposalChange, status: string) {
  return change.status?.toUpperCase() === status
}

function isEntity(change: GraphChangeProposalChange, entityType: 'NODE' | 'EDGE') {
  return change.entityType?.toUpperCase() === entityType
}

function isAction(change: GraphChangeProposalChange, action: 'ADD' | 'UPDATE' | 'DELETE') {
  return change.action?.toUpperCase() === action
}

function parseDate(value: string | null | undefined) {
  if (!value) {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function proposalCreatedInRange(proposal: GraphChangeProposal, rangeStart: Date | null) {
  if (!rangeStart) {
    return true
  }
  const createdAt = parseDate(proposal.createdAt)
  return createdAt ? createdAt >= rangeStart : false
}

function changeActivityDate(change: GraphChangeProposalChange) {
  if (isStatus(change, 'APPLIED')) {
    return parseDate(change.appliedAt) ?? parseDate(change.updatedAt) ?? parseDate(change.createdAt)
  }
  return parseDate(change.updatedAt) ?? parseDate(change.createdAt) ?? parseDate(change.appliedAt)
}

function changeInRange(change: GraphChangeProposalChange, rangeStart: Date | null) {
  if (!rangeStart) {
    return true
  }
  const activityDate = changeActivityDate(change)
  return activityDate ? activityDate >= rangeStart : false
}

function estimateMinutesSaved(change: GraphChangeProposalChange) {
  if (!isStatus(change, 'APPLIED')) {
    return 0
  }
  if (isEntity(change, 'NODE')) {
    if (isAction(change, 'ADD')) {
      return TIME_ASSUMPTIONS.nodeAddMinutes
    }
    if (isAction(change, 'UPDATE')) {
      return TIME_ASSUMPTIONS.nodeUpdateMinutes
    }
    if (isAction(change, 'DELETE')) {
      return TIME_ASSUMPTIONS.nodeDeleteMinutes
    }
  }
  if (isEntity(change, 'EDGE')) {
    if (isAction(change, 'ADD')) {
      return TIME_ASSUMPTIONS.edgeAddMinutes
    }
    if (isAction(change, 'UPDATE')) {
      return TIME_ASSUMPTIONS.edgeUpdateMinutes
    }
    if (isAction(change, 'DELETE')) {
      return TIME_ASSUMPTIONS.edgeDeleteMinutes
    }
  }
  return 0
}

function summarizeProject(project: Project, proposals: GraphChangeProposal[], rangeStart: Date | null): ProjectImpact {
  const changes = proposals
    .flatMap((proposal) => proposal.changes ?? [])
    .filter((change) => changeInRange(change, rangeStart))
  const changesAccepted = changes.filter((change) => isStatus(change, 'APPLIED')).length
  const changesRejected = changes.filter((change) => isStatus(change, 'REJECTED')).length
  const changesNeedsUpdate = changes.filter((change) => isStatus(change, 'NEEDS_UPDATE')).length
  const changesPending = changes.filter((change) => isStatus(change, 'PENDING')).length
  const decidedChanges = changesAccepted + changesRejected + changesNeedsUpdate

  return {
    project,
    proposalsCreated: proposals.filter((proposal) => proposalCreatedInRange(proposal, rangeStart)).length,
    changesProposed: changes.length,
    changesAccepted,
    changesRejected,
    changesNeedsUpdate,
    changesPending,
    nodeChangesAccepted: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'NODE')).length,
    edgeChangesAccepted: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'EDGE')).length,
    acceptedNodeAdds: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'NODE') && isAction(change, 'ADD')).length,
    acceptedNodeUpdates: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'NODE') && isAction(change, 'UPDATE')).length,
    acceptedNodeDeletes: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'NODE') && isAction(change, 'DELETE')).length,
    acceptedEdgeAdds: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'EDGE') && isAction(change, 'ADD')).length,
    acceptedEdgeUpdates: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'EDGE') && isAction(change, 'UPDATE')).length,
    acceptedEdgeDeletes: changes.filter((change) => isStatus(change, 'APPLIED') && isEntity(change, 'EDGE') && isAction(change, 'DELETE')).length,
    estimatedMinutesSaved: changes.reduce((total, change) => total + estimateMinutesSaved(change), 0),
    decidedChanges,
    acceptanceRate: decidedChanges === 0 ? 0 : changesAccepted / decidedChanges,
  }
}

function sumImpacts(impacts: ProjectImpact[]): ProjectImpact {
  const emptyProject: Project = {
    id: 'all',
    title: 'All projects',
    name: 'All projects',
    description: null,
    userId: null,
  }

  return impacts.reduce<ProjectImpact>((total, impact) => ({
    project: emptyProject,
    proposalsCreated: total.proposalsCreated + impact.proposalsCreated,
    changesProposed: total.changesProposed + impact.changesProposed,
    changesAccepted: total.changesAccepted + impact.changesAccepted,
    changesRejected: total.changesRejected + impact.changesRejected,
    changesNeedsUpdate: total.changesNeedsUpdate + impact.changesNeedsUpdate,
    changesPending: total.changesPending + impact.changesPending,
    nodeChangesAccepted: total.nodeChangesAccepted + impact.nodeChangesAccepted,
    edgeChangesAccepted: total.edgeChangesAccepted + impact.edgeChangesAccepted,
    acceptedNodeAdds: total.acceptedNodeAdds + impact.acceptedNodeAdds,
    acceptedNodeUpdates: total.acceptedNodeUpdates + impact.acceptedNodeUpdates,
    acceptedNodeDeletes: total.acceptedNodeDeletes + impact.acceptedNodeDeletes,
    acceptedEdgeAdds: total.acceptedEdgeAdds + impact.acceptedEdgeAdds,
    acceptedEdgeUpdates: total.acceptedEdgeUpdates + impact.acceptedEdgeUpdates,
    acceptedEdgeDeletes: total.acceptedEdgeDeletes + impact.acceptedEdgeDeletes,
    estimatedMinutesSaved: total.estimatedMinutesSaved + impact.estimatedMinutesSaved,
    decidedChanges: total.decidedChanges + impact.decidedChanges,
    acceptanceRate: 0,
  }), {
    project: emptyProject,
    proposalsCreated: 0,
    changesProposed: 0,
    changesAccepted: 0,
    changesRejected: 0,
    changesNeedsUpdate: 0,
    changesPending: 0,
    nodeChangesAccepted: 0,
    edgeChangesAccepted: 0,
    acceptedNodeAdds: 0,
    acceptedNodeUpdates: 0,
    acceptedNodeDeletes: 0,
    acceptedEdgeAdds: 0,
    acceptedEdgeUpdates: 0,
    acceptedEdgeDeletes: 0,
    estimatedMinutesSaved: 0,
    decidedChanges: 0,
    acceptanceRate: 0,
  })
}

function formatFeatureName(feature: string) {
  return feature
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function MetricCard({ label, value, description, icon: Icon }: {
  label: string
  value: string
  description: string
  icon: LucideIcon
}) {
  return (
    <div className="rounded-md border px-3 py-2">
      <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
        {label}
        <Icon className="h-4 w-4" />
      </div>
      <div className="mt-2 text-2xl font-semibold">{value}</div>
      <div className="mt-1 text-sm text-muted-foreground">{description}</div>
    </div>
  )
}

function FeatureUsageTable({ usage }: { usage: LlmUsageSummary | null }) {
  if (!usage || usage.byFeature.length === 0) {
    return (
      <Empty className="min-h-32 border-0">
        <EmptyHeader>
          <EmptyMedia variant="icon"><Bot /></EmptyMedia>
          <EmptyTitle>No feature usage yet</EmptyTitle>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Feature</TableHead>
          <TableHead className="text-right">Tokens</TableHead>
          <TableHead className="text-right">Requests</TableHead>
          <TableHead className="text-right">Failures</TableHead>
          <TableHead className="text-right">Success rate</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {usage.byFeature.map((row) => (
          <TableRow key={row.feature}>
            <TableCell className="font-medium">{formatFeatureName(row.feature)}</TableCell>
            <TableCell className="text-right">{formatCompact(row.inputTokens + row.outputTokens)}</TableCell>
            <TableCell className="text-right">{formatNumber(row.requests)}</TableCell>
            <TableCell className="text-right">{formatNumber(row.failures)}</TableCell>
            <TableCell className="text-right">{formatPercent(row.successRate)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export default function AIAnalyticsPage() {
  const [projectReports, setProjectReports] = useState<ProjectProposalReport[]>([])
  const [llmStatus, setLlmStatus] = useState<LlmStatus | null>(null)
  const [usage, setUsage] = useState<LlmUsageSummary | null>(null)
  const [isUsageLoading, setIsUsageLoading] = useState(true)
  const [selectedProjectId, setSelectedProjectId] = useState('all')
  const [dateRange, setDateRange] = useState<DateRangeKey>('all')
  const [isLoading, setIsLoading] = useState(true)

  const loadUsage = useCallback(async () => {
    setIsUsageLoading(true)

    try {
      const days = DATE_RANGE_OPTIONS.find((option) => option.value === dateRange)?.days ?? undefined
      setUsage(await getLlmUsage(selectedProjectId === 'all' ? undefined : selectedProjectId, days))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load token usage.')
    } finally {
      setIsUsageLoading(false)
    }
  }, [selectedProjectId, dateRange])

  useEffect(() => {
    const loadTimer = window.setTimeout(() => {
      void loadUsage()
    }, 0)

    return () => window.clearTimeout(loadTimer)
  }, [loadUsage])

  const loadImpact = useCallback(async () => {
    setIsLoading(true)

    try {
      const [projects, nextLlmStatus] = await Promise.all([
        listProjects(),
        getLlmStatus().catch(() => ({ provider: 'none', available: false })),
      ])
      const projectProposals = await Promise.all(projects.map(async (project) => ({
        project,
        proposals: await listGraphChangeProposals(project.id),
      })))
      setLlmStatus(nextLlmStatus)
      setProjectReports(projectProposals)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load AI analytics report.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const loadTimer = window.setTimeout(() => {
      void loadImpact()
    }, 0)

    return () => window.clearTimeout(loadTimer)
  }, [loadImpact])

  const rangeStart = useMemo(() => {
    const option = DATE_RANGE_OPTIONS.find((current) => current.value === dateRange)
    if (!option?.days) {
      return null
    }
    const start = new Date()
    start.setDate(start.getDate() - option.days)
    return start
  }, [dateRange])

  const projectOptions = useMemo(() => (
    [...projectReports]
      .map((report) => report.project)
      .sort((left, right) => formatProjectTitle(left).localeCompare(formatProjectTitle(right)))
  ), [projectReports])

  const impacts = useMemo(() => (
    projectReports.map(({ project, proposals }) => summarizeProject(project, proposals, rangeStart))
  ), [projectReports, rangeStart])

  const filteredImpacts = useMemo(() => (
    impacts.filter((impact) => selectedProjectId === 'all' || impact.project.id === selectedProjectId)
  ), [impacts, selectedProjectId])

  const totals = useMemo(() => {
    const summary = sumImpacts(filteredImpacts)
    return {
      ...summary,
      acceptanceRate: summary.decidedChanges === 0 ? 0 : summary.changesAccepted / summary.decidedChanges,
    }
  }, [filteredImpacts])

  const sortedImpacts = useMemo(() => (
    [...filteredImpacts].sort((left, right) => (
      right.estimatedMinutesSaved - left.estimatedMinutesSaved
      || right.changesAccepted - left.changesAccepted
      || formatProjectTitle(left.project).localeCompare(formatProjectTitle(right.project))
    ))
  ), [filteredImpacts])

  const projectTitleById = useMemo(() => {
    const titles = new Map<string, string>()
    projectReports.forEach(({ project }) => titles.set(project.id, formatProjectTitle(project)))
    return titles
  }, [projectReports])

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-14 shrink-0 items-center gap-2 border-b px-4 py-3 md:px-6">
        <h1 className="text-xl font-semibold leading-none tracking-normal">AI Analytics</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-3 overflow-auto p-4 md:p-6">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <NativeSelect className="w-full sm:w-56" value={selectedProjectId} onChange={(event) => setSelectedProjectId(event.target.value)}>
            <NativeSelectOption value="all">All projects</NativeSelectOption>
            {projectOptions.map((project) => (
              <NativeSelectOption key={project.id} value={project.id}>
                {formatProjectTitle(project)}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          <NativeSelect className="w-full sm:w-40" value={dateRange} onChange={(event) => setDateRange(event.target.value as DateRangeKey)}>
            {DATE_RANGE_OPTIONS.map((option) => (
              <NativeSelectOption key={option.value} value={option.value}>
                {option.label}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          <Button type="button" variant="outline" className="gap-2" onClick={() => { void loadImpact(); void loadUsage() }} disabled={isLoading || isUsageLoading}>
            {isLoading || isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            Refresh
          </Button>
        </div>

        {llmStatus && !llmStatus.available ? (
          <div className="flex items-center gap-2 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>AI provider unavailable. Historical analytics remain available.</span>
          </div>
        ) : null}

        <Tabs defaultValue="overview" className="gap-4">
          <TabsList variant="line" className="w-full justify-start overflow-x-auto border-b">
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="usage">Usage &amp; reliability</TabsTrigger>
            <TabsTrigger value="outcomes">ROI &amp; outcomes</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="mt-0 space-y-3">
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <MetricCard
                label="AI requests"
                value={usage ? formatNumber(usage.totals.requests) : '—'}
                description={usage ? `${formatNumber(usage.totals.failures)} failed` : 'Loading usage'}
                icon={Bot}
              />
              <MetricCard
                label="Total tokens"
                value={usage ? formatCompact(usage.totals.inputTokens + usage.totals.outputTokens) : '—'}
                description="input + output"
                icon={Cpu}
              />
              <MetricCard
                label="Accepted changes"
                value={isLoading ? '—' : formatNumber(totals.changesAccepted)}
                description={isLoading ? 'Loading outcomes' : `${totals.nodeChangesAccepted} nodes, ${totals.edgeChangesAccepted} edges`}
                icon={CheckCircle2}
              />
              <MetricCard
                label="Acceptance rate"
                value={isLoading ? '—' : formatPercent(totals.acceptanceRate)}
                description={isLoading ? 'Loading outcomes' : `${totals.decidedChanges} decided changes`}
                icon={TrendingUp}
              />
            </div>

            <div className="rounded-md border bg-background p-4">
              <div className="mb-4 flex items-center gap-2">
                <h2 className="text-sm font-semibold tracking-normal">AI usage by feature</h2>
                {isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
              </div>
              {isUsageLoading ? (
                <div className="flex min-h-32 items-center justify-center">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : (
                <FeatureUsageTable usage={usage} />
              )}
            </div>
          </TabsContent>

          <TabsContent value="outcomes" className="mt-0 space-y-3">
        <div className="rounded-md border bg-background p-4">
          <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                Estimated time saved
                <Popover>
                  <PopoverTrigger
                    render={(
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-xs"
                        className="-mr-1 text-muted-foreground"
                        aria-label="How time saved is estimated"
                      >
                        <Info className="h-4 w-4" />
                      </Button>
                    )}
                  />
                  <PopoverContent align="end" side="top" className="w-72">
                    <PopoverHeader>
                      <PopoverTitle>Time saved assumptions</PopoverTitle>
                      <PopoverDescription>Estimated minutes saved per accepted change.</PopoverDescription>
                    </PopoverHeader>
                    <div className="grid gap-1.5 text-sm">
                      {TIME_ASSUMPTION_ROWS.map(([label, minutes]) => (
                        <div key={label} className="flex items-center justify-between gap-3">
                          <span className="text-muted-foreground">{label}</span>
                          <span className="font-medium">{formatNumber(minutes)} min</span>
                        </div>
                      ))}
                    </div>
                  </PopoverContent>
                </Popover>
              </div>
              <div className="mt-2 text-2xl font-semibold">{formatHours(totals.estimatedMinutesSaved)}</div>
              <div className="mt-1 text-sm text-muted-foreground">across {formatNumber(totals.changesAccepted)} accepted changes</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                Accepted changes
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{totals.changesAccepted}</div>
              <div className="mt-1 text-sm text-muted-foreground">{totals.nodeChangesAccepted} nodes, {totals.edgeChangesAccepted} edges</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                Acceptance rate
                <TrendingUp className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{formatPercent(totals.acceptanceRate)}</div>
              <div className="mt-1 text-sm text-muted-foreground">{totals.decidedChanges} decided changes</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                Rework requested
                <XCircle className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{totals.changesRejected + totals.changesNeedsUpdate}</div>
              <div className="mt-1 text-sm text-muted-foreground">{totals.changesRejected} rejected, {totals.changesNeedsUpdate} needs update</div>
            </div>
          </div>

          {isLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : sortedImpacts.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Bot />
                </EmptyMedia>
                <EmptyTitle>No AI impact yet</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Project</TableHead>
                  <TableHead className="text-right">Saved</TableHead>
                  <TableHead className="text-right">Accepted</TableHead>
                  <TableHead className="text-right">Rework</TableHead>
                  <TableHead className="text-right">Pending</TableHead>
                  <TableHead className="text-right">Rate</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sortedImpacts.map((impact) => (
                  <TableRow key={impact.project.id}>
                    <TableCell>
                      <div className="min-w-0">
                        <NavLink to={`/app/projects/${impact.project.id}`} className="font-medium hover:underline">
                          {formatProjectTitle(impact.project)}
                        </NavLink>
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-medium">{formatHours(impact.estimatedMinutesSaved)}</TableCell>
                    <TableCell className="text-right">{impact.changesAccepted}</TableCell>
                    <TableCell className="text-right">{impact.changesRejected + impact.changesNeedsUpdate}</TableCell>
                    <TableCell className="text-right">{impact.changesPending}</TableCell>
                    <TableCell className="text-right">{formatPercent(impact.acceptanceRate)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
        </TabsContent>

        <TabsContent value="usage" className="mt-0 space-y-3">
        <div className="rounded-md border bg-background p-4">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="text-sm font-semibold tracking-normal">Token usage</h2>
            {isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
          </div>

          {usage ? (
            <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  Total tokens
                  <Cpu className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">
                  {formatCompact(usage.totals.inputTokens + usage.totals.outputTokens)}
                </div>
                <div className="mt-1 text-sm text-muted-foreground">
                  input {formatCompact(usage.totals.inputTokens)} · output {formatCompact(usage.totals.outputTokens)}
                </div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  Requests
                  <Bot className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatNumber(usage.totals.requests)}</div>
                <div className="mt-1 text-sm text-muted-foreground">{usage.totals.failures} failed</div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  Success rate
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatPercent(usage.totals.successRate)}</div>
                <div className="mt-1 text-sm text-muted-foreground">of {formatNumber(usage.totals.requests)} requests</div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  Avg latency
                  <Timer className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatDuration(usage.totals.avgDurationMs)}</div>
                <div className="mt-1 text-sm text-muted-foreground">per request</div>
              </div>
            </div>
          ) : null}

          {isUsageLoading ? (
            <div className="flex min-h-24 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : !usage || usage.byProject.length === 0 ? (
            <Empty className="min-h-32 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Cpu />
                </EmptyMedia>
                <EmptyTitle>No token usage yet</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Project</TableHead>
                  <TableHead className="text-right">Tokens</TableHead>
                  <TableHead className="text-right">Requests</TableHead>
                  <TableHead className="text-right">Success rate</TableHead>
                  <TableHead className="text-right">Avg latency</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {usage.byProject.map((row) => (
                  <TableRow key={row.projectId ?? 'unknown'}>
                    <TableCell>
                      <div className="min-w-0">
                        {row.projectId ? (
                          <NavLink to={`/app/projects/${row.projectId}`} className="font-medium hover:underline">
                            {projectTitleById.get(row.projectId) ?? 'Unknown project'}
                          </NavLink>
                        ) : (
                          <span className="font-medium">Unknown project</span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {formatCompact(row.inputTokens + row.outputTokens)}
                    </TableCell>
                    <TableCell className="text-right">{formatNumber(row.requests)}</TableCell>
                    <TableCell className="text-right">{formatPercent(row.successRate)}</TableCell>
                    <TableCell className="text-right">{formatDuration(row.avgDurationMs)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>

        {usage && usage.byProviderModel.length > 0 ? (
          <div className="rounded-md border bg-background p-4">
            <div className="mb-4 flex items-center gap-2">
              <h2 className="text-sm font-semibold tracking-normal">Models</h2>
              {isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Provider</TableHead>
                  <TableHead>Model</TableHead>
                  <TableHead className="text-right">Tokens</TableHead>
                  <TableHead className="text-right">Requests</TableHead>
                  <TableHead className="text-right">Success rate</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {usage.byProviderModel.map((row) => (
                  <TableRow key={`${row.provider}:${row.model ?? 'unknown'}`}>
                    <TableCell className="font-medium capitalize">{row.provider}</TableCell>
                    <TableCell>
                      <span className="font-mono text-xs text-muted-foreground">{row.model ?? '—'}</span>
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {formatCompact(row.inputTokens + row.outputTokens)}
                    </TableCell>
                    <TableCell className="text-right">{formatNumber(row.requests)}</TableCell>
                    <TableCell className="text-right">{formatPercent(row.successRate)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : null}
        </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
