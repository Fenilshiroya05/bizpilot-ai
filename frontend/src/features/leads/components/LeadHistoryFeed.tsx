import { FileText, PlusCircle, RefreshCw, Trash2, UserCog } from 'lucide-react'

import { useAuth } from '@/app/providers/AuthProvider'
import { formatDateTime } from '@/lib/date'
import type { LeadActivityResponse } from '@/types/api'

const ACTIVITY_ICONS: Record<LeadActivityResponse['type'], typeof FileText> = {
  CREATED: PlusCircle,
  STATUS_CHANGED: RefreshCw,
  ASSIGNED: UserCog,
  ARCHIVED: Trash2,
  NOTE: FileText,
}

export function LeadHistoryFeed({ activities }: { activities: LeadActivityResponse[] }) {
  const { user } = useAuth()

  if (activities.length === 0) {
    return <p className="py-6 text-center text-sm text-muted-foreground">No activity yet.</p>
  }

  return (
    <ol className="flex flex-col gap-4">
      {activities.map((activity) => {
        const Icon = ACTIVITY_ICONS[activity.type]
        // No user-directory endpoint exists — only the current user's own
        // actions are ever attributed by name; every other author is left
        // unattributed rather than showing a fabricated or opaque id.
        const attributedToMe = user && activity.createdByUserId === user.id
        return (
          <li key={activity.id} className="flex gap-3">
            <Icon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <div className="flex-1 space-y-0.5">
              <p className="text-sm text-foreground">{activity.content}</p>
              <p className="text-xs text-muted-foreground">
                {attributedToMe ? 'You · ' : ''}
                {formatDateTime(activity.createdAt)}
              </p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
