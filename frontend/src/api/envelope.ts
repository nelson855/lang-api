import { z } from 'zod';

export class PortalApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;

  constructor(status: number, code: string, message: string, requestId?: string) {
    super(message);
    this.name = 'PortalApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

export class InvalidPortalResponseError extends Error {
  constructor() {
    super('服务端响应无法解析');
    this.name = 'InvalidPortalResponseError';
  }
}

export class PortalNetworkError extends Error {
  constructor() {
    super('网络请求失败');
    this.name = 'PortalNetworkError';
  }
}

export class PortalCancelledError extends Error {
  constructor() {
    super('请求已取消');
    this.name = 'PortalCancelledError';
  }
}

const requestIdSchema = z.string().min(1);
const failureSchema = z.object({
  requestId: requestIdSchema.optional(),
  error: z.object({
    code: z.string().min(1),
    message: z.string().min(1),
  }),
});

export function parsePortalSuccess<T>(payload: unknown, dataSchema: z.ZodType<T>) {
  const wrapper = z
    .object({
      requestId: requestIdSchema,
      data: z.unknown(),
    })
    .safeParse(payload);
  if (!wrapper.success) {
    throw new InvalidPortalResponseError();
  }
  const data = dataSchema.safeParse(wrapper.data.data);
  if (!data.success) {
    throw new InvalidPortalResponseError();
  }
  return { data: data.data, requestId: wrapper.data.requestId };
}

export function parsePortalFailure(payload: unknown, status: number): PortalApiError {
  const parsed = failureSchema.safeParse(payload);
  if (!parsed.success) {
    throw new InvalidPortalResponseError();
  }
  return new PortalApiError(
    status,
    parsed.data.error.code,
    parsed.data.error.message,
    parsed.data.requestId,
  );
}
