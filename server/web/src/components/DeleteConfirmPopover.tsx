import type { ReactElement } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from '@/components/ui/popover'

type DeleteConfirmPopoverProps = {
  trigger: ReactElement
  title?: string
  description?: string
  confirmLabel?: string
  disabled?: boolean
  onConfirm: () => void | Promise<void>
}

export default function DeleteConfirmPopover({
  trigger,
  title = 'Confirm delete',
  description,
  confirmLabel = 'Delete',
  disabled = false,
  onConfirm,
}: DeleteConfirmPopoverProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  function handleConfirm() {
    setOpen(false)
    void onConfirm()
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger render={trigger} />
      <PopoverContent align="end" side="top" className="w-80">
        <PopoverHeader>
          <PopoverTitle>{title}</PopoverTitle>
          {description ? <PopoverDescription>{description}</PopoverDescription> : null}
        </PopoverHeader>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" size="sm" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="destructive" size="sm" disabled={disabled} onClick={handleConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
