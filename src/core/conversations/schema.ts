import type { SchemaDefinition } from '@fraqjs/plugin-kysely';
import type { Selectable } from 'kysely';

import type { Conversation } from '#core/model/contact.ts';
import type { Scene } from '#core/model/peer.ts';

export interface ConversationTable {
  scene: Scene;
  peer_uin: number;
  name: string;
  summary: string;
  time: number;
  unread: number;
  pinned: number;
  /** The newest seq seen; unread counts only messages past it, so a message is never counted twice. */
  last_seq: number;
  /** Time of the message at `last_seq`, needed to mark friend messages as read. */
  last_seq_time: number;
}

declare module '@fraqjs/plugin-kysely' {
  interface FraqDatabase {
    cecilia_conversations: ConversationTable;
  }
}

export type ConversationRow = Selectable<ConversationTable>;

export function toConversation(row: ConversationRow): Conversation {
  return {
    peer: { scene: row.scene, uin: row.peer_uin },
    name: row.name,
    summary: row.summary,
    time: row.time,
    unread: row.unread,
    pinned: row.pinned === 1,
  };
}

export const conversationSchema: SchemaDefinition = {
  name: 'cecilia/conversations',
  migrations: {
    '001_create': {
      async up(db) {
        await db.schema
          .createTable('cecilia_conversations')
          .addColumn('scene', 'text', (column) => column.notNull())
          .addColumn('peer_uin', 'integer', (column) => column.notNull())
          .addColumn('name', 'text', (column) => column.notNull())
          .addColumn('summary', 'text', (column) => column.notNull())
          .addColumn('time', 'integer', (column) => column.notNull())
          .addColumn('unread', 'integer', (column) => column.notNull())
          .addColumn('pinned', 'integer', (column) => column.notNull())
          .addColumn('last_seq', 'integer', (column) => column.notNull())
          .addColumn('last_seq_time', 'integer', (column) => column.notNull())
          .addPrimaryKeyConstraint('cecilia_conversations_pk', ['scene', 'peer_uin'])
          .execute();
      },
    },
  },
};
