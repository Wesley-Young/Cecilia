import { serviceToken } from '@fraqjs/kernel';
import type { KyselyService } from '@fraqjs/plugin-kysely';
import { sql, type Updateable } from 'kysely';

import type { ChatClient } from '#core/client/service.ts';
import type { ContactService } from '#core/contacts/service.ts';
import type { ChatContext } from '#core/context.ts';
import { AppError } from '#core/errors.ts';
import type { ChatMessage, RemoteMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import {
  type ImageFormat,
  type OutgoingSegment,
  type Segment,
  sniffImageFormat,
  toLocalSegments,
} from '#core/model/segment.ts';
import { type MessageRow, type MessageTable, toChatMessage } from './schema.ts';

import { createHash, randomInt } from 'node:crypto';
import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { basename, extname, join } from 'node:path';

/** The server returns at most this many messages per history request. */
export const historyPageSize = 30;

export interface HistoryPage {
  /** Oldest first. */
  messages: ChatMessage[];
  /** Whether older messages may exist. */
  hasMore: boolean;
}

/** Starts the file id of an image sent from here, which is a file in the store's media directory. */
const localImage = 'local:';

const mimeTypes = {
  png: 'image/png',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  bmp: 'image/bmp',
  tiff: 'image/tiff',
} satisfies Record<ImageFormat, string>;

/** Orders unsent messages, which have no seq yet, after every sent one. */
const newestFirst = sql<number>`coalesce(seq, ${Number.MAX_SAFE_INTEGER})`;

/**
 * The local message database. Messages from the server and messages sent from here end up as one row each: a sent
 * message is saved before it goes out, and the server's echo is matched to it by `random` (and `clientSeq`, which
 * group echoes leave at 0).
 */
export class ChatStore {
  static readonly token = serviceToken<ChatStore>('cecilia/ChatStore');
  private readonly ctx: ChatContext;
  private readonly kysely: KyselyService;
  private readonly client: ChatClient;
  private readonly contacts: ContactService;

  /** Holds the images of messages sent from here until the server has them. */
  private readonly mediaDir: string;

  constructor(ctx: ChatContext, kysely: KyselyService, client: ChatClient, contacts: ContactService, mediaDir: string) {
    this.ctx = ctx;
    this.kysely = kysely;
    this.client = client;
    this.contacts = contacts;
    this.mediaDir = mediaDir;
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
      let pending = where.where('seq', 'is', null).where('random', '=', message.random);
      if (message.clientSeq !== 0) pending = pending.where('client_seq', '=', message.clientSeq);
      const match = await pending.executeTakeFirst();
      if (match) {
        row = await this.db
          .updateTable('cecilia_messages')
          .set({ seq: message.seq, time: message.time, segments: JSON.stringify(message.segments), status: 'sent' })
          .where('id', '=', match.id)
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
          (outgoing ? message.sender.name || self?.nickname : await this.senderName(peer, message.sender.uin)) ||
          message.sender.name ||
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
    if (live) {
      const [named] = await this.withSenderNames(peer, [saved]);
      this.ctx.emit('store/message', named ?? saved);
    }
    return saved;
  }

  /**
   * The sender's current name in this conversation. In groups it comes from the member list (when the group's has
   * been loaded) rather than the message, which acidify leaves without a name when it has a reply.
   */
  private async senderName(peer: Peer, uin: number): Promise<string | undefined> {
    // Recalled messages fetched from the server come from uin 0; looking it up would refetch the member list.
    if (uin === 0) return undefined;
    if (peer.scene === 'group') return this.contacts.memberName(peer.uin, uin);
    return this.contacts.nameOf({ scene: 'friend', uin });
  }

  /**
   * Shows group members under their current names, with the name saved with a message as the fallback, and names
   * the sender of each quoted message: by the current name, then the name the server sent with the reply, the name
   * saved with the quoted message, and the uin.
   */
  private async withSenderNames(peer: Peer, messages: ChatMessage[]): Promise<ChatMessage[]> {
    const names = new Map<number, string | undefined>();
    // One at a time, so a missing member refetches the member list once.
    const nameOf = async (uin: number) => {
      if (!names.has(uin)) names.set(uin, await this.senderName(peer, uin));
      return names.get(uin);
    };
    const quoted = messages.flatMap((message) =>
      message.segments.flatMap((segment) => (segment.type === 'reply' ? [segment.seq] : [])),
    );
    const saved = await this.savedSenderNames(peer, quoted);
    const self = this.client.self;
    const named: ChatMessage[] = [];
    for (const message of messages) {
      let { sender, segments } = message;
      if (peer.scene === 'group') {
        const name = await nameOf(sender.uin);
        if (name) sender = { ...sender, name };
      }
      if (segments.some((segment) => segment.type === 'reply')) {
        segments = [];
        for (const segment of message.segments) {
          if (segment.type !== 'reply') {
            segments.push(segment);
            continue;
          }
          const senderName =
            (await nameOf(segment.senderUin)) ||
            segment.senderName ||
            saved.get(segment.seq) ||
            (segment.senderUin === self?.uin ? self.nickname : '') ||
            String(segment.senderUin);
          segments.push({ ...segment, senderName });
        }
      }
      named.push({ ...message, sender, segments });
    }
    return named;
  }

  /** The names saved with the messages of these seqs, by seq. */
  private async savedSenderNames(peer: Peer, seqs: number[]): Promise<Map<number, string>> {
    if (seqs.length === 0) return new Map();
    const rows = await this.db
      .selectFrom('cecilia_messages')
      .select(['seq', 'sender_name'])
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin)
      .where('seq', 'in', seqs)
      .execute();
    return new Map(rows.flatMap((row) => (row.seq === null ? [] : [[row.seq, row.sender_name] as const])));
  }

  /** Saves the message as sending and returns it right away; the outcome follows as a `store/message` event. */
  async send(peer: Peer, segments: OutgoingSegment[]): Promise<ChatMessage> {
    const self = this.client.self;
    if (!self) throw new AppError('precondition_failed', '尚未登录');
    const clientSeq = randomInt(1, 2 ** 31 - 1);
    const random = randomInt(1, 2 ** 31 - 1);
    // In a group, the message goes out under the account's group card when the member list has it.
    const name = (peer.scene === 'group' && (await this.senderName(peer, self.uin))) || self.nickname;
    const reply = segments.find((segment) => segment.type === 'reply');
    const quote = reply && (await this.localQuote(peer, reply.seq));
    const local: Segment[] = [];
    for (const segment of segments) {
      if (segment.type === 'image') local.push(await this.saveImage(segment));
      else local.push(...toLocalSegments([segment]));
    }
    const row = await this.db
      .insertInto('cecilia_messages')
      .values({
        scene: peer.scene,
        peer_uin: peer.uin,
        seq: null,
        time: Math.floor(Date.now() / 1000),
        sender_uin: self.uin,
        sender_name: name,
        segments: JSON.stringify(quote ? [quote, ...local] : local),
        status: 'sending',
        outgoing: 1,
        client_seq: clientSeq,
        random,
      })
      .returningAll()
      .executeTakeFirstOrThrow();
    const message = toChatMessage(row);
    this.ctx.emit('store/message', message);
    this.deliver(message.id, peer, segments, clientSeq, random).catch((error: unknown) =>
      this.ctx.logger.error(`Cannot save the message sent to ${peer.scene} ${peer.uin}`, error),
    );
    return message;
  }

  /** Keeps an image being sent, so the message can show it before the server has it. */
  private async saveImage(segment: Extract<OutgoingSegment, { type: 'image' }>): Promise<Segment> {
    // Named by content: the same picture sent twice is one file.
    const name = `${createHash('sha256').update(segment.data).digest('hex')}.${segment.format}`;
    await mkdir(this.mediaDir, { recursive: true });
    await writeFile(join(this.mediaDir, name), segment.data);
    const { width, height } = segment;
    return { type: 'image', fileId: `${localImage}${name}`, width, height, summary: '[图片]' };
  }

  /** Where to load an image from: its saved file when it was sent from here, the server otherwise. */
  async imageUrl(fileId: string): Promise<string> {
    if (!fileId.startsWith(localImage)) return this.client.getImageUrl(fileId);
    const name = fileId.slice(localImage.length);
    const format = extname(name).slice(1);
    if (basename(name) !== name || !(format in mimeTypes)) throw new AppError('bad_request', '无效的图片');
    let data: Buffer;
    try {
      data = await readFile(join(this.mediaDir, name));
    } catch {
      throw new AppError('not_found', '图片已不存在');
    }
    return `data:${mimeTypes[format as ImageFormat]};base64,${data.toString('base64')}`;
  }

  /**
   * The bytes of an image the window shows, by the address it shows it from (from `imageUrl`, or a market face's),
   * so it can be sent again. The type is read from the bytes, as the server's label is not always right.
   */
  async downloadImage(src: string): Promise<{ data: Uint8Array; type: string }> {
    let response: Response;
    try {
      response = await fetch(src);
    } catch {
      throw new AppError('bad_gateway', '图片下载失败');
    }
    if (!response.ok) throw new AppError('bad_gateway', '图片下载失败');
    const data = new Uint8Array(await response.arrayBuffer());
    const format = sniffImageFormat(data);
    return { data, type: format ? `image/${format}` : (response.headers.get('content-type') ?? '') };
  }

  /** Deletes the saved images that no unsent message shows any more; the server has the rest. */
  async pruneImages() {
    let names: string[];
    try {
      names = await readdir(this.mediaDir);
    } catch {
      return;
    }
    const rows = await this.db.selectFrom('cecilia_messages').select('segments').where('seq', 'is', null).execute();
    const kept = new Set(
      rows.flatMap((row) =>
        (JSON.parse(row.segments) as Segment[]).flatMap((segment) =>
          segment.type === 'image' && segment.fileId.startsWith(localImage)
            ? [segment.fileId.slice(localImage.length)]
            : [],
        ),
      ),
    );
    await Promise.all(names.filter((name) => !kept.has(name)).map((name) => rm(join(this.mediaDir, name))));
  }

  /** The reply segment for a message sent from here, built from the quoted message saved locally. */
  private async localQuote(peer: Peer, seq: number): Promise<Segment | undefined> {
    const row = await this.db
      .selectFrom('cecilia_messages')
      .selectAll()
      .where('scene', '=', peer.scene)
      .where('peer_uin', '=', peer.uin)
      .where('seq', '=', seq)
      .executeTakeFirst();
    if (!row) return undefined;
    const quoted = toChatMessage(row);
    return {
      type: 'reply',
      seq,
      senderUin: quoted.sender.uin,
      senderName: quoted.sender.name,
      time: quoted.time,
      // A quote shows what the message says, not what it quoted in turn.
      segments: quoted.segments.filter((segment) => segment.type !== 'reply'),
    };
  }

  private async deliver(id: number, peer: Peer, segments: OutgoingSegment[], clientSeq: number, random: number) {
    let outcome: Updateable<MessageTable>;
    try {
      const result = await this.client.sendMessage(peer, segments, clientSeq, random);
      outcome = { seq: result.seq, time: result.time, status: 'sent' };
    } catch (error) {
      this.ctx.logger.warn(`Cannot send to ${peer.scene} ${peer.uin}`, error);
      outcome = { status: 'failed' };
    }
    // Nothing to do when the echo has already filled the row in.
    const row = await this.db
      .updateTable('cecilia_messages')
      .set(outcome)
      .where('id', '=', id)
      .where('seq', 'is', null)
      .returningAll()
      .executeTakeFirst();
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

  /** Forgets every message, for when the account changes. */
  async clear() {
    await this.db.deleteFrom('cecilia_messages').execute();
    await rm(this.mediaDir, { recursive: true, force: true });
  }

  /**
   * A page of history before `before` (the newest when omitted). Local rows come first; the server is asked when
   * online and the newest page is requested (to catch up on what arrived while offline), when the local page is
   * short, or when a group's page has a hole in its seqs.
   */
  async history(peer: Peer, before?: number): Promise<HistoryPage> {
    const online = this.client.loginState.kind === 'online';
    // Fetched alongside the history; the names looked up below wait for it.
    if (online && peer.scene === 'group') void this.contacts.loadMembers(peer.uin);
    let local = await this.localPage(peer, before);
    let remoteCount: number | undefined;
    if (online && (before === undefined || this.incomplete(peer, local, before))) {
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
    return { messages: await this.withSenderNames(peer, local), hasMore: more && oldest != null && oldest > 1 };
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
