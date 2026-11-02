import type { SchemaDefinition } from '@fraqjs/plugin-kysely';

export interface SettingTable {
  key: string;
  /** JSON of the value. */
  value: string;
}

declare module '@fraqjs/plugin-kysely' {
  interface FraqDatabase {
    cecilia_settings: SettingTable;
  }
}

export const settingSchema: SchemaDefinition = {
  name: 'cecilia/settings',
  migrations: {
    '001_create': {
      async up(db) {
        await db.schema
          .createTable('cecilia_settings')
          .addColumn('key', 'text', (column) => column.primaryKey())
          .addColumn('value', 'text', (column) => column.notNull())
          .execute();
      },
    },
  },
};
