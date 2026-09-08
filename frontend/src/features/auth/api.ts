import { apiFetch } from '@/lib/api-client'
import type {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  UserResponse,
} from '@/types/api'

export function login(payload: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: payload,
  })
}

export function register(payload: RegisterRequest): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: payload,
  })
}

export function logout(payload: RefreshTokenRequest): Promise<void> {
  return apiFetch<void>('/api/v1/auth/logout', {
    method: 'POST',
    body: payload,
  })
}

export function getMe(): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/v1/auth/me')
}
