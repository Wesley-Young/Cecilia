import { z } from 'zod';

import type { AccountService } from '#core/account/service.ts';
import { publicProcedure } from '../procedures.ts';

const settingsInput = z.object({
  uin: z
    .number({ error: '请填写 QQ 号' })
    .int('QQ 号不正确')
    .min(10000, 'QQ 号不正确')
    .max(99_999_999_999, 'QQ 号不正确'),
  signUrl: z
    .string()
    .trim()
    .min(1, '请填写签名服务地址')
    .refine((value) => /^https?:\/\//.test(value) && URL.canParse(value), '签名服务地址应以 http:// 或 https:// 开头'),
  /** Omitted or empty to keep the saved token. */
  signToken: z
    .string()
    .trim()
    .optional()
    .transform((value) => value || undefined),
  appInfo: z.string().min(1, '请选择协议版本'),
});

export function settingRoutes(account: AccountService) {
  return {
    get: publicProcedure.query(() => account.view()),
    update: publicProcedure.input(settingsInput).mutation(({ input }) => account.update(input)),
  };
}
