import { useQuery } from '@tanstack/react-query';
import { PUBLIC_CONFIG_QUERY_KEY, fetchPublicConfig } from './publicConfig';

export function usePublicConfig() {
  return useQuery({
    queryKey: PUBLIC_CONFIG_QUERY_KEY,
    queryFn: ({ signal }) => fetchPublicConfig(signal),
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
