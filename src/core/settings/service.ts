import { serviceToken } from '@fraqjs/kernel';
import type { KyselyService } from '@fraqjs/plugin-kysely';

import { defaultAppInfo } from '#core/acidify/app-info.ts';
import type { AccountSettings } from '#core/model/settings.ts';
// Declares the table on FraqDatabase; without it, programs that reach this file only through types lose it.
import type {} from './schema.ts';

const defaults: AccountSettings = { uin: null, signUrl: '', signToken: '', appInfo: defaultAppInfo };

/**
 * The account settings, one row per field. Read only after plugin-kysely has migrated, that is from `start` on or
 * later; `apply` is too early.
 */
export class SettingsStore {
  static readonly token = serviceToken<SettingsStore>('cecilia/SettingsStore');
  private readonly kysely: KyselyService;

  constructor(kysely: KyselyService) {
    this.kysely = kysely;
  }

  async load(): Promise<AccountSettings> {
    const rows = await this.kysely.db.selectFrom('cecilia_settings').selectAll().execute();
    const stored: Record<string, unknown> = Object.fromEntries(rows.map((row) => [row.key, JSON.parse(row.value)]));
    // A missing or mistyped value falls back to its default rather than break login.
    const text = (key: 'signUrl' | 'signToken' | 'appInfo') =>
      typeof stored[key] === 'string' ? stored[key] : defaults[key];
    return {
      uin: typeof stored.uin === 'number' ? stored.uin : defaults.uin,
      signUrl: text('signUrl'),
      signToken: text('signToken'),
      appInfo: text('appInfo'),
    };
  }

  async save(settings: AccountSettings) {
    await this.kysely.db.transaction().execute(async (trx) => {
      for (const [key, value] of Object.entries(settings)) {
        await trx
          .insertInto('cecilia_settings')
          .values({ key, value: JSON.stringify(value) })
          .onConflict((conflict) => conflict.column('key').doUpdateSet({ value: JSON.stringify(value) }))
          .execute();
      }
    });
  }
}
