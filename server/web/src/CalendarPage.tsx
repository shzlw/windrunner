import { useEffect, useMemo, useState, type FormEvent } from 'react'
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
import { ChevronLeft, ChevronRight, Plus, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Textarea } from '@/components/ui/textarea'
import {
  createCalendarEvent,
  deleteCalendarEvent,
  listCalendarEvents,
  listCalendarScopes,
  updateCalendarEvent,
  type AuthUser,
  type CalendarEvent,
  type CalendarEventRequest,
  type CalendarScope,
  type CalendarScopeType,
} from '@/lib/api'
import { cn } from '@/lib/utils'

type CalendarView = 'month' | 'week'
type EventFilter = 'ALL' | 'BUSY' | 'TASK' | 'VACATION' | 'MEETING' | 'FOCUS'
type EventForm = {
  title: string
  eventType: string
  startsAt: string
  endsAt: string
  description: string
  allDay: boolean
  showAsBusy: boolean
}

const EVENT_FILTERS: Array<{ value: EventFilter; label: string }> = [
  { value: 'ALL', label: 'All events' },
  { value: 'BUSY', label: 'Busy time' },
  { value: 'TASK', label: 'Scheduled work' },
  { value: 'VACATION', label: 'Time off' },
  { value: 'MEETING', label: 'Meetings' },
  { value: 'FOCUS', label: 'Focus time' },
]

const EVENT_TYPES = [
  { value: 'TASK', label: 'Scheduled work' },
  { value: 'VACATION', label: 'Time off' },
  { value: 'MEETING', label: 'Meeting' },
  { value: 'FOCUS', label: 'Focus time' },
  { value: 'OTHER', label: 'Other' },
]

function isAdminLike(user: AuthUser | null) {
  return user?.globalRole === 'ADMIN' || user?.globalRole === 'SUPERADMIN'
}

function formatInputDate(value: string) {
  return format(new Date(value), "yyyy-MM-dd'T'HH:mm")
}

function eventAccent(eventType: string) {
  if (eventType === 'VACATION') return 'border-l-rose-500 bg-rose-50 text-rose-800 dark:bg-rose-950/30 dark:text-rose-200'
  if (eventType === 'MEETING') return 'border-l-violet-500 bg-violet-50 text-violet-800 dark:bg-violet-950/30 dark:text-violet-200'
  if (eventType === 'FOCUS') return 'border-l-amber-500 bg-amber-50 text-amber-800 dark:bg-amber-950/30 dark:text-amber-200'
  return 'border-l-emerald-500 bg-emerald-50 text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200'
}

function overlapsDay(event: CalendarEvent, day: Date) {
  const dayStart = startOfDay(day)
  const dayEnd = addDays(dayStart, 1)
  return new Date(event.endsAt) > dayStart && new Date(event.startsAt) < dayEnd
}

function getDefaultForm(day: Date): EventForm {
  const start = new Date(day)
  start.setHours(9, 0, 0, 0)
  const end = new Date(start)
  end.setHours(10)
  return {
    title: '',
    eventType: 'TASK',
    startsAt: formatInputDate(start.toISOString()),
    endsAt: formatInputDate(end.toISOString()),
    description: '',
    allDay: false,
    showAsBusy: true,
  }
}

export default function CalendarPage({ currentUser }: { currentUser: AuthUser | null }) {
  const [scopes, setScopes] = useState<CalendarScope[]>([])
  const [selectedScopeKeys, setSelectedScopeKeys] = useState<string[]>([])
  const [currentDate, setCurrentDate] = useState(() => new Date())
  const [view, setView] = useState<CalendarView>('month')
  const [filter, setFilter] = useState<EventFilter>('ALL')
  const [events, setEvents] = useState<CalendarEvent[]>([])
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
      setIsLoading(false)
      return
    }
    let cancelled = false
    setIsLoading(true)
    void Promise.all(selectedScopes.map((scope) => listCalendarEvents(range.from, range.to, scope.type, scope.id)))
      .then((eventGroups) => {
        if (!cancelled) {
          const uniqueEvents = new Map<string, CalendarEvent>()
          eventGroups.flat().forEach((event) => uniqueEvents.set(event.id, event))
          setEvents(Array.from(uniqueEvents.values()))
        }
      })
      .catch((error) => {
        if (!cancelled) toast.error(error instanceof Error ? error.message : 'Unable to load calendar events')
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

  const selectablePeople = useMemo(() => Array.from(ownerNames.entries())
    .filter(([userId]) => userId !== currentUser?.id)
    .sort((left, right) => left[1].localeCompare(right[1]) || left[0].localeCompare(right[0])), [currentUser?.id, ownerNames])

  const visibleEvents = useMemo(() => events.filter((event) => {
    if (filter === 'BUSY') return event.showAsBusy
    if (filter === 'ALL') return true
    return event.eventType === filter
  }), [events, filter])

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
      if (current.includes(scopeKey)) {
        return current.length === 1 ? current : current.filter((key) => key !== scopeKey)
      }
      return [...current, scopeKey]
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
      eventType: event.eventType,
      startsAt: formatInputDate(event.startsAt),
      endsAt: formatInputDate(event.endsAt),
      description: event.description ?? '',
      allDay: event.allDay,
      showAsBusy: event.showAsBusy,
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
      eventType: form.eventType,
      title: form.title.trim(),
      description: form.description.trim() || null,
      startsAt: new Date(form.startsAt).toISOString(),
      endsAt: new Date(form.endsAt).toISOString(),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      allDay: form.allDay,
      showAsBusy: form.showAsBusy,
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
        className={cn('w-full overflow-hidden rounded border-l-4 px-2 py-1 text-left text-xs hover:brightness-95', eventAccent(event.eventType))}
        onClick={() => openEvent(event)}
        title={`${event.title} · ${ownerNames.get(event.userId) ?? 'Unknown user'}`}
      >
        <span className="block truncate font-medium">{event.allDay ? event.title : `${format(new Date(event.startsAt), 'h:mm a')} ${event.title}`}</span>
        {showEventOwner ? <span className="block truncate text-[10px] opacity-75">{ownerNames.get(event.userId) ?? 'Unknown user'}</span> : null}
      </button>
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
            <NativeSelect aria-label="Filter events" value={filter} onChange={(event) => setFilter(event.target.value as EventFilter)} className="w-40">
              {EVENT_FILTERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </NativeSelect>
            <div className="flex rounded-lg border p-0.5">
              {(['month', 'week'] as CalendarView[]).map((item) => (
                <Button key={item} type="button" size="sm" variant={view === item ? 'secondary' : 'ghost'} onClick={() => setView(item)}>
                  {item === 'month' ? 'Month' : 'Week'}
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
                  <Button
                    key={scope.teamId}
                    type="button"
                    variant={selectedScopeKeys.includes(`TEAM:${scope.teamId}`) ? 'secondary' : 'ghost'}
                    className="w-full justify-start truncate"
                    onClick={() => toggleScope(`TEAM:${scope.teamId}`)}
                  >
                    {scope.teamName}
                  </Button>
                ))}
              </div> : null}

              {selectablePeople.length > 0 ? <div className="pt-3">
                <div className="px-2 pb-1 text-xs font-medium text-muted-foreground">People</div>
                {selectablePeople.map(([userId, displayName]) => (
                  <Button
                    key={userId}
                    type="button"
                    variant={selectedScopeKeys.includes(`USER:${userId}`) ? 'secondary' : 'ghost'}
                    className="w-full justify-start truncate"
                    onClick={() => toggleScope(`USER:${userId}`)}
                  >
                    {displayName}
                  </Button>
                ))}
              </div> : null}
            </div>
          </aside>

          <section className="min-w-0 flex-1 p-3 md:p-4">
          <div>
            {isLoading ? (
              <div className="flex min-h-96 items-center justify-center text-sm text-muted-foreground">Loading calendar…</div>
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
                        <div className="space-y-1">{visibleEvents.filter((event) => overlapsDay(event, day)).slice(0, 4).map(renderEvent)}</div>
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
                    {weekDays.map((day) => <div key={day.toISOString()} className="min-h-80 space-y-1 border-b border-l p-2">{visibleEvents.filter((event) => overlapsDay(event, day)).map(renderEvent)}</div>)}
                  </div>
                </div>
              </div>
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
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-type">Type</label><NativeSelect id="calendar-event-type" value={form.eventType} onChange={(event) => setForm((current) => ({ ...current, eventType: event.target.value }))} disabled={!canEditEvent}>{EVENT_TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</NativeSelect></div>
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-owner">Owner</label><Input id="calendar-event-owner" value={ownerNames.get(editingEvent?.userId ?? currentUser.id) ?? currentUser.username} disabled /></div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-start">Starts</label><Input id="calendar-event-start" type="datetime-local" value={form.startsAt} onChange={(event) => setForm((current) => ({ ...current, startsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
              <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-end">Ends</label><Input id="calendar-event-end" type="datetime-local" value={form.endsAt} onChange={(event) => setForm((current) => ({ ...current, endsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
            </div>
            <div className="space-y-2"><label className="block text-sm font-semibold" htmlFor="calendar-event-description">Notes</label><Textarea id="calendar-event-description" value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} disabled={!canEditEvent} placeholder="Optional context" /></div>
            <div className="flex flex-wrap gap-4 text-sm"><label className="flex items-center gap-2"><input type="checkbox" checked={form.allDay} onChange={(event) => setForm((current) => ({ ...current, allDay: event.target.checked }))} disabled={!canEditEvent} />All day</label><label className="flex items-center gap-2"><input type="checkbox" checked={form.showAsBusy} onChange={(event) => setForm((current) => ({ ...current, showAsBusy: event.target.checked }))} disabled={!canEditEvent} />Show as busy</label></div>
            <div className="flex flex-wrap items-center gap-2">
              {editingEvent && canEditEvent ? <DeleteConfirmPopover trigger={<Button type="button" variant="destructive" className="mr-auto"><Trash2 />Delete</Button>} title="Delete calendar event?" description="This permanently removes the event from the calendar." onConfirm={handleDelete} /> : null}
              <Button type="button" variant="outline" onClick={() => setSheetOpen(false)}>Close</Button>
              {canEditEvent ? <Button type="submit" disabled={isSaving || !form.title.trim()}>{isSaving ? 'Saving…' : 'Save event'}</Button> : null}
            </div>
          </form>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
