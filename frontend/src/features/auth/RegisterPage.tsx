import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import { register as registerUser } from '@/features/auth/api'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { registerSchema, type RegisterFormValues } from '@/features/auth/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'

export function RegisterPage() {
  const navigate = useNavigate()
  const [showPassword, setShowPassword] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register: registerField,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  // Registration only creates the account (backend returns UserResponse, no
  // tokens) — the user still has to log in explicitly afterward.
  async function onSubmit(values: RegisterFormValues) {
    setApiError(null)
    try {
      await registerUser(values)
      toast({
        title: 'Account created',
        description: 'Sign in with your new credentials.',
        variant: 'success',
      })
      navigate('/auth/login', { replace: true })
    } catch (error) {
      setApiError(
        error instanceof ApiError
          ? error.message
          : 'Unable to create your account. Please try again.',
      )
    }
  }

  return (
    <AuthLayout
      title="Create your workspace"
      description="Set up BizPilot AI for your business."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/auth/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {apiError && <Alert variant="destructive">{apiError}</Alert>}

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="firstName">First name</Label>
            <Input
              id="firstName"
              autoComplete="given-name"
              aria-invalid={!!errors.firstName}
              {...registerField('firstName')}
            />
            {errors.firstName && (
              <p className="text-sm text-destructive">{errors.firstName.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="lastName">Last name</Label>
            <Input
              id="lastName"
              autoComplete="family-name"
              aria-invalid={!!errors.lastName}
              {...registerField('lastName')}
            />
            {errors.lastName && (
              <p className="text-sm text-destructive">{errors.lastName.message}</p>
            )}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="organizationName">Organization name</Label>
          <Input
            id="organizationName"
            autoComplete="organization"
            aria-invalid={!!errors.organizationName}
            {...registerField('organizationName')}
          />
          {errors.organizationName && (
            <p className="text-sm text-destructive">{errors.organizationName.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={!!errors.email}
            {...registerField('email')}
          />
          {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              aria-invalid={!!errors.password}
              className="pr-10"
              {...registerField('password')}
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-muted-foreground hover:text-foreground"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Eye className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          </div>
          {errors.password ? (
            <p className="text-sm text-destructive">{errors.password.message}</p>
          ) : (
            <p className="text-xs text-muted-foreground">
              At least 8 characters, with a letter and a digit.
            </p>
          )}
        </div>

        <Button type="submit" className="w-full" isLoading={isSubmitting}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}
