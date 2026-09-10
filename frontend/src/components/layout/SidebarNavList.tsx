import { Bot, Settings } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { PRIMARY_NAV } from '@/app/nav-config'
import { useAuth } from '@/app/providers/AuthProvider'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

const linkClasses = (isActive: boolean, collapsed: boolean) =>
  cn(
    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
    collapsed && 'justify-center px-2',
    isActive
      ? 'bg-primary/10 text-primary'
      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
  )

export function SidebarNavList({
  collapsed = false,
  onNavigate,
  onOpenAi,
}: {
  collapsed?: boolean
  onNavigate?: () => void
  onOpenAi: () => void
}) {
  const { permissions } = useAuth()

  return (
    <nav className="flex flex-1 flex-col justify-between" aria-label="Primary">
      <ul className="flex flex-col gap-1">
        {PRIMARY_NAV.filter((item) => !item.permission || permissions.has(item.permission)).map(
          (item) => (
            <li key={item.to}>
              <NavItemLink
                to={item.to}
                label={item.label}
                Icon={item.icon}
                collapsed={collapsed}
                onNavigate={onNavigate}
              />
            </li>
          ),
        )}
      </ul>

      <ul className="flex flex-col gap-1 border-t border-border pt-2">
        {permissions.has('AI_USE') && (
          <li>
            <button
              type="button"
              onClick={onOpenAi}
              className={linkClasses(false, collapsed)}
              aria-label="Open AI Assistant"
            >
              <Bot className="h-4 w-4 shrink-0" aria-hidden="true" />
              {!collapsed && <span>AI Assistant</span>}
            </button>
          </li>
        )}
        <li>
          <NavItemLink
            to="/settings"
            label="Settings"
            Icon={Settings}
            collapsed={collapsed}
            onNavigate={onNavigate}
          />
        </li>
      </ul>
    </nav>
  )
}

function NavItemLink({
  to,
  label,
  Icon,
  collapsed,
  onNavigate,
}: {
  to: string
  label: string
  Icon: typeof Settings
  collapsed: boolean
  onNavigate?: () => void
}) {
  const link = (
    <NavLink
      to={to}
      onClick={onNavigate}
      aria-label={collapsed ? label : undefined}
      className={({ isActive }) => linkClasses(isActive, collapsed)}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      {!collapsed && <span>{label}</span>}
    </NavLink>
  )

  if (!collapsed) return link

  return (
    <Tooltip delayDuration={200}>
      <TooltipTrigger asChild>{link}</TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  )
}
