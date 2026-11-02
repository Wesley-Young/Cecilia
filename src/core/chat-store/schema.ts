import type { SchemaDefinition } from '@fraqjs/plugin-kysely';
import type { Generated, Selectable } from 'kysely';

import type { ChatMessage, MessageStatus } from '#core/model/message.ts';
import type { Scene } from '#core/model/peer.ts';

export interface MessageTable {
  id: Generated<number>;
  scene: Scene;
  peer_uin: number;
  /** Null until the server assigns one. */
  seq: number | null;
  time: number;
  sender_uin: number;
  sender_name: string;
  /** JSON of `Segment[]`. */
  segments: string;
  status: MessageStatus;
  outgoing: number;
  /** Set on messages sent from this device, to match the server's echo. */
  client_seq: number | null;
  random: number | null;
}

declare module '@fraqjs/plugin-kysely' {
  interface FraqDatabase {
    cecilia_messages: MessageTable;
  }
}

export type MessageRow = Selectable<MessageTable>;

export function toChatMessage(row: MessageRow): ChatMessage {
  return {
    id: row.id,
    peer: { scene: row.scene, uin: row.peer_uin },
    seq: row.seq,
    time: row.time,
    sender: { uin: row.sender_uin, name: row.sender_name },
    segments: JSON.parse(row.segments),
    status: row.status,
    outgoing: row.outgoing === 1,
  };
}

export const messageSchema: SchemaDefinition = {
  name: 'cecilia/messages',
  migrations: {
    '001_create': {
      async up(db) {
        await db.schema
          .createTable('cecilia_messages')
          .addColumn('id', 'integer', (column) => column.primaryKey().autoIncrement())
          .addColumn('scene', 'text', (column) => column.notNull())
          .addColumn('peer_uin', 'integer', (column) => column.notNull())
          .addColumn('seq', 'integer')
          .addColumn('time', 'integer', (column) => column.notNull())
          .addColumn('sender_uin', 'integer', (column) => column.notNull())
          .addColumn('sender_name', 'text', (column) => column.notNull())
          .addColumn('segments', 'text', (column) => column.notNull())
          .addColumn('status', 'text', (column) => column.notNull())
          .addColumn('outgoing', 'integer', (column) => column.notNull())
          .addColumn('client_seq', 'integer')
          .addColumn('random', 'integer')
          .execute();
        // SQLite treats NULLs as distinct, so any number of unsent messages fit under the unique index.
        await db.schema
          .createIndex('cecilia_messages_seq')
          .on('cecilia_messages')
          .columns(['scene', 'peer_uin', 'seq'])
          .unique()
          .execute();
        await db.schema
          .createIndex('cecilia_messages_pending')
          .on('cecilia_messages')
          .columns(['scene', 'peer_uin', 'client_seq', 'random'])
          .execute();
      },
    },
  },
};
