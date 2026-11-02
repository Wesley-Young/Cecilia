import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { Secrets } from '#core/secrets/service.ts';
import { SettingsStore } from '#core/settings/service.ts';
import { AcidifyClient } from './service.ts';

/** Provides the ChatClient for a real account, built from the account settings. */
export const AcidifyPlugin = definePlugin({
  name: 'acidify',
  inject: { secrets: Secrets, settings: SettingsStore },
  provides: [ChatClient],
  apply(ctx) {
    ctx.provide(ChatClient, new AcidifyClient(ctx, ctx.secrets));
  },
  // Settings can be read once plugin-kysely has migrated, which its own `start` does before this one runs.
  async start(ctx) {
    await (ctx.resolve(ChatClient) as AcidifyClient).applySettings(await ctx.settings.load());
  },
});
