import {
  CheckSquare,
  FileText,
  FolderOpen,
  LayoutDashboard,
  Package,
  Receipt,
  Target,
  Users,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { Permission } from '@/lib/permissions'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** Omit for items every authenticated user should see regardless of role. */
  permission?: Permission
}

// The full target information architecture (CLAUDE.md §14/§29). Only
// /dashboard and /settings render a real feature in Phase 20 — every other
// link resolves to an honest "coming in a later phase" page rather than a
// 404, so the shell can be built once and feature pages fill in over
// Phases 21-24 without any further navigation changes.
export const PRIMARY_NAV: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard, permission: 'ANALYTICS_READ' },
  { label: 'Customers', to: '/customers', icon: Users, permission: 'CUSTOMER_READ' },
  { label: 'Leads', to: '/leads', icon: Target, permission: 'LEAD_READ' },
  { label: 'Products', to: '/products', icon: Package, permission: 'PRODUCT_READ' },
  { label: 'Quotations', to: '/quotations', icon: FileText, permission: 'QUOTATION_READ' },
  { label: 'Invoices', to: '/invoices', icon: Receipt, permission: 'INVOICE_READ' },
  { label: 'Tasks', to: '/tasks', icon: CheckSquare, permission: 'TASK_READ' },
  { label: 'Documents', to: '/documents', icon: FolderOpen, permission: 'DOCUMENT_READ' },
]
