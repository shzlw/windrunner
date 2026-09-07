import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { NavLink } from 'react-router'
import {
  addDays,
  addMonths,
  addWeeks,
  endOfMonth,
  endOfWeek,
  format,
  formatISO,
  isSameMonth,
  isToday,
  startOfDay,
  startOfMonth,
  startOfWeek,
} from 'date-fns'
import { ChevronLeft, ChevronRight, Loader2, Plus, Save, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Textarea } from '@/components/ui/textarea'
import {
  createCalendarEvent,
  deleteCalendarEvent,
  listCalendarEvents,
  listCalendarScopes,
  listCalendarWorkItems,
  updateCalendarEvent,
  type AuthUser,
  type CalendarEvent,
  type CalendarEventRequest,
  type CalendarScope,
  type CalendarScopeType,
  type CalendarWorkItem,
} from '@/lib/api'
import { cn } from '@/lib/utils'

type CalendarView = 'month' | 'week' | 'team'
type EventForm = {
  title: string
  startsAt: string
  endsAt: string
  description: string
  allDay: boolean
}

type CalendarScheduleRow = {
  type: CalendarScopeType
  id: string
  name: string
}

function isAdminLike(user: AuthUser | null) {
  return user?.globalRole === 'ADMIN' || user?.globalRole === 'SUPERADMIN'
}

function formatInputDate(value: string) {
  return format(new Date(value), "yyyy-MM-dd'T'HH:mm")
}

const EVENT_ACCENT = 'border-l-emerald-500 bg-emerald-50 text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200'
const WORK_ITEM_ACCENT = 'border-l-sky-500 bg-sky-50 text-sky-800 dark:bg-sky-950/30 dark:text-sky-200'
const WORK_ITEM_PAUSED_ACCENT = 'border-l-amber-500 bg-amber-50 text-amber-800 dark:bg-amber-950/30 dark:text-amber-200'
const WORK_ITEM_OVERDUE_ACCENT = 'border-l-rose-500 bg-rose-50 text-rose-800 dark:bg-rose-950/30 dark:text-rose-200'

function overlapsDay(event: CalendarEvent, day: Date) {
  const dayStart = startOfDay(day)
  const dayEnd = addDays(dayStart, 1)
  return new Date(event.endsAt) > dayStart && new Date(event.startsAt) < dayEnd
}

function workItemSpansDay(item: CalendarWorkItem, day: Date) {
  const dayKey = format(day, 'yyyy-MM-dd')
  if (!item.startedOn) return item.dueDate === dayKey
  if (dayKey < item.startedOn) return false
  if (!item.dueDate) return true
  const endKey = item.overdue ? [item.dueDate, format(new Date(), 'yyyy-MM-dd')].sort().at(-1) ?? item.dueDate : item.dueDate
  return dayKey <= endKey
}

function workItemAccent(item: CalendarWorkItem) {
  if (item.overdue) return WORK_ITEM_OVERDUE_ACCENT
  if (item.status === 'BLOCKED' || item.status === 'WAITING') return WORK_ITEM_PAUSED_ACCENT
  return WORK_ITEM_ACCENT
}

function getDefaultForm(day: Date): EventForm {
  const start = new Date(day)
  start.setHours(9, 0, 0, 0)
  const end = new Date(start)
  end.setHours(10)
  return {
    title: '',
    startsAt: formatInputDate(start.toISOString()),
    endsAt: formatInputDate(end.toISOString()),
    description: '',
    allDay: false,
  }
}

export default function CalendarPage({ currentUser }: { currentUser: AuthUser | null }) {
  const [scopes, setScopes] = useState<CalendarScope[]>([])
  const [selectedScopeKeys, setSelectedScopeKeys] = useState<string[]>([])
  const [currentDate, setCurrentDate] = useState(() => new Date())
  const [view, setView] = useState<CalendarView>('team')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [workItems, setWorkItems] = useState<CalendarWorkItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState<CalendarEvent | null>(null)
  const [form, setForm] = useState<EventForm>(() => getDefaultForm(new Date()))

  useEffect(() => {
    if (!currentUser) return
    const defaultScope = `USER:${currentUser.id}`
    setSelectedScopeKeys([defaultScope])
    let cancelled = false
    void listCalendarScopes()
      .then((nextScopes) => {
        if (!cancelled) setScopes(nextScopes)
      })
      .catch((error) => {
        if (!cancelled) toast.error(error instanceof Error ? error.message : 'Unable to load calendar scopes')
      })
    return () => {
      cancelled = true
    }
  }, [currentUser?.id])

  const selectedScopes = useMemo(() => selectedScopeKeys.flatMap((scopeKey) => {
    const separator = scopeKey.indexOf(':')
    if (separator < 0) return []
    return [{
      type: scopeKey.slice(0, separator) as CalendarScopeType,
      id: scopeKey.slice(separator + 1),
    }]
  }), [selectedScopeKeys])

  const range = useMemo(() => {
    const start = view === 'month'
      ? startOfWeek(startOfMonth(currentDate))
      : startOfWeek(currentDate)
    const end = view === 'month'
      ? addDays(endOfWeek(endOfMonth(currentDate)), 1)
      : addDays(endOfWeek(currentDate), 1)
    return { from: formatISO(start), to: formatISO(end) }
  }, [currentDate, view])

  useEffect(() => {
    if (!currentUser || selectedScopes.length === 0) {
      setEvents([])
      setWorkItems([])
      setIsLoading(false)
      return
    }
    let cancelled = false
    setIsLoading(true)
    setLoadError(null)
    void Promise.all([
      Promise.all(selectedScopes.map((scope) => listCalendarEvents(range.from, range.to, scope.type, scope.id))),
      Promise.all(selectedScopes.map((scope) => listCalendarWorkItems(range.from, range.to, scope.type, scope.id))),
    ])
      .then(([eventGroups, workItemGroups]) => {
        if (!cancelled) {
          const uniqueEvents = new Map<string, CalendarEvent>()
          eventGroups.flat().forEach((event) => uniqueEvents.set(event.id, event))
          setEvents(Array.from(uniqueEvents.values()))
          const uniqueWorkItems = new Map<string, CalendarWorkItem>()
          workItemGroups.flat().forEach((item) => uniqueWorkItems.set(`${item.workItemId}:${item.assigneeType}:${item.assigneeId}`, item))
          setWorkItems(Array.from(uniqueWorkItems.values()))
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setEvents([])
          setWorkItems([])
          setLoadError(error instanceof Error ? error.message : 'Unable to load calendar events')
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [currentUser?.id, range.from, range.to, selectedScopes])

  const ownerNames = useMemo(() => {
    const names = new Map<string, string>()
    if (currentUser) names.set(currentUser.id, currentUser.displayName || currentUser.username)
    scopes.forEach((scope) => scope.members.forEach((member) => names.set(member.userId, member.displayName)))
    return names
  }, [currentUser, scopes])

  const selectedPeople = useMemo(() => Array.from(ownerNames.entries())
    .filter(([userId]) => selectedScopeKeys.includes(`USER:${userId}`))
    .sort((left, right) => left[1].localeCompare(right[1]) || left[0].localeCompare(right[0])), [selectedScopeKeys, ownerNames])

  const teamNames = useMemo(() => new Map(scopes.map((scope) => [scope.teamId, scope.teamName])), [scopes])
  const selectedTeams = useMemo(() => scopes
    .filter((scope) => selectedScopeKeys.includes(`TEAM:${scope.teamId}`))
    .map((scope) => ({ type: 'TEAM' as const, id: scope.teamId, name: scope.teamName })), [scopes, selectedScopeKeys])
  const selectedRows = useMemo<CalendarScheduleRow[]>(() => [
    ...selectedTeams,
    ...selectedPeople.map(([id, name]) => ({ type: 'USER' as const, id, name })),
  ], [selectedPeople, selectedTeams])
  const unscheduledWorkItems = useMemo(() => workItems.filter((item) => !item.startedOn), [workItems])

  const visibleEvents = events

  const monthDays = useMemo(() => {
    const start = startOfWeek(startOfMonth(currentDate))
    const end = endOfWeek(endOfMonth(currentDate))
    const days: Date[] = []
    for (let day = start; day <= end; day = addDays(day, 1)) days.push(day)
    return days
  }, [currentDate])

  const weekDays = useMemo(() => {
    const start = startOfWeek(currentDate)
    return Array.from({ length: 7 }, (_, index) => addDays(start, index))
  }, [currentDate])

  const canEditEvent = !editingEvent || editingEvent.userId === currentUser?.id || isAdminLike(currentUser)

  function toggleScope(scopeKey: string) {
    setSelectedScopeKeys((current) => {
      const next = current.includes(scopeKey)
        ? current.filter((key) => key !== scopeKey)
        : [...current, scopeKey]
      if (!scopeKey.startsWith('USER:')) return next
      const userId = scopeKey.slice('USER:'.length)
      return next.filter((key) => !scopes.some((scope) =>
        key === `TEAM:${scope.teamId}` && scope.members.some((member) => member.userId === userId)))
    })
  }

  function toggleTeam(scope: CalendarScope) {
    const teamKey = `TEAM:${scope.teamId}`
    const keys = scope.members.map((member) => `USER:${member.userId}`)
    setSelectedScopeKeys((current) => current.includes(teamKey)
      ? current.filter((key) => key !== teamKey && !keys.includes(key))
      : Array.from(new Set([...current, teamKey, ...keys])))
    setView('team')
  }

  function toggleMember(teamId: string, userId: string) {
    const userKey = `USER:${userId}`
    setSelectedScopeKeys((current) => {
      const next = current.includes(userKey)
        ? current.filter((key) => key !== userKey)
        : [...current, userKey]
      return next.filter((key) => key !== `TEAM:${teamId}`)
    })
  }

  function openNewEvent(day = currentDate) {
    setEditingEvent(null)
    setForm(getDefaultForm(day))
    setSheetOpen(true)
  }

  function openEvent(event: CalendarEvent) {
    setEditingEvent(event)
    setForm({
      title: event.title,
      startsAt: formatInputDate(event.startsAt),
      endsAt: formatInputDate(event.endsAt),
      description: event.description ?? '',
      allDay: event.allDay,
    })
    setSheetOpen(true)
  }

  function movePeriod(direction: number) {
    setCurrentDate((date) => view === 'month' ? addMonths(date, direction) : addWeeks(date, direction))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!currentUser || !canEditEvent || !form.title.trim()) return
    setIsSaving(true)
    const request: CalendarEventRequest = {
      userId: editingEvent?.userId ?? currentUser.id,
      title: form.title.trim(),
      description: form.description.trim() || null,
      startsAt: new Date(form.startsAt).toISOString(),
      endsAt: new Date(form.endsAt).toISOString(),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      allDay: form.allDay,
    }
    try {
      const saved = editingEvent
        ? await updateCalendarEvent(editingEvent.id, request)
        : await createCalendarEvent(request)
      setEvents((current) => editingEvent
        ? current.map((item) => item.id === saved.id ? saved : item)
        : [...current, saved])
      setSheetOpen(false)
      toast.success(editingEvent ? 'Calendar event updated' : 'Calendar event created')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to save calendar event')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleDelete() {
    if (!editingEvent) return
    try {
      await deleteCalendarEvent(editingEvent.id)
      setEvents((current) => current.filter((item) => item.id !== editingEvent.id))
      setSheetOpen(false)
      toast.success('Calendar event deleted')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to delete calendar event')
    }
  }

  function renderEvent(event: CalendarEvent) {
    const showEventOwner = selectedScopes.length > 1 || selectedScopes.some((scope) => scope.type === 'TEAM')
    return (
      <button
        key={event.id}
        type="button"
        className={cn('w-full overflow-hidden rounded border-l-4 px-2 py-1 text-left text-xs hover:brightness-95', EVENT_ACCENT)}
        onClick={() => openEvent(event)}
        title={`${event.title} · ${ownerNames.get(event.userId) ?? 'Unknown user'}`}
      >
        <span className="block truncate font-medium">{event.allDay ? event.title : `${format(new Date(event.startsAt), 'h:mm a')} ${event.title}`}</span>
        {showEventOwner ? <span className="block truncate text-[10px] opacity-75">{ownerNames.get(event.userId) ?? 'Unknown user'}</span> : null}
      </button>
    )
  }

  function renderWorkItem(item: CalendarWorkItem) {
    const showAssignee = selectedScopes.length > 1 || selectedScopes.some((scope) => scope.type === 'TEAM')
    const assigneeName = item.assigneeType === 'TEAM'
      ? teamNames.get(item.assigneeId) ?? 'Unknown team'
      : ownerNames.get(item.assigneeId) ?? 'Unknown user'
    const href = `/app/projects/${item.projectId}?workItemId=${encodeURIComponent(item.workItemId)}`
    return (
      <NavLink
        key={`${item.workItemId}:${item.assigneeType}:${item.assigneeId}`}
        to={href}
        className={cn('block w-full overflow-hidden rounded border-l-4 px-2 py-1 text-left text-xs hover:brightness-95', workItemAccent(item))}
        title={`${item.title} · ${item.projectName}`}
      >
        <span className="block truncate font-medium">{item.title}</span>
        <span className="block truncate text-[10px] opacity-75">{item.projectName} · {item.status.replaceAll('_', ' ')}</span>
        {showAssignee ? <span className="block truncate text-[10px] opacity-75">{assigneeName}</span> : null}
        {!item.startedOn && item.dueDate ? <span className="block truncate text-[10px] opacity-75">Due {format(new Date(`${item.dueDate}T00:00:00`), 'MMM d')}</span> : null}
      </NavLink>
    )
  }

  function renderDayItems(day: Date, limit?: number) {
    const entries: Array<
      { kind: 'event'; value: CalendarEvent } |
      { kind: 'workItem'; value: CalendarWorkItem }
    > = [
      ...visibleEvents.filter((event) => overlapsDay(event, day)).map((value) => ({ kind: 'event' as const, value })),
      ...workItems.filter((item) => workItemSpansDay(item, day)).map((value) => ({ kind: 'workItem' as const, value })),
    ]
    return entries.slice(0, limit ?? entries.length).map((entry) => entry.kind === 'event' ? renderEvent(entry.value) : renderWorkItem(entry.value))
  }

  function renderScheduleRow(row: CalendarScheduleRow) {
    const rowEvents = row.type === 'USER' ? events.filter((event) => event.userId === row.id) : []
    const rowWorkItems = workItems.filter((item) => item.assigneeType === row.type && item.assigneeId === row.id)
    return (
      <tr key={`${row.type}:${row.id}`} className="border-b">
        <th scope="row" className="sticky left-0 z-10 bg-background p-3 text-left align-top font-medium">
          <span className="block truncate">{row.name}</span>
          <span className="block text-[10px] font-normal uppercase text-muted-foreground">{row.type === 'TEAM' ? 'Team' : 'Person'}</span>
        </th>
        {weekDays.map((day) => {
          const dayEvents = rowEvents.filter((event) => overlapsDay(event, day))
          const dayWorkItems = rowWorkItems.filter((item) => workItemSpansDay(item, day))
          return (
            <td key={day.toISOString()} className={cn('border-l p-2 align-top', (dayEvents.length > 0 || dayWorkItems.length > 0) && 'bg-muted/20')}>
              <div className="min-h-24 space-y-2">
                {dayWorkItems.map(renderWorkItem)}
                {dayEvents.map((event) => (
                  <button key={event.id} type="button" className={cn('w-full rounded border-l-4 p-2 text-left text-xs', EVENT_ACCENT)} onClick={() => openEvent(event)}>
                    <span className="block break-words font-medium">{event.title}</span>
                    <span className="mt-1 block">{event.allDay ? 'All day' : `${format(new Date(Math.max(new Date(event.startsAt).getTime(), startOfDay(day).getTime())), 'h:mm a')}–${format(new Date(Math.min(new Date(event.endsAt).getTime(), addDays(startOfDay(day), 1).getTime())), 'h:mm a')}`}</span>
                  </button>
                ))}
              </div>
            </td>
          )
        })}
      </tr>
    )
  }

  if (!currentUser) return null

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-12 shrink-0 items-center border-b px-4 py-2 md:px-5">
        <h1 className="text-xl font-semibold leading-none tracking-normal">Calendar</h1>
      </div>

      <div className="shrink-0 border-b bg-background px-4 py-3 md:px-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Button type="button" className="gap-2" onClick={() => openNewEvent()}><Plus className="size-4" /> New event</Button>
            <Button type="button" variant="outline" size="sm" onClick={() => setCurrentDate(new Date())}>Today</Button>
            <Button type="button" variant="outline" size="icon-sm" onClick={() => movePeriod(-1)} aria-label="Previous period"><ChevronLeft /></Button>
            <Button type="button" variant="outline" size="icon-sm" onClick={() => movePeriod(1)} aria-label="Next period"><ChevronRight /></Button>
            <span className="min-w-32 text-sm font-semibold">{format(currentDate, view === 'month' ? 'MMMM yyyy' : 'MMM d, yyyy')}</span>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex rounded-lg border p-0.5">
              {(['team', 'month', 'week'] as CalendarView[]).map((item) => (
                <Button key={item} type="button" size="sm" variant={view === item ? 'secondary' : 'ghost'} onClick={() => setView(item)}>
                  {item === 'team' ? 'Team schedule' : item === 'month' ? 'Month' : 'Week'}
                </Button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <main className="min-h-0 min-w-0 flex-1 overflow-auto">
        <div className="flex min-h-full min-w-0 flex-col xl:flex-row">
          <aside className="flex shrink-0 flex-col border-b xl:w-[15rem] xl:border-b-0 xl:border-r">
            <div className="shrink-0 border-b px-3 py-3 md:px-4">
              <h2 className="text-sm font-semibold">Calendars</h2>
            </div>
            <div className="space-y-1 px-2 py-2 md:px-3 xl:pr-4">
              <div className="px-2 pb-1 text-xs font-medium text-muted-foreground">My calendar</div>
              <Button
                type="button"
                variant={selectedScopeKeys.includes(`USER:${currentUser.id}`) ? 'secondary' : 'ghost'}
                className="w-full justify-start"
                onClick={() => toggleScope(`USER:${currentUser.id}`)}
              >
                {ownerNames.get(currentUser.id) ?? currentUser.username}
              </Button>

              {scopes.length > 0 ? <div className="pt-3">
                <div className="px-2 pb-1 text-xs font-medium text-muted-foreground">Teams</div>
                {scopes.map((scope) => (
                  <div key={scope.teamId} className="py-2">
                    <label className="flex items-center gap-2 px-2 text-sm font-semibold">
                      <Checkbox checked={selectedScopeKeys.includes(`TEAM:${scope.teamId}`)} disabled={scope.members.length === 0} onCheckedChange={() => toggleTeam(scope)} />
                      <span className="min-w-0 flex-1 truncate">{scope.teamName}</span>
                      <span className="text-xs text-muted-foreground">{scope.members.length}</span>
                    </label>
                    <div className="mt-2 space-y-1 pl-5">
                      {scope.members.map((member) => (
                        <label key={member.userId} className="flex items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-muted">
                          <Checkbox checked={selectedScopeKeys.includes(`USER:${member.userId}`)} onCheckedChange={() => toggleMember(scope.teamId, member.userId)} />
                          <span className="truncate">{member.displayName}</span>
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div> : null}
            </div>
          </aside>

          <section className="min-w-0 flex-1 p-3 md:p-4">
          <div>
            {isLoading ? (
              <div className="flex min-h-96 items-center justify-center text-sm text-muted-foreground">Loading calendar…</div>
            ) : loadError ? (
              <p role="alert" className="p-4 text-sm text-destructive">{loadError}</p>
            ) : selectedRows.length === 0 ? (
              <p className="p-4 text-sm text-muted-foreground">Select a team or people to compare their schedules.</p>
            ) : (
              <>
                {unscheduledWorkItems.length > 0 ? (
                  <div className="mb-4 border-b pb-3">
                    <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
                      Unscheduled work
                      <span className="text-xs font-normal text-muted-foreground">{unscheduledWorkItems.length}</span>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {unscheduledWorkItems.map((item) => <div key={`${item.workItemId}:${item.assigneeType}:${item.assigneeId}`} className="min-w-56 max-w-full">{renderWorkItem(item)}</div>)}
                    </div>
                  </div>
                ) : null}

                {view === 'team' ? (
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                      <span className="font-medium">{selectedRows.length} calendars · {format(weekDays[0], 'MMM d')}–{format(weekDays[6], 'MMM d, yyyy')}</span>
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full min-w-[1100px] table-fixed border-collapse text-sm">
                        <thead>
                          <tr className="border-b bg-muted/30">
                            <th className="sticky left-0 z-10 w-40 bg-background p-3 text-left">Calendar</th>
                            {weekDays.map((day) => <th key={day.toISOString()} className={cn('border-l p-2 text-left font-medium', isToday(day) && 'text-primary')}>{format(day, 'EEE, MMM d')}</th>)}
                          </tr>
                        </thead>
                        <tbody>{selectedRows.map(renderScheduleRow)}</tbody>
                      </table>
                    </div>
                  </div>
                ) : view === 'month' ? (
                  <div className="overflow-x-auto">
                    <div className="min-w-[760px]">
                      <div className="grid grid-cols-7 border-b bg-muted/30">
                        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <div key={day} className="px-2 py-2 text-xs font-medium text-muted-foreground">{day}</div>)}
                      </div>
                      <div className="grid grid-cols-7">
                        {monthDays.map((day) => (
                          <div key={day.toISOString()} className={cn('min-h-32 border-b border-l p-1.5', !isSameMonth(day, currentDate) && 'bg-muted/15')}>
                            <button type="button" className={cn('mb-1 flex size-6 items-center justify-center rounded-full text-xs', isToday(day) && 'bg-primary font-semibold text-primary-foreground', !isSameMonth(day, currentDate) && 'text-muted-foreground')} onClick={() => openNewEvent(day)}>{format(day, 'd')}</button>
                            <div className="space-y-1">{renderDayItems(day, 4)}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <div className="min-w-[760px]">
                      <div className="grid grid-cols-7 border-b bg-muted/30">
                        {weekDays.map((day) => <div key={day.toISOString()} className={cn('border-l px-2 py-2 text-xs', isToday(day) && 'font-semibold text-primary')}><div>{format(day, 'EEE')}</div><div className="text-muted-foreground">{format(day, 'MMM d')}</div></div>)}
                      </div>
                      <div className="grid min-h-80 grid-cols-7">
                        {weekDays.map((day) => <div key={day.toISOString()} className="min-h-80 space-y-1 border-b border-l p-2">{renderDayItems(day)}</div>)}
                      </div>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
          </section>
        </div>
      </main>

      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent side="right" className="!w-full overflow-y-auto p-0 sm:!max-w-xl" showCloseButton={false}>
          <SheetHeader className="flex min-h-12 flex-row items-center justify-between gap-3 border-b px-4 py-2">
            <SheetTitle className="text-xl">{editingEvent ? (canEditEvent ? 'Edit calendar event' : 'Calendar event') : 'New calendar event'}</SheetTitle>
            <SheetClose
              render={<Button variant="ghost" size="icon-sm" className="-mr-2" />}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </SheetClose>
          </SheetHeader>

          <div className="flex-1 px-4 py-3">
          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-title">Title</label><Input id="calendar-event-title" value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} disabled={!canEditEvent} required /></div>
            <div className="space-y-2"><span className="block text-sm font-semibold">Calendar</span><p className="text-sm text-muted-foreground">{ownerNames.get(editingEvent?.userId ?? currentUser.id) ?? currentUser.username}</p></div>
            <label className="flex items-center gap-2 text-sm"><Checkbox checked={form.allDay} onCheckedChange={(checked) => setForm((current) => ({ ...current, allDay: checked }))} disabled={!canEditEvent} />All day</label>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-start">Starts</label><Input id="calendar-event-start" type="datetime-local" value={form.startsAt} onChange={(event) => setForm((current) => ({ ...current, startsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-end">Ends</label><Input id="calendar-event-end" type="datetime-local" value={form.endsAt} onChange={(event) => setForm((current) => ({ ...current, endsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
            </div>
            <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-description">Notes</label><Textarea id="calendar-event-description" rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} disabled={!canEditEvent} placeholder="Optional context" /></div>
            <div className="flex flex-wrap items-center gap-2">
              {canEditEvent ? <Button type="submit" className="gap-2" disabled={isSaving || !form.title.trim()}>{isSaving ? <Loader2 className="size-4 animate-spin" /> : editingEvent ? <Save className="size-4" /> : <Plus className="size-4" />}{isSaving ? 'Saving…' : editingEvent ? 'Save changes' : 'Create event'}</Button> : null}
              <Button type="button" variant="outline" className="gap-2" disabled={isSaving} onClick={() => setSheetOpen(false)}><X className="size-4" />{canEditEvent ? 'Cancel' : 'Close'}</Button>
            </div>
            {editingEvent && canEditEvent ? <div className="border-t pt-4"><DeleteConfirmPopover trigger={<Button type="button" variant="destructive" disabled={isSaving}><Trash2 />Delete event</Button>} title="Delete calendar event?" description="This permanently removes the event from the calendar." onConfirm={handleDelete} /></div> : null}
          </form>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
