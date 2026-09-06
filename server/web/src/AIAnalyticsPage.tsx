import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, Bot, CheckCircle2, Cpu, Loader2, RefreshCw, Timer, TrendingUp, XCircle, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  getAiAnalytics,
  getLlmStatus,
  getLlmUsage,
  type AiAnalyticsSummary,
  type LlmStatus,
  type LlmUsageSummary,
} from '@/lib/api'

type DateRangeKey = 'all' | '7d' | '30d' | '90d'

const DATE_RANGE_OPTIONS: Array<{ value: DateRangeKey; label: string; days: number | null }> = [
  { value: 'all', label: 'All time', days: null },
  { value: '7d', label: 'Last 7 days', days: 7 },
  { value: '30d', label: 'Last 30 days', days: 30 },
  { value: '90d', label: 'Last 90 days', days: 90 },
]

function formatNumber(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value)
}

function formatPercent(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0, style: 'percent' }).format(value)
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

function formatModelName(model: string | null | undefined) {
  return model?.trim() || 'Unknown model'
}

function formatFeatureName(feature: string, t: TFunction) {
  const key = {
    CHAT: 'analytics.featureChat',
    ENTRY_AI_REVIEW: 'analytics.featureEntryAiReview',
    WORK_ITEM_AI_REVIEW: 'analytics.featureWorkItemAiReview',
  }[feature]
  return key ? t(key) : feature.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')
}

function formatEntityName(entityType: string, t: TFunction) {
  const key = {
    TEAM: 'analytics.entityTeam',
    TEAM_MEMBERSHIP: 'analytics.entityTeamMembership',
    PROJECT_MEMBERSHIP: 'analytics.entityProjectMembership',
    USER_PROFILE: 'analytics.entityUserProfile',
    USER_ACCESS: 'analytics.entityUserAccess',
    WORK_ITEM: 'analytics.entityWorkItem',
    ENTRY: 'analytics.entityEntry',
    RELATIONSHIP: 'analytics.entityRelationship',
  }[entityType.toUpperCase()]
  return key ? t(key) : entityType.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')
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
  const { t } = useTranslation()
  if (!usage || usage.byFeature.length === 0) {
    return (
      <Empty className="min-h-32 border-0">
        <EmptyHeader>
          <EmptyMedia variant="icon"><Bot /></EmptyMedia>
          <EmptyTitle>{t('analytics.noFeatureUsage')}</EmptyTitle>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('analytics.feature')}</TableHead>
          <TableHead className="text-right">{t('common.tokens')}</TableHead>
          <TableHead className="text-right">{t('common.requests')}</TableHead>
          <TableHead className="text-right">{t('common.failures')}</TableHead>
          <TableHead className="text-right">{t('common.successRate')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {usage.byFeature.map((row) => (
          <TableRow key={row.feature}>
            <TableCell className="font-medium">{formatFeatureName(row.feature, t)}</TableCell>
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
  const { t } = useTranslation()
  const [analytics, setAnalytics] = useState<AiAnalyticsSummary | null>(null)
  const [llmStatus, setLlmStatus] = useState<LlmStatus | null>(null)
  const [usage, setUsage] = useState<LlmUsageSummary | null>(null)
  const [isUsageLoading, setIsUsageLoading] = useState(true)
  const [dateRange, setDateRange] = useState<DateRangeKey>('all')
  const [isLoading, setIsLoading] = useState(true)

  const loadUsage = useCallback(async () => {
    setIsUsageLoading(true)

    try {
      const days = DATE_RANGE_OPTIONS.find((option) => option.value === dateRange)?.days ?? undefined
      setUsage(await getLlmUsage(undefined, days))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('analytics.failedLoadTokens'))
    } finally {
      setIsUsageLoading(false)
    }
  }, [dateRange, t])

  useEffect(() => {
    const loadTimer = window.setTimeout(() => {
      void loadUsage()
    }, 0)

    return () => window.clearTimeout(loadTimer)
  }, [loadUsage])

  const loadImpact = useCallback(async () => {
    setIsLoading(true)

    try {
      const days = DATE_RANGE_OPTIONS.find((option) => option.value === dateRange)?.days ?? undefined
      const [nextAnalytics, nextLlmStatus] = await Promise.all([
        getAiAnalytics(days),
        getLlmStatus().catch(() => ({ provider: 'none', available: false })),
      ])
      setAnalytics(nextAnalytics)
      setLlmStatus(nextLlmStatus)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('analytics.failedLoadReport'))
    } finally {
      setIsLoading(false)
    }
  }, [dateRange, t])

  useEffect(() => {
    const loadTimer = window.setTimeout(() => {
      void loadImpact()
    }, 0)

    return () => window.clearTimeout(loadTimer)
  }, [loadImpact])

  const decidedChanges = analytics
    ? analytics.changesAccepted + analytics.changesRejected + analytics.changesNeedsUpdate
    : 0
  const acceptanceRate = decidedChanges === 0 ? 0 : (analytics?.changesAccepted ?? 0) / decidedChanges

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center gap-2 border-b px-4 py-2 md:px-5">
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('analytics.pageTitle')}</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-2 overflow-auto p-3 md:p-4">
        <div className="flex flex-col gap-2 sm:flex-row lg:items-center">
          <NativeSelect className="w-full sm:w-40" value={dateRange} onChange={(event) => setDateRange(event.target.value as DateRangeKey)}>
            {DATE_RANGE_OPTIONS.map((option) => (
              <NativeSelectOption key={option.value} value={option.value}>
                {option.value === 'all' ? t('analytics.allTime') : t('analytics.lastDays', { count: option.days })}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          <Button type="button" variant="outline" className="gap-2" onClick={() => { void loadImpact(); void loadUsage() }} disabled={isLoading || isUsageLoading}>
            {isLoading || isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            {t('analytics.refresh')}
          </Button>
        </div>

        {llmStatus && !llmStatus.available ? (
          <div className="flex items-center gap-2 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{t('analytics.unavailable')}</span>
          </div>
        ) : null}

        <Tabs defaultValue="overview" className="gap-4">
          <TabsList variant="line" className="justify-start overflow-x-auto overflow-y-hidden border-b">
            <TabsTrigger value="overview">{t('analytics.overview')}</TabsTrigger>
            <TabsTrigger value="usage">{t('analytics.usageReliability')}</TabsTrigger>
            <TabsTrigger value="outcomes">{t('analytics.roiOutcomes')}</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="mt-0 space-y-3">
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <MetricCard
                label={t('analytics.aiRequests')}
                value={usage ? formatNumber(usage.totals.requests) : '—'}
                description={usage ? t('analytics.failed', { count: formatNumber(usage.totals.failures) }) : t('analytics.loadingUsage')}
                icon={Bot}
              />
              <MetricCard
                label={t('analytics.totalTokens')}
                value={usage ? formatCompact(usage.totals.inputTokens + usage.totals.outputTokens) : '—'}
                description={t('analytics.inputOutput')}
                icon={Cpu}
              />
              <MetricCard
                label={t('analytics.changesAccepted')}
                value={isLoading || !analytics ? '—' : formatNumber(analytics.changesAccepted)}
                description={isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.proposedChanges', { count: formatNumber(analytics.changesProposed) })}
                icon={CheckCircle2}
              />
              <MetricCard
                label={t('analytics.acceptanceRate')}
                value={isLoading || !analytics ? '—' : formatPercent(acceptanceRate)}
                description={isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.decidedChanges', { count: formatNumber(decidedChanges) })}
                icon={TrendingUp}
              />
            </div>

            <div className="rounded-md border bg-background p-3">
              <div className="mb-4 flex items-center gap-2">
                <h2 className="text-sm font-semibold tracking-normal">{t('analytics.usageByFeature')}</h2>
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
        <div className="rounded-md border bg-background p-3">
          <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                {t('analytics.questionsAnswered')}
                <Bot className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{isLoading || !analytics ? '—' : formatNumber(analytics.questionsAnswered)}</div>
              <div className="mt-1 text-sm text-muted-foreground">{isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.questionsAsked', { count: formatNumber(analytics.questionsAsked) })}</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                {t('analytics.changesAccepted')}
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{isLoading || !analytics ? '—' : formatNumber(analytics.changesAccepted)}</div>
              <div className="mt-1 text-sm text-muted-foreground">{isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.proposedChanges', { count: formatNumber(analytics.changesProposed) })}</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                {t('analytics.acceptanceRate')}
                <TrendingUp className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{isLoading || !analytics ? '—' : formatPercent(acceptanceRate)}</div>
              <div className="mt-1 text-sm text-muted-foreground">{isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.decidedChanges', { count: formatNumber(decidedChanges) })}</div>
            </div>

            <div className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                {t('analytics.reworkRequested')}
                <XCircle className="h-4 w-4" />
              </div>
              <div className="mt-2 text-2xl font-semibold">{isLoading || !analytics ? '—' : formatNumber(analytics.changesRejected + analytics.changesNeedsUpdate)}</div>
              <div className="mt-1 text-sm text-muted-foreground">{isLoading || !analytics ? t('analytics.loadingOutcomes') : t('analytics.rejectedNeedsUpdate', { rejected: analytics.changesRejected, needsUpdate: analytics.changesNeedsUpdate })}</div>
            </div>
          </div>

          {isLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : !analytics || analytics.byEntity.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Bot />
                </EmptyMedia>
                <EmptyTitle>{t('analytics.noImpact')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('analytics.entity')}</TableHead>
                  <TableHead className="text-right">{t('analytics.proposed')}</TableHead>
                  <TableHead className="text-right">{t('analytics.accepted')}</TableHead>
                  <TableHead className="text-right">{t('analytics.rework')}</TableHead>
                  <TableHead className="text-right">{t('analytics.pending')}</TableHead>
                  <TableHead className="text-right">{t('analytics.rate')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {analytics.byEntity.map((entity) => {
                  const entityDecided = entity.changesAccepted + entity.changesRejected + entity.changesNeedsUpdate
                  return (
                  <TableRow key={entity.entityType}>
                    <TableCell className="font-medium">{formatEntityName(entity.entityType, t)}</TableCell>
                    <TableCell className="text-right">{formatNumber(entity.changesProposed)}</TableCell>
                    <TableCell className="text-right">{formatNumber(entity.changesAccepted)}</TableCell>
                    <TableCell className="text-right">{formatNumber(entity.changesRejected + entity.changesNeedsUpdate)}</TableCell>
                    <TableCell className="text-right">{formatNumber(entity.changesPending)}</TableCell>
                    <TableCell className="text-right">{formatPercent(entityDecided === 0 ? 0 : entity.changesAccepted / entityDecided)}</TableCell>
                  </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
        </TabsContent>

        <TabsContent value="usage" className="mt-0 space-y-3">
        <div className="rounded-md border bg-background p-3">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="text-sm font-semibold tracking-normal">{t('analytics.tokenUsage')}</h2>
            {isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
          </div>

          {usage ? (
            <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  {t('analytics.totalTokens')}
                  <Cpu className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">
                  {formatCompact(usage.totals.inputTokens + usage.totals.outputTokens)}
                </div>
                <div className="mt-1 text-sm text-muted-foreground">
                  {t('analytics.inputOutputValues', { input: formatCompact(usage.totals.inputTokens), output: formatCompact(usage.totals.outputTokens) })}
                </div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  {t('common.requests')}
                  <Bot className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatNumber(usage.totals.requests)}</div>
                <div className="mt-1 text-sm text-muted-foreground">{t('analytics.failed', { count: usage.totals.failures })}</div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  {t('common.successRate')}
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatPercent(usage.totals.successRate)}</div>
                <div className="mt-1 text-sm text-muted-foreground">{t('analytics.ofRequests', { count: formatNumber(usage.totals.requests) })}</div>
              </div>

              <div className="rounded-md border px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground">
                  {t('analytics.avgLatency')}
                  <Timer className="h-4 w-4" />
                </div>
                <div className="mt-2 text-2xl font-semibold">{formatDuration(usage.totals.avgDurationMs)}</div>
                <div className="mt-1 text-sm text-muted-foreground">{t('analytics.perRequest')}</div>
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
                <EmptyTitle>{t('analytics.noTokenUsage')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.project')}</TableHead>
                  <TableHead className="text-right">{t('common.tokens')}</TableHead>
                  <TableHead className="text-right">{t('common.requests')}</TableHead>
                  <TableHead className="text-right">{t('common.successRate')}</TableHead>
                  <TableHead className="text-right">{t('analytics.avgLatency')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {usage.byProject.map((row) => (
                  <TableRow key={row.projectId ?? 'unknown'}>
                    <TableCell>
                      <div className="min-w-0">
                        {row.projectId ? (
                          <NavLink to={`/app/projects/${row.projectId}`} className="font-medium hover:underline">
                            {row.projectId}
                          </NavLink>
                        ) : (
                          <span className="font-medium">{t('analytics.unscopedUsage')}</span>
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
          <div className="rounded-md border bg-background p-3">
            <div className="mb-4 flex items-center gap-2">
              <h2 className="text-sm font-semibold tracking-normal">{t('analytics.models')}</h2>
              {isUsageLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('analytics.provider')}</TableHead>
                  <TableHead>{t('analytics.model')}</TableHead>
                  <TableHead className="text-right">{t('common.tokens')}</TableHead>
                  <TableHead className="text-right">{t('common.requests')}</TableHead>
                  <TableHead className="text-right">{t('common.successRate')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {usage.byProviderModel.map((row) => (
                  <TableRow key={`${row.provider}:${row.model ?? 'unknown'}`}>
                    <TableCell className="font-medium capitalize">{row.provider}</TableCell>
                    <TableCell>
                      <span className="font-mono text-xs text-muted-foreground">{formatModelName(row.model)}</span>
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
