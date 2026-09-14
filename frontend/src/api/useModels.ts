import { useQuery } from '@tanstack/react-query';
import { MODELS_QUERY_KEY, fetchModels } from './models';

export function useModels() {
  return useQuery({
    queryKey: MODELS_QUERY_KEY,
    queryFn: ({ signal }) => fetchModels(signal),
    staleTime: 30_000,
    gcTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
