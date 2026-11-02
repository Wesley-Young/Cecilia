import { serviceToken } from '@fraqjs/kernel';
import type { KyselyService } from '@fraqjs/plugin-kysely';

import type { ChatClient } from '#core/client/service.ts';
import type { ContactService } from '#core/contacts/service.ts';
import type { ChatContext } from '#core/context.ts';
import type { Conversation } from '#core/model/contact.ts';
import type { ChatMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import { summarize } from '#core/model/segment.ts';
import { type ConversationRow, toConversation } from './schema.ts';

function preview(message: ChatMessage): string {
  if (message.status === 'recalled') return `${message.outgoing ? '你' : message.sender.name}撤回了一条消息`;
  const text = summarize(message.segments);
  return message.peer.scene === 'group' && !message.outgoing ? `${message.sender.name}: ${text}` : text;
}

/** The conversation list: one row per peer, updated from the messages the store saves. */
export class ConversationService {
  static readonly token = serviceToken<ConversationService>('cecilia/ConversationService');
  private readonly ctx: ChatContext;
  private readonly kysely: KyselyService;
  private readonly client: ChatClient;
  private readonly contacts: ContactService;

  constructor(ctx: ChatContext, kysely: KyselyService, client: ChatClient, contacts: ContactService) {
    this.ctx = ctx;
    this.kysely = kysely;
    this.client = client;
    this.contacts = contacts;
  }

  private get db() {
    return this.kysely.db;
  }

  /** Pinned first, then by the time of the last message. */
  async list(): Promise<Conversation[]> {
    const rows = await this.db
      .selectFrom('cecilia_conversations')
      .selectAll()
      .orderBy('pinned', 'desc')
      .orderBy('time', 'desc')
      .execute();
    return rows.map(toConversation);
  }

  private find(peer: Peer) {
    return this.db
      .selectFrom('cecilia_conversations')
      .selectAll()
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin)
      .executeTakeFirst();
  }

  /** Takes in a message the store saved live: a new one, or one whose status changed. */
  async update(message: ChatMessage) {
    const { peer } = message;
    const existing = await this.find(peer);
    // Only the last message shows in the list; an older one, recalled or arriving late, changes nothing.
    if (existing && message.status === 'recalled' && message.seq !== existing.last_seq) return;
    if (existing && message.status !== 'recalled' && message.time < existing.time) return;
    const isNew = message.seq !== null && message.seq > (existing?.last_seq ?? 0);
    const counts = isNew && !message.outgoing && message.status === 'sent';
    const row: ConversationRow = {
      scene: peer.scene,
      peer_uin: peer.uin,
      name: this.contacts.nameOf(peer) ?? existing?.name ?? String(peer.uin),
      summary: preview(message),
      time: Math.max(message.time, existing?.time ?? 0),
      unread: (existing?.unread ?? 0) + (counts ? 1 : 0),
      pinned: existing?.pinned ?? 0,
      last_seq: isNew ? (message.seq ?? 0) : (existing?.last_seq ?? 0),
      last_seq_time: isNew ? message.time : (existing?.last_seq_time ?? 0),
    };
    await this.save(row);
  }

  async markRead(peer: Peer) {
    const existing = await this.find(peer);
    if (!existing || existing.unread === 0) return;
    await this.save({ ...existing, unread: 0 });
    if (this.client.loginState.kind === 'online' && existing.last_seq > 0) {
      try {
        await this.client.markRead(peer, existing.last_seq, existing.last_seq_time);
      } catch (error) {
        this.ctx.logger.warn(`Cannot mark ${peer.scene} ${peer.uin} as read on the server`, error);
      }
    }
  }

  /** Renames conversations after the contact list changed. */
  async rename() {
    const rows = await this.db.selectFrom('cecilia_conversations').selectAll().execute();
    for (const row of rows) {
      const name = this.contacts.nameOf({ scene: row.scene, uin: row.peer_uin });
      if (name && name !== row.name) await this.save({ ...row, name });
    }
  }

  /** Takes in which conversations are pinned on the server. */
  async syncPinned() {
    const pinned = await this.client.getPinned();
    const isPinned = (row: ConversationRow) =>
      (row.scene === 'friend' ? pinned.friends : pinned.groups).includes(row.peer_uin) ? 1 : 0;
    const rows = await this.db.selectFrom('cecilia_conversations').selectAll().execute();
    for (const row of rows) {
      if (row.pinned !== isPinned(row)) await this.save({ ...row, pinned: isPinned(row) });
    }
    // Pinned conversations show up even before they have a message here.
    const known = new Set(rows.map((row) => `${row.scene}:${row.peer_uin}`));
    const missing: Peer[] = [
      ...pinned.friends.map((uin): Peer => ({ scene: 'friend', uin })),
      ...pinned.groups.map((uin): Peer => ({ scene: 'group', uin })),
    ].filter((peer) => !known.has(`${peer.scene}:${peer.uin}`));
    for (const peer of missing) {
      await this.save({
        scene: peer.scene,
        peer_uin: peer.uin,
        name: this.contacts.nameOf(peer) ?? String(peer.uin),
        summary: '',
        time: 0,
        unread: 0,
        pinned: 1,
        last_seq: 0,
        last_seq_time: 0,
      });
    }
  }

  private async save(row: ConversationRow) {
    await this.db
      .insertInto('cecilia_conversations')
      .values(row)
      .onConflict((conflict) =>
        conflict.columns(['scene', 'peer_uin']).doUpdateSet({
          name: row.name,
          summary: row.summary,
          time: row.time,
          unread: row.unread,
          pinned: row.pinned,
          last_seq: row.last_seq,
          last_seq_time: row.last_seq_time,
        }),
      )
      .execute();
    this.ctx.emit('store/conversation', toConversation(row));
  }
}
