import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/AuthProvider'
import { RequireAuth, RequirePermission } from '@/app/guards'
import { AppShell } from '@/components/layout/AppShell'
import { ComingSoon } from '@/components/feedback/ComingSoon'
import { Skeleton } from '@/components/ui/skeleton'

const LoginPage = lazy(() =>
  import('@/features/auth/LoginPage').then((m) => ({ default: m.LoginPage })),
)
const RegisterPage = lazy(() =>
  import('@/features/auth/RegisterPage').then((m) => ({ default: m.RegisterPage })),
)
const DashboardPage = lazy(() =>
  import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const SettingsPage = lazy(() =>
  import('@/features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
)
const CustomersListPage = lazy(() =>
  import('@/features/customers/CustomersListPage').then((m) => ({ default: m.CustomersListPage })),
)
const CustomerDetailPage = lazy(() =>
  import('@/features/customers/CustomerDetailPage').then((m) => ({ default: m.CustomerDetailPage })),
)
const LeadsListPage = lazy(() =>
  import('@/features/leads/LeadsListPage').then((m) => ({ default: m.LeadsListPage })),
)
const LeadDetailPage = lazy(() =>
  import('@/features/leads/LeadDetailPage').then((m) => ({ default: m.LeadDetailPage })),
)
const ProductsListPage = lazy(() =>
  import('@/features/products/ProductsListPage').then((m) => ({ default: m.ProductsListPage })),
)
const ProductDetailPage = lazy(() =>
  import('@/features/products/ProductDetailPage').then((m) => ({ default: m.ProductDetailPage })),
)
const QuotationsListPage = lazy(() =>
  import('@/features/quotations/QuotationsListPage').then((m) => ({ default: m.QuotationsListPage })),
)
const QuotationDetailPage = lazy(() =>
  import('@/features/quotations/QuotationDetailPage').then((m) => ({ default: m.QuotationDetailPage })),
)
const QuotationFormPage = lazy(() =>
  import('@/features/quotations/QuotationFormPage').then((m) => ({ default: m.QuotationFormPage })),
)
const TasksListPage = lazy(() =>
  import('@/features/tasks/TasksListPage').then((m) => ({ default: m.TasksListPage })),
)
const TaskDetailPage = lazy(() =>
  import('@/features/tasks/TaskDetailPage').then((m) => ({ default: m.TaskDetailPage })),
)

function PageFallback() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-40 w-full" />
    </div>
  )
}

function withSuspense(element: React.ReactNode) {
  return <Suspense fallback={<PageFallback />}>{element}</Suspense>
}

function RootRedirect() {
  const { isAuthenticated } = useAuth()
  return <Navigate to={isAuthenticated ? '/dashboard' : '/auth/login'} replace />
}

export const router = createBrowserRouter([
  { path: '/', element: <RootRedirect /> },
  { path: '/auth/login', element: withSuspense(<LoginPage />) },
  { path: '/auth/register', element: withSuspense(<RegisterPage />) },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/dashboard', element: withSuspense(<DashboardPage />) },
          {
            path: '/customers',
            element: (
              <RequirePermission permission="CUSTOMER_READ">
                {withSuspense(<CustomersListPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/customers/:id',
            element: (
              <RequirePermission permission="CUSTOMER_READ">
                {withSuspense(<CustomerDetailPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/leads',
            element: (
              <RequirePermission permission="LEAD_READ">
                {withSuspense(<LeadsListPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/leads/:id',
            element: (
              <RequirePermission permission="LEAD_READ">
                {withSuspense(<LeadDetailPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/products',
            element: (
              <RequirePermission permission="PRODUCT_READ">
                {withSuspense(<ProductsListPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/products/:id',
            element: (
              <RequirePermission permission="PRODUCT_READ">
                {withSuspense(<ProductDetailPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/quotations',
            element: (
              <RequirePermission permission="QUOTATION_READ">
                {withSuspense(<QuotationsListPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/quotations/new',
            element: (
              <RequirePermission permission="QUOTATION_CREATE">
                {withSuspense(<QuotationFormPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/quotations/:id',
            element: (
              <RequirePermission permission="QUOTATION_READ">
                {withSuspense(<QuotationDetailPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/invoices',
            element: (
              <RequirePermission permission="INVOICE_READ">
                <ComingSoon title="Invoices" phase="Phase 23" />
              </RequirePermission>
            ),
          },
          {
            path: '/tasks',
            element: (
              <RequirePermission permission="TASK_READ">
                {withSuspense(<TasksListPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/tasks/:id',
            element: (
              <RequirePermission permission="TASK_READ">
                {withSuspense(<TaskDetailPage />)}
              </RequirePermission>
            ),
          },
          {
            path: '/documents',
            element: (
              <RequirePermission permission="DOCUMENT_READ">
                <ComingSoon title="Documents" phase="Phase 24" />
              </RequirePermission>
            ),
          },
          { path: '/settings', element: withSuspense(<SettingsPage />) },
        ],
      },
    ],
  },
  { path: '*', element: <RootRedirect /> },
])
