import { useAuth } from '@/app/providers/AuthProvider'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/layout/PageHeader'

function Field({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      {value ? (
        <p className="text-sm text-foreground">{value}</p>
      ) : (
        <Skeleton className="h-4 w-32" />
      )}
    </div>
  )
}

export function SettingsPage() {
  const { user, organization, isBootstrapping } = useAuth()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Settings" description="Your profile and organization details." />

      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" value={user ? `${user.firstName} ${user.lastName}` : undefined} />
          <Field label="Email" value={user?.email} />
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Roles
            </p>
            {isBootstrapping ? (
              <Skeleton className="h-5 w-24" />
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {user?.roles.map((role) => (
                  <Badge key={role} variant="primary">
                    {role}
                  </Badge>
                ))}
              </div>
            )}
          </div>
          <Field label="Status" value={user?.status} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Organization</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" value={organization?.name} />
        </CardContent>
      </Card>

      <p className="text-sm text-muted-foreground">
        Team, role, and organization management aren't available yet — the backend doesn't expose
        those APIs today. This page will grow to include them in a later phase.
      </p>
    </div>
  )
}
