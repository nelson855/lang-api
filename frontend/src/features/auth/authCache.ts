import type { QueryClient } from '@tanstack/react-query';
import { AUTH_PROFILE_QUERY_KEY, type AuthProfile } from '../../api/auth';

export type AuthProfileResponse = { data: AuthProfile; requestId: string };

export function cacheAuthenticatedProfile(client: QueryClient, response: AuthProfileResponse) {
  client.setQueryData(AUTH_PROFILE_QUERY_KEY, response);
}

export function clearAuthenticatedScope(client: QueryClient) {
  client.removeQueries({ queryKey: AUTH_PROFILE_QUERY_KEY });
}
