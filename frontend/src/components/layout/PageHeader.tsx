import type { ReactNode } from 'react'

import type { BreadcrumbItem } from '@/components/layout/Breadcrumb'
import { Breadcrumb } from '@/components/layout/Breadcrumb'

export function PageHeader({
  title,
  description,
  breadcrumb,
  actions,
}: {
  title: string
  description?: string
  breadcrumb?: BreadcrumbItem[]
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="space-y-1">
        {breadcrumb && breadcrumb.length > 0 && <Breadcrumb items={breadcrumb} />}
        <h1 className="text-2xl font-semibold text-foreground">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}
