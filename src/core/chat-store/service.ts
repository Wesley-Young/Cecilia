import { serviceToken } from '@fraqjs/kernel';
import type { KyselyService } from '@fraqjs/plugin-kysely';
import { sql } from 'kysely';

import type { ChatClient } from '#core/client/service.ts';
import type { ContactService } from '#core/contacts/service.ts';
import type { ChatContext } from '#core/context.ts';
import { AppError } from '#core/errors.ts';
import type { ChatMessage, RemoteMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import { type OutgoingSegment, toLocalSegments } from '#core/model/segment.ts';
import { type MessageRow, toChatMessage } from './schema.ts';

import { randomInt } from 'node:crypto';

/** The server returns at most this many messages per history request. */
export const historyPageSize = 30;

export interface HistoryPage {
  /** Oldest first. */
  messages: ChatMessage[];
  /** Whether older messages may exist. */
  hasMore: boolean;
}

/** Orders unsent messages, which have no seq yet, after every sent one. */
const newestFirst = sql<number>`coalesce(seq, ${Number.MAX_SAFE_INTEGER})`;

/**
 * The local message database. Messages from the server and messages sent from here end up as one row each: a sent
 * message is saved before it goes out, and the server's echo is matched to it by `clientSeq` and `random`.
 */
export class ChatStore {
  static readonly token = serviceToken<ChatStore>('cecilia/ChatStore');
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

  /**
   * Saves a message from the server. Returns the row when it is new or changed; `live` messages are also announced,
   * while history the caller asked for is not.
   */
  async receive(message: RemoteMessage, live: boolean): Promise<ChatMessage | undefined> {
    const { peer } = message;
    const where = this.db
      .selectFrom('cecilia_messages')
      .selectAll()
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin);
    if (await where.where('seq', '=', message.seq).executeTakeFirst()) return undefined;

    const self = this.client.self;
    const outgoing = message.sender.uin === self?.uin;
    let row: MessageRow | undefined;
    if (outgoing && message.random !== 0) {
      const pending = await where
        .where('seq', 'is', null)
        .where('client_seq', '=', message.clientSeq)
        .where('random', '=', message.random)
        .executeTakeFirst();
      if (pending) {
        row = await this.db
          .updateTable('cecilia_messages')
          .set({ seq: message.seq, time: message.time, segments: JSON.stringify(message.segments), status: 'sent' })
          .where('id', '=', pending.id)
          .returningAll()
          .executeTakeFirst();
      }
    }
    row ??= await this.db
      .insertInto('cecilia_messages')
      .values({
        scene: peer.scene,
        peer_uin: peer.uin,
        seq: message.seq,
        time: message.time,
        sender_uin: message.sender.uin,
        sender_name:
          message.sender.name ||
          (outgoing ? self?.nickname : this.contacts.nameOf({ scene: 'friend', uin: message.sender.uin })) ||
          String(message.sender.uin),
        segments: JSON.stringify(message.segments),
        status: 'sent',
        outgoing: outgoing ? 1 : 0,
        client_seq: message.clientSeq,
        random: message.random,
      })
      .onConflict((conflict) => conflict.columns(['scene', 'peer_uin', 'seq']).doNothing())
      .returningAll()
      .executeTakeFirst();
    if (!row) return undefined;
    const saved = toChatMessage(row);
    if (live) this.ctx.emit('store/message', saved);
    return saved;
  }

  /** Saves the message as sending and returns it right away; the outcome follows as a `store/message` event. */
  async send(peer: Peer, segments: OutgoingSegment[]): Promise<ChatMessage> {
    const self = this.client.self;
    if (!self) throw new AppError('precondition_failed', '尚未登录');
    const clientSeq = randomInt(1, 2 ** 31 - 1);
    const random = randomInt(1, 2 ** 31 - 1);
    const row = await this.db
      .insertInto('cecilia_messages')
      .values({
        scene: peer.scene,
        peer_uin: peer.uin,
        seq: null,
        time: Math.floor(Date.now() / 1000),
        sender_uin: self.uin,
        sender_name: self.nickname,
        segments: JSON.stringify(toLocalSegments(segments)),
        status: 'sending',
        outgoing: 1,
        client_seq: clientSeq,
        random,
      })
      .returningAll()
      .executeTakeFirstOrThrow();
    const message = toChatMessage(row);
    this.ctx.emit('store/message', message);
    void this.deliver(message.id, peer, segments, clientSeq, random);
    return message;
  }

  private async deliver(id: number, peer: Peer, segments: OutgoingSegment[], clientSeq: number, random: number) {
    let row: MessageRow | undefined;
    try {
      const result = await this.client.sendMessage(peer, segments, clientSeq, random);
      row = await this.db
        .updateTable('cecilia_messages')
        .set({ seq: result.seq, time: result.time, status: 'sent' })
        .where('id', '=', id)
        .returningAll()
        .executeTakeFirst();
    } catch (error) {
      this.ctx.logger.warn(`Cannot send to ${peer.scene} ${peer.uin}`, error);
      row = await this.db
        .updateTable('cecilia_messages')
        .set({ status: 'failed' })
        .where('id', '=', id)
        .where('seq', 'is', null)
        .returningAll()
        .executeTakeFirst();
    }
    if (row) this.ctx.emit('store/message', toChatMessage(row));
  }

  async recall(peer: Peer, seq: number) {
    const row = await this.db
      .updateTable('cecilia_messages')
      .set({ status: 'recalled' })
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin)
      .where('seq', '=', seq)
      .returningAll()
      .executeTakeFirst();
    if (row) this.ctx.emit('store/message', toChatMessage(row));
  }

  /**
   * A page of history before `before` (the newest when omitted). Local rows come first; the server is asked when
   * online and the newest page is requested (to catch up on what arrived while offline), when the local page is
   * short, or when a group's page has a hole in its seqs.
   */
  async history(peer: Peer, before?: number): Promise<HistoryPage> {
    let local = await this.localPage(peer, before);
    let remoteCount: number | undefined;
    if (this.client.loginState.kind === 'online' && (before === undefined || this.incomplete(peer, local, before))) {
      try {
        const remote = await this.client.getHistory(
          peer,
          historyPageSize,
          before === undefined ? undefined : before - 1,
        );
        remoteCount = remote.length;
        for (const message of remote) await this.receive(message, false);
        local = await this.localPage(peer, before);
      } catch (error) {
        this.ctx.logger.warn(`Cannot fetch history of ${peer.scene} ${peer.uin}`, error);
      }
    }
    const oldest = local.find((message) => message.seq !== null)?.seq;
    // Without an answer from the server, only a full local page suggests there is more.
    const more = remoteCount === undefined ? local.length === historyPageSize : remoteCount > 0;
    return { messages: local, hasMore: more && oldest != null && oldest > 1 };
  }

  private incomplete(peer: Peer, page: ChatMessage[], before: number): boolean {
    if (page.length < historyPageSize) return true;
    if (peer.scene !== 'group') return false;
    // Group seqs have no gaps, so a full page must cover a contiguous range ending right before `before`.
    const oldest = page.find((message) => message.seq !== null)?.seq ?? before;
    return before - oldest !== page.length;
  }

  private async localPage(peer: Peer, before?: number): Promise<ChatMessage[]> {
    let query = this.db
      .selectFrom('cecilia_messages')
      .selectAll()
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin);
    if (before !== undefined) query = query.where('seq', '<', before);
    const rows = await query.orderBy(newestFirst, 'desc').orderBy('id', 'desc').limit(historyPageSize).execute();
    return rows.reverse().map(toChatMessage);
  }
}
