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
import { CalendarDays, ChevronLeft, ChevronRight, Clock3, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import DeleteConfirmPopover from '@/components/DeleteConfirmPopover'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
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
  const [scopeKey, setScopeKey] = useState('')
  const [currentDate, setCurrentDate] = useState(() => new Date())
  const [view, setView] = useState<CalendarView>('month')
  const [filter, setFilter] = useState<EventFilter>('ALL')
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState<CalendarEvent | null>(null)
  const [form, setForm] = useState<EventForm>(() => getDefaultForm(new Date()))

  useEffect(() => {
    if (!currentUser) return
    const defaultScope = `USER:${currentUser.id}`
    setScopeKey(defaultScope)
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

  const selectedScope = useMemo(() => {
    const separator = scopeKey.indexOf(':')
    if (separator < 0) return null
    return {
      type: scopeKey.slice(0, separator) as CalendarScopeType,
      id: scopeKey.slice(separator + 1),
    }
  }, [scopeKey])

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
    if (!currentUser || !selectedScope) return
    let cancelled = false
    setIsLoading(true)
    void listCalendarEvents(range.from, range.to, selectedScope.type, selectedScope.id)
      .then((nextEvents) => {
        if (!cancelled) setEvents(nextEvents)
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
  }, [currentUser?.id, range.from, range.to, selectedScope])

  const ownerNames = useMemo(() => {
    const names = new Map<string, string>()
    if (currentUser) names.set(currentUser.id, currentUser.displayName || currentUser.username)
    scopes.forEach((scope) => scope.members.forEach((member) => names.set(member.userId, member.displayName)))
    return names
  }, [currentUser, scopes])

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

  const selectedScopeLabel = useMemo(() => {
    if (!selectedScope || !currentUser) return 'My calendar · 1 person'
    if (selectedScope.type === 'USER') {
      return `${ownerNames.get(selectedScope.id) ?? 'Calendar'} · 1 person`
    }
    const team = scopes.find((scope) => scope.teamId === selectedScope.id)
    return team ? `${team.teamName} · ${team.members.length} people` : 'Team calendar'
  }, [currentUser, ownerNames, scopes, selectedScope])

  const canEditEvent = !editingEvent || editingEvent.userId === currentUser?.id || isAdminLike(currentUser)

  function openNewEvent(day = currentDate) {
    setEditingEvent(null)
    setForm(getDefaultForm(day))
    setDialogOpen(true)
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
    setDialogOpen(true)
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
      setDialogOpen(false)
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
      setDialogOpen(false)
      toast.success('Calendar event deleted')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to delete calendar event')
    }
  }

  function renderEvent(event: CalendarEvent) {
    return (
      <button
        key={event.id}
        type="button"
        className={cn('w-full overflow-hidden rounded border-l-4 px-2 py-1 text-left text-xs hover:brightness-95', eventAccent(event.eventType))}
        onClick={() => openEvent(event)}
        title={`${event.title} · ${ownerNames.get(event.userId) ?? 'Unknown user'}`}
      >
        <span className="block truncate font-medium">{event.allDay ? event.title : `${format(new Date(event.startsAt), 'h:mm a')} ${event.title}`}</span>
        {selectedScope?.type === 'TEAM' ? <span className="block truncate text-[10px] opacity-75">{ownerNames.get(event.userId) ?? 'Unknown user'}</span> : null}
      </button>
    )
  }

  if (!currentUser) return null

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-auto bg-muted/20">
      <div className="border-b bg-background px-5 py-4 sm:px-7">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <CalendarDays className="size-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Calendar</h1>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">Plan time, see team calendars, and spot assignment conflicts.</p>
          </div>
          <Button type="button" onClick={() => openNewEvent()}><Plus /> New event</Button>
        </div>
      </div>

      <div className="border-b bg-background px-5 py-3 sm:px-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setCurrentDate(new Date())}>Today</Button>
            <Button type="button" variant="outline" size="icon-sm" onClick={() => movePeriod(-1)} aria-label="Previous period"><ChevronLeft /></Button>
            <Button type="button" variant="outline" size="icon-sm" onClick={() => movePeriod(1)} aria-label="Next period"><ChevronRight /></Button>
            <span className="min-w-32 text-sm font-semibold">{format(currentDate, view === 'month' ? 'MMMM yyyy' : 'MMM d, yyyy')}</span>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs font-medium text-muted-foreground">Showing</span>
              <NativeSelect value={scopeKey} onChange={(event) => setScopeKey(event.target.value)} className="min-w-64">
                <option value={`USER:${currentUser.id}`}>My calendar</option>
                {scopes.map((scope) => (
                  <optgroup key={scope.teamId} label={scope.teamName}>
                    <option value={`TEAM:${scope.teamId}`}>{scope.teamName} · {scope.members.length} people</option>
                    {scope.members.filter((member) => member.userId !== currentUser.id).map((member) => (
                      <option key={`${scope.teamId}:${member.userId}`} value={`USER:${member.userId}`}>↳ {member.displayName}</option>
                    ))}
                  </optgroup>
                ))}
              </NativeSelect>
              <span className="hidden text-xs text-muted-foreground lg:inline">{selectedScopeLabel}</span>
            </div>
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

      <div className="border-b bg-background px-5 py-2 sm:px-7">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-xs font-medium text-muted-foreground">Filter</span>
          {EVENT_FILTERS.map((item) => (
            <Button key={item.value} type="button" size="sm" variant={filter === item.value ? 'secondary' : 'ghost'} onClick={() => setFilter(item.value)}>
              {item.label}
            </Button>
          ))}
          <span className="ml-auto text-xs text-muted-foreground">{visibleEvents.length} events · {visibleEvents.filter((event) => event.showAsBusy).length} busy</span>
        </div>
      </div>

      <main className="p-5 sm:p-7">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-3">
            <CardTitle className="text-sm">{selectedScopeLabel}</CardTitle>
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-emerald-500" />Scheduled work</span>
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-rose-500" />Time off</span>
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-violet-500" />Meetings</span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
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
                      <div key={day.toISOString()} className={cn('min-h-28 border-b border-l p-1.5', !isSameMonth(day, currentDate) && 'bg-muted/15')}>
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
                  <div className="grid min-h-96 grid-cols-7">
                    {weekDays.map((day) => <div key={day.toISOString()} className="space-y-1 border-b border-l p-2">{visibleEvents.filter((event) => overlapsDay(event, day)).map(renderEvent)}</div>)}
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
        <p className="mt-3 flex items-center gap-1 text-xs text-muted-foreground"><Clock3 className="size-3.5" />Busy events are used by assignment conflict checks.</p>
      </main>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingEvent ? (canEditEvent ? 'Edit calendar event' : 'Calendar event') : 'New calendar event'}</DialogTitle>
            <DialogDescription>{canEditEvent ? 'Create a schedule entry or record time that consumes capacity.' : `Read-only event for ${ownerNames.get(editingEvent?.userId ?? '') ?? 'another user'}.`}</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-title">Title</label><Input id="calendar-event-title" value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} disabled={!canEditEvent} required /></div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-type">Type</label><NativeSelect id="calendar-event-type" value={form.eventType} onChange={(event) => setForm((current) => ({ ...current, eventType: event.target.value }))} disabled={!canEditEvent}>{EVENT_TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</NativeSelect></div>
              <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-owner">Owner</label><Input id="calendar-event-owner" value={ownerNames.get(editingEvent?.userId ?? currentUser.id) ?? currentUser.username} disabled /></div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-start">Starts</label><Input id="calendar-event-start" type="datetime-local" value={form.startsAt} onChange={(event) => setForm((current) => ({ ...current, startsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
              <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-end">Ends</label><Input id="calendar-event-end" type="datetime-local" value={form.endsAt} onChange={(event) => setForm((current) => ({ ...current, endsAt: event.target.value }))} disabled={!canEditEvent} required /></div>
            </div>
            <div className="space-y-1.5"><label className="text-sm font-medium" htmlFor="calendar-event-description">Notes</label><Textarea id="calendar-event-description" value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} disabled={!canEditEvent} placeholder="Optional context" /></div>
            <div className="flex flex-wrap gap-4 text-sm"><label className="flex items-center gap-2"><input type="checkbox" checked={form.allDay} onChange={(event) => setForm((current) => ({ ...current, allDay: event.target.checked }))} disabled={!canEditEvent} />All day</label><label className="flex items-center gap-2"><input type="checkbox" checked={form.showAsBusy} onChange={(event) => setForm((current) => ({ ...current, showAsBusy: event.target.checked }))} disabled={!canEditEvent} />Show as busy</label></div>
            <DialogFooter>
              {editingEvent && canEditEvent ? <DeleteConfirmPopover trigger={<Button type="button" variant="destructive" className="mr-auto"><Trash2 />Delete</Button>} title="Delete calendar event?" description="This permanently removes the event from the calendar." onConfirm={handleDelete} /> : null}
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>Close</Button>
              {canEditEvent ? <Button type="submit" disabled={isSaving || !form.title.trim()}>{isSaving ? 'Saving…' : 'Save event'}</Button> : null}
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
