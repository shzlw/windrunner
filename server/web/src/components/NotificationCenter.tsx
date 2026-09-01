import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Bell, CheckCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import type { TFunction } from 'i18next'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type AuthUser,
  type UserNotification,
} from '@/lib/api'

function relativeTime(value: string, t: TFunction) {
  const seconds = Math.max(1, Math.round((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 60) return t('notifications.secondsAgo', { count: seconds })
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes })
  const hours = Math.round(minutes / 60)
  if (hours < 24) return t('notifications.hoursAgo', { count: hours })
  return t('notifications.daysAgo', { count: Math.round(hours / 24) })
}

type NotificationContextValue = {
  notifications: UserNotification[]
  unreadCount: number
  markRead: (notification: UserNotification) => Promise<void>
  markAllRead: () => Promise<void>
}

const NotificationContext = createContext<NotificationContextValue | null>(null)

export function NotificationProvider({ currentUser, children }: { currentUser: AuthUser | null; children: React.ReactNode }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const navigateRef = useRef(navigate)
  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])
  const [notifications, setNotifications] = useState<UserNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const seenIds = useRef(new Set<string>())
  const userId = currentUser?.id ?? null

  useEffect(() => {
    if (!userId) return

    let cancelled = false
    seenIds.current = new Set()
    getNotifications({ unread: true, limit: 50 })
      .then((page) => {
        if (cancelled) return
        page.items.forEach((notification) => seenIds.current.add(notification.id))
        setNotifications((current) => {
          const currentById = new Map(current.map((item) => [item.id, item]))
          const merged = [...page.items, ...current.filter((item) => !page.items.some((loaded) => loaded.id === item.id))]
          return merged.map((item) => currentById.get(item.id) ?? item).slice(0, 50)
        })
        setUnreadCount((count) => Math.max(count, page.unreadCount))
      })
      .catch(() => undefined)

    const source = new EventSource('/internal-api/v1/notifications/stream')
    const onNotification = (event: Event) => {
      const messageEvent = event as MessageEvent<string>
      let notification: UserNotification
      try {
        notification = JSON.parse(messageEvent.data) as UserNotification
      } catch {
        return
      }
      if (seenIds.current.has(notification.id)) return
      seenIds.current.add(notification.id)
      setNotifications((current) => [notification, ...current.filter((item) => item.id !== notification.id)].slice(0, 50))
      setUnreadCount((count) => count + 1)
      toast.info(notification.title, {
        description: notification.message,
        action: notification.projectId
          ? {
              label: t('notifications.open'),
              onClick: () => void navigateRef.current(`/app/projects/${notification.projectId}`),
            }
          : undefined,
      })
    }
    source.addEventListener('notification', onNotification)

    return () => {
      cancelled = true
      source.removeEventListener('notification', onNotification)
      source.close()
    }
  }, [userId])

  async function markRead(notification: UserNotification) {
    if (!notification.read) {
      await markNotificationRead(notification.id)
      setNotifications((current) => current.map((item) => item.id === notification.id ? { ...item, read: true } : item))
      setUnreadCount((count) => Math.max(0, count - 1))
    }
    if (notification.projectId) {
      navigateRef.current(`/app/projects/${notification.projectId}`)
    }
  }

  async function markAllRead() {
    await markAllNotificationsRead()
    setNotifications((current) => current.map((notification) => ({ ...notification, read: true })))
    setUnreadCount(0)
  }

  return <NotificationContext.Provider value={{ notifications, unreadCount, markRead, markAllRead }}>{children}</NotificationContext.Provider>
}

function useNotifications() {
  const value = useContext(NotificationContext)
  if (!value) throw new Error('NotificationCenter must be rendered inside NotificationProvider')
  return value
}

export default function NotificationCenter({ inSidebar = false }: { inSidebar?: boolean }) {
  const { t } = useTranslation()
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications()
  const label = unreadCount ? t('notifications.unread', { count: unreadCount }) : t('notifications.title')

  return (
    <Popover>
      <PopoverTrigger>
        <Button
          variant="ghost"
          size={inSidebar ? 'default' : 'icon'}
          className={inSidebar ? 'relative w-full justify-start gap-2 px-2 group-data-[collapsible=icon]:justify-center' : 'relative'}
          aria-label={label}
        >
          <Bell className="size-4" />
          <span className={inSidebar ? 'group-data-[collapsible=icon]:hidden' : 'sr-only'}>{t('notifications.title')}</span>
          {unreadCount > 0 ? (
            <Badge className={inSidebar ? 'ml-auto group-data-[collapsible=icon]:absolute group-data-[collapsible=icon]:-right-1 group-data-[collapsible=icon]:-top-1 group-data-[collapsible=icon]:ml-0 h-4 min-w-4 px-1 text-[10px] leading-none' : 'absolute -right-1 -top-1 h-4 min-w-4 px-1 text-[10px] leading-none'}>{unreadCount > 99 ? '99+' : unreadCount}</Badge>
          ) : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" side={inSidebar ? 'right' : 'bottom'} className="w-96 gap-0 p-0">
        <PopoverHeader className="flex-row items-center justify-between border-b px-4 py-3">
          <PopoverTitle>{t('notifications.title')}</PopoverTitle>
          {unreadCount > 0 ? (
            <Button variant="ghost" size="sm" className="h-7 gap-1.5 px-2 text-xs" onClick={() => void markAllRead()}>
              <CheckCheck className="size-3.5" />
              {t('notifications.markAllRead')}
            </Button>
          ) : null}
        </PopoverHeader>
        <div className="max-h-96 overflow-y-auto">
          {notifications.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">{t('notifications.caughtUp')}</p>
          ) : notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              className="block w-full border-b px-4 py-3 text-left transition-colors hover:bg-muted/50"
              onClick={() => void markRead(notification)}
            >
              <div className="flex items-start gap-2">
                {!notification.read ? <span className="mt-1.5 size-2 shrink-0 rounded-full bg-sky-600" /> : <span className="size-2 shrink-0" />}
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{notification.title}</p>
                  <p className="mt-0.5 text-sm text-muted-foreground">{notification.message}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{relativeTime(notification.createdAt, t)}</p>
                </div>
              </div>
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
