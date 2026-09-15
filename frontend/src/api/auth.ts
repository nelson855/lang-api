import { z } from 'zod';
import { portalRequest } from './portalClient';

export const authOptionsSchema = z.object({
  registrationEnabled: z.boolean(),
  registrationDisabledReason: z.enum(['ADMIN_DISABLED', 'PREVIEW_MODE', 'LEGAL_UNAVAILABLE']).nullable(),
  emailVerificationEnabled: z.literal(false),
  captchaEnabled: z.literal(false),
}).strict();

export const csrfSchema = z.object({ token: z.string().min(1) }).strict();

export const authProfileSchema = z.object({
  id: z.number().int().positive(),
  username: z.string().min(1),
  displayName: z.string().nullable(),
  email: z.string().email().nullable(),
}).strict();

export type AuthOptions = z.output<typeof authOptionsSchema>;
export type AuthProfile = z.output<typeof authProfileSchema>;
export type LoginInput = { username: string; password: string };
export type RegisterInput = LoginInput & { confirmPassword: string };

export const AUTH_OPTIONS_PATH = '/portal/api/auth/options';
export const AUTH_CSRF_PATH = '/portal/api/auth/csrf';
export const AUTH_PROFILE_PATH = '/portal/api/profile';
export const AUTH_PROFILE_QUERY_KEY = ['portal', 'auth', 'profile'] as const;

export function fetchAuthOptions(signal?: AbortSignal) {
  return portalRequest(AUTH_OPTIONS_PATH, authOptionsSchema, signal ? { signal } : undefined);
}

export function fetchProfile(signal?: AbortSignal) {
  return portalRequest(AUTH_PROFILE_PATH, authProfileSchema, signal ? { signal } : undefined);
}

export function fetchCsrf(signal?: AbortSignal) {
  return portalRequest(AUTH_CSRF_PATH, csrfSchema, signal ? { signal } : undefined);
}

export async function register(input: RegisterInput) {
  return mutation('/portal/api/auth/register', input, z.null());
}

export async function login(input: LoginInput) {
  return mutation('/portal/api/auth/login', input, authProfileSchema);
}

export async function refresh() {
  return mutation('/portal/api/auth/refresh', undefined, authProfileSchema);
}

export async function logout() {
  return mutation('/portal/api/auth/logout', undefined, z.null());
}

async function mutation<T>(path: string, body: unknown, schema: z.ZodType<T>): Promise<{ data: T; requestId: string }> {
  const csrf = await fetchCsrf();
  return portalRequest(path, schema, {
    method: 'POST',
    body,
    headers: { 'X-XSRF-TOKEN': csrf.data.token },
  });
}
