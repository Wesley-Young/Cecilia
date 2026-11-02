import { KyselyService } from '@fraqjs/plugin-kysely';
import { sql } from 'kysely';

import { definePlugin } from '#core/context.ts';

/**
 * Connection settings plugin-kysely has no options for. Runs in `apply`, so before kysely migrates any schema in its
 * `start`. Install plugin-kysely with `autoVacuum: { enabled: false }`: a full VACUUM rewrites the whole file and
 * blocks the event loop the protocol heartbeat runs on.
 */
export const DatabasePlugin = definePlugin({
  name: 'database',
  inject: { kysely: KyselyService },
  async apply(ctx) {
    await sql`PRAGMA journal_mode = WAL`.execute(ctx.kysely.db);
    await sql`PRAGMA synchronous = NORMAL`.execute(ctx.kysely.db);
    await sql`PRAGMA foreign_keys = ON`.execute(ctx.kysely.db);
  },
});
