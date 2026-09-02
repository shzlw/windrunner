import { useCallback, useEffect, useState } from 'react'
import { NavLink } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Bookmark, ChevronLeft, ChevronRight, Loader2, X } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import { listSubscriptions, unsubscribeWorkItem, type SubscribedWorkItem } from '@/lib/api'
import { cn } from '@/lib/utils'
import { workItemTypeBadgeClass } from '@/lib/typeBadges'
import { translateWorkItemType } from '@/i18n/labels'

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function SubscriptionsPage() {
  const { t } = useTranslation()
  const [items, setItems] = useState<SubscribedWorkItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [isLoading, setIsLoading] = useState(true)
  const [unsubscribingWorkItemIds, setUnsubscribingWorkItemIds] = useState<ReadonlySet<string>>(new Set())

  const loadPage = useCallback(async (nextPage: number) => {
    setIsLoading(true)
    try {
      const data = await listSubscriptions(nextPage, pageSize)
      setItems(data.items)
      setTotalPages(data.totalPages)
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : t('subscriptions.failedLoad'))
      setItems([])
      setTotalPages(0)
    } finally {
      setIsLoading(false)
    }
  }, [pageSize])

  useEffect(() => {
    queueMicrotask(() => {
      void loadPage(page)
    })
  }, [loadPage, page])

  async function handleUnsubscribe(item: SubscribedWorkItem) {
    setUnsubscribingWorkItemIds((current) => new Set(current).add(item.workItemId))
    try {
      await unsubscribeWorkItem(item.projectId, item.workItemId)
      setItems((current) => current.filter((entry) => entry.workItemId !== item.workItemId))
      toast.success(t('subscriptions.unsubscribed', { title: item.workItemTitle }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('subscriptions.failedUnsubscribe'))
    } finally {
      setUnsubscribingWorkItemIds((current) => {
        const next = new Set(current)
        next.delete(item.workItemId)
        return next
      })
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
        <h1 className="text-xl font-semibold leading-none tracking-normal">{t('subscriptions.pageTitle')}</h1>
      </div>

      <div className="min-w-0 flex-1 space-y-2 overflow-auto p-3 md:p-4">
        <div className="rounded-md border bg-background p-3">
          {isLoading ? (
            <div className="flex min-h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : items.length === 0 ? (
            <Empty className="min-h-64 border-0">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Bookmark />
                </EmptyMedia>
                <EmptyTitle>{t('subscriptions.empty')}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('subscriptions.workItem')}</TableHead>
                    <TableHead>{t('common.project')}</TableHead>
                    <TableHead>{t('subscriptions.subscribed')}</TableHead>
                    <TableHead className="text-right">{t('subscriptions.action')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => {
                    const isUnsubscribing = unsubscribingWorkItemIds.has(item.workItemId)
                    return (
                      <TableRow key={item.workItemId}>
                        <TableCell>
                          <div className="min-w-0">
                            <div className="flex min-w-0 items-center gap-2">
                              <Badge variant="outline" className={cn('shrink-0 font-medium uppercase', workItemTypeBadgeClass(item.workItemType))}>
                                {translateWorkItemType(item.workItemType, t)}
                              </Badge>
                              <NavLink
                                to={`/app/projects/${item.projectId}?workItemId=${item.workItemId}`}
                                className="truncate font-medium hover:underline"
                              >
                                {item.workItemTitle}
                              </NavLink>
                            </div>
                            {item.parentWorkItemId && item.parentWorkItemTitle ? (
                              <div className="mt-0.5 truncate text-xs text-muted-foreground">
                                {t('subscriptions.under')} {item.parentWorkItemTitle}
                              </div>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell>
                          <NavLink to={`/app/projects/${item.projectId}`} className="font-medium hover:underline">
                            {item.projectName}
                          </NavLink>
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-muted-foreground">{formatDate(item.subscribedAt)}</TableCell>
                        <TableCell className="text-right">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            className="gap-1.5"
                            disabled={isUnsubscribing}
                            onClick={() => void handleUnsubscribe(item)}
                          >
                            {isUnsubscribing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <X className="h-3.5 w-3.5" />}
                            {t('subscriptions.unsubscribe')}
                          </Button>
                        </TableCell>
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
                    disabled={page === 0 || isLoading}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">{t('common.pageOf', { page: page + 1, total: Math.max(totalPages, 1) })}</span>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={isLoading || totalPages === 0 || page >= totalPages - 1}
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
                      disabled={isLoading}
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
    </div>
  )
}
