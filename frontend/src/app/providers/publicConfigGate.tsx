import { createContext, useContext, type ReactNode } from 'react';
import { usePublicConfig } from '../../api/usePublicConfig';
import type { PublicConfig } from '../../api/publicConfig';
import { PortalApiError } from '../../api/envelope';

const PublicConfigContext = createContext<PublicConfig | null>(null);

export function usePublicConfigData(): PublicConfig {
  const config = useContext(PublicConfigContext);
  if (config === null) {
    throw new Error('必须在 PublicConfigGate 内使用运行时配置');
  }
  return config;
}

function freezeConfig(config: PublicConfig): PublicConfig {
  return Object.freeze({ ...config, apiBaseUrls: Object.freeze([...config.apiBaseUrls]) as PublicConfig['apiBaseUrls'] });
}

function requestIdOf(error: unknown): string | undefined {
  if (error instanceof PortalApiError) {
    return error.requestId;
  }
  return undefined;
}

export function PublicConfigGate({ children }: { children: ReactNode }) {
  const { data, isPending, isError, error, refetch, isFetching } = usePublicConfig();

  if (isPending) {
    return <p role="status">正在启动，读取运行时配置…</p>;
  }

  if (isError || !data) {
    const requestId = requestIdOf(error);
    return (
      <main>
        <h1>启动失败</h1>
        <p>运行时配置加载失败，请检查后端服务后重试。</p>
        {requestId ? <p>{`requestId：${requestId}`}</p> : null}
        <button type="button" onClick={() => void refetch()} disabled={isFetching}>
          重试
        </button>
      </main>
    );
  }

  return (
    <PublicConfigContext.Provider value={freezeConfig(data.data)}>
      {children}
    </PublicConfigContext.Provider>
  );
}
