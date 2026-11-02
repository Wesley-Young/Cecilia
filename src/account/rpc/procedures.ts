import type { Logger } from '@fraqjs/kernel';
import { initTRPC, type TRPC_ERROR_CODE_KEY, TRPCError } from '@trpc/server';
import { z } from 'zod';

import { AppError, type AppErrorKind } from '#core/errors.ts';

export interface RpcContext {
  logger: Logger;
}

const t = initTRPC.context<RpcContext>().create({
  isServer: true,
  errorFormatter({ shape, error }) {
    const validation = error.cause instanceof z.ZodError ? error.cause : undefined;
    return {
      ...shape,
      message:
        error.code === 'INTERNAL_SERVER_ERROR'
          ? '操作失败，请稍后重试'
          : (validation?.issues[0]?.message ?? shape.message),
      data: { ...shape.data, stack: undefined },
    };
  },
});

const errorCodes = {
  bad_request: 'BAD_REQUEST',
  not_found: 'NOT_FOUND',
  conflict: 'CONFLICT',
  precondition_failed: 'PRECONDITION_FAILED',
  bad_gateway: 'BAD_GATEWAY',
} as const satisfies Record<AppErrorKind, TRPC_ERROR_CODE_KEY>;

export const router = t.router;

/** Base of every procedure: maps AppError to the matching tRPC error; anything else stays an internal error. */
export const publicProcedure = t.procedure.use(async ({ next }) => {
  const result = await next();
  if (!result.ok && result.error.cause instanceof AppError) {
    const { cause } = result.error;
    throw new TRPCError({ code: errorCodes[cause.kind], message: cause.message, cause });
  }
  return result;
});

export const peerInput = z.object({
  scene: z.enum(['friend', 'group']),
  uin: z.number().int().positive(),
});
