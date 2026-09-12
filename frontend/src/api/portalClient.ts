import type { z } from 'zod';
import {
  InvalidPortalResponseError,
  PortalApiError,
  PortalCancelledError,
  PortalNetworkError,
  parsePortalFailure,
  parsePortalSuccess,
} from './envelope';
import { validatePortalApiPath } from './portalPath';

export interface PortalRequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
}

function newRequestId(): string {
  const cryptoRef = globalThis.crypto as Crypto | undefined;
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return cryptoRef.randomUUID();
  }
  return `req-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
}

function headerRequestId(response: Response): string | undefined {
  const value = response.headers.get('x-request-id')?.trim();
  if (!value) {
    return undefined;
  }
  if (!/^[A-Za-z0-9_:.=-]{1,128}$/.test(value)) {
    return undefined;
  }
  return value;
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError')
  );
}

export async function portalRequest<T>(
  path: string,
  dataSchema: z.ZodType<T>,
  options: PortalRequestOptions = {},
): Promise<{ data: T; requestId: string }> {
  validatePortalApiPath(path);
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Request-Id': newRequestId(),
  };
  let bodyInit: string | undefined;
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    bodyInit = JSON.stringify(options.body);
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      credentials: 'same-origin',
      headers,
      body: bodyInit,
      signal: options.signal,
    });
  } catch (error) {
    if (isAbortError(error) || options.signal?.aborted) {
      throw new PortalCancelledError();
    }
    throw new PortalNetworkError();
  }
  if (options.signal?.aborted) {
    throw new PortalCancelledError();
  }
  const contentType = response.headers.get('content-type') ?? '';
  let payload: unknown;
  try {
    if (!contentType.includes('application/json') && !contentType.includes('+json')) {
      const text = await response.text();
      try {
        payload = JSON.parse(text);
      } catch {
        throw new InvalidPortalResponseError();
      }
    } else {
      payload = (await response.json()) as unknown;
    }
  } catch (error) {
    if (error instanceof InvalidPortalResponseError) {
      throw error;
    }
    throw new InvalidPortalResponseError();
  }
  if (response.ok) {
    try {
      return parsePortalSuccess(payload, dataSchema);
    } catch {
      throw new InvalidPortalResponseError();
    }
  }
  try {
    throw parsePortalFailure(payload, response.status);
  } catch (error) {
    if (error instanceof PortalApiError) {
      if (!error.requestId) {
        const fallback = headerRequestId(response);
        if (fallback) {
          throw new PortalApiError(error.status, error.code, error.message, fallback);
        }
      }
      throw error;
    }
    throw new InvalidPortalResponseError();
  }
}
