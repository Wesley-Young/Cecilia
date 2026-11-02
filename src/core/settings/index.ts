import { KyselyService } from '@fraqjs/plugin-kysely';

import { definePlugin } from '#core/context.ts';
import { settingSchema } from './schema.ts';
import { SettingsStore } from './service.ts';

export const SettingsPlugin = definePlugin({
  name: 'settings',
  inject: { kysely: KyselyService },
  provides: [SettingsStore],
  apply(ctx) {
    ctx.kysely.schemas.register(settingSchema);
    ctx.provide(SettingsStore, new SettingsStore(ctx.kysely));
  },
});
