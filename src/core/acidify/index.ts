import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { Secrets } from '#core/secrets/service.ts';
import { AcidifyClient, type AcidifyOptions } from './service.ts';

export type { AcidifyOptions } from './service.ts';

/** Provides the ChatClient for a real account. */
export const AcidifyPlugin = definePlugin({
  name: 'acidify',
  inject: { secrets: Secrets },
  provides: [ChatClient],
  async apply(ctx, options: AcidifyOptions) {
    const session = await ctx.secrets.get('session');
    ctx.provide(ChatClient, new AcidifyClient(ctx, ctx.secrets, options, session));
  },
  start(ctx) {
    (ctx.resolve(ChatClient) as AcidifyClient).start();
  },
});
