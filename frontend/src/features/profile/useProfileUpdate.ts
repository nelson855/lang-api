import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { USAGE_QUERY_KEY } from '../../api/usage';
import { WALLET_QUERY_KEY, updateProfile, type ProfileUpdateInput } from '../../api/wallet';

export const PROFILE_UPDATE_MUTATION_KEY = ['portal', 'auth', 'profileUpdate'] as const;

export function useProfileUpdateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: PROFILE_UPDATE_MUTATION_KEY,
    mutationFn: (input: ProfileUpdateInput) => updateProfile(input),
    retry: false,
    onSuccess: (result) => {
      // 成功响应原子替换唯一 profile 缓存，并使用户作用域数据失效重读。
      queryClient.setQueryData(AUTH_PROFILE_QUERY_KEY, result);
      const userId = result.data.id;
      queryClient.invalidateQueries({ queryKey: [...USAGE_QUERY_KEY, userId] });
      queryClient.invalidateQueries({ queryKey: [...WALLET_QUERY_KEY, userId] });
      queryClient.invalidateQueries({ queryKey: [...API_REQUEST_LOGS_QUERY_KEY, userId] });
    },
    onSettled: () => {
      // 密码只留在提交瞬间内存：结算后立即清除本次 mutation 记录（含 variables）。
      const cache = queryClient.getMutationCache();
      cache
        .findAll({ mutationKey: PROFILE_UPDATE_MUTATION_KEY })
        .forEach((mutation) => cache.remove(mutation));
    },
  });
}
