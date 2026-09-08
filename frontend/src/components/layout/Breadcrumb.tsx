import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export interface BreadcrumbItem {
  label: string
  to?: string
}

export function Breadcrumb({ items }: { items: BreadcrumbItem[] }) {
  if (items.length === 0) return null

  return (
    <nav aria-label="Breadcrumb">
      <ol className="flex items-center gap-1.5 text-sm text-muted-foreground">
        {items.map((item, index) => {
          const isLast = index === items.length - 1
          return (
            <li key={`${item.label}-${index}`} className="flex items-center gap-1.5">
              {index > 0 && <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />}
              {item.to && !isLast ? (
                <Link to={item.to} className="transition-colors hover:text-foreground">
                  {item.label}
                </Link>
              ) : (
                <span
                  aria-current={isLast ? 'page' : undefined}
                  className={isLast ? 'text-foreground' : ''}
                >
                  {item.label}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
