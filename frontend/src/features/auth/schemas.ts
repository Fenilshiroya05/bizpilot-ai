import { z } from 'zod'

// Mirrors backend/src/main/java/com/bizpilot/security/dto/LoginRequest.java exactly.
export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
})

export type LoginFormValues = z.infer<typeof loginSchema>

// Mirrors backend/src/main/java/com/bizpilot/security/dto/RegisterRequest.java exactly,
// including the @Size(min=8,max=72) bound and the letter+digit @Pattern.
export const registerSchema = z.object({
  email: z.string().min(1, 'Email is required').max(255).email('Enter a valid email address'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .max(72, 'Password must be at most 72 characters')
    .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, 'Password must contain at least one letter and one digit'),
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  organizationName: z.string().min(1, 'Organization name is required').max(255),
})

export type RegisterFormValues = z.infer<typeof registerSchema>
