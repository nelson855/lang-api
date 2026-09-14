import type { QueryClient } from '@tanstack/react-query';
import { AUTH_PROFILE_QUERY_KEY, type AuthProfile } from '../../api/auth';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { USAGE_QUERY_KEY } from '../../api/usage';

export type AuthProfileResponse = { data: AuthProfile; requestId: string };

export function cacheAuthenticatedProfile(client: QueryClient, response: AuthProfileResponse) {
  client.setQueryData(AUTH_PROFILE_QUERY_KEY, response);
}

export function clearAuthenticatedScope(client: QueryClient) {
  client.removeQueries({ queryKey: AUTH_PROFILE_QUERY_KEY });
  client.removeQueries({ queryKey: USAGE_QUERY_KEY });
  client.removeQueries({ queryKey: API_REQUEST_LOGS_QUERY_KEY });
}
