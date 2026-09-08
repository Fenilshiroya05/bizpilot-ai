import { cva, type VariantProps } from 'class-variance-authority'
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react'
import { forwardRef, type HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export const alertVariants = cva('flex items-start gap-3 rounded-md border p-3 text-sm', {
  variants: {
    variant: {
      info: 'border-info/30 bg-info/5 text-info',
      success: 'border-success/30 bg-success/5 text-success',
      warning: 'border-warning/30 bg-warning/5 text-warning',
      destructive: 'border-destructive/30 bg-destructive/5 text-destructive',
    },
  },
  defaultVariants: {
    variant: 'info',
  },
})

const ICONS = {
  info: Info,
  success: CheckCircle2,
  warning: TriangleAlert,
  destructive: AlertCircle,
} as const

export interface AlertProps
  extends HTMLAttributes<HTMLDivElement>, VariantProps<typeof alertVariants> {}

export const Alert = forwardRef<HTMLDivElement, AlertProps>(
  ({ className, variant = 'info', children, ...props }, ref) => {
    const Icon = ICONS[variant ?? 'info']
    return (
      <div ref={ref} role="alert" className={cn(alertVariants({ variant }), className)} {...props}>
        <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <div className="text-foreground">{children}</div>
      </div>
    )
  },
)
Alert.displayName = 'Alert'
