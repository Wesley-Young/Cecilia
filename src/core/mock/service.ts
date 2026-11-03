import { ChatClient, type PinnedPeers, type SendResult } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import { AppError } from '#core/errors.ts';
import { type Friend, type Group, type GroupMember, memberName, type SelfInfo } from '#core/model/contact.ts';
import type { LoginState } from '#core/model/login.ts';
import type { RemoteMessage, Sender } from '#core/model/message.ts';
import { type Peer, peerKey } from '#core/model/peer.ts';
import { type OutgoingSegment, type Segment, toLocalSegments } from '#core/model/segment.ts';

const self: SelfInfo = { uin: 10000, nickname: 'Cecilia' };

const friends: Friend[] = [
  { uin: 10001, nickname: '星野', remark: '' },
  { uin: 10002, nickname: 'kobayashi', remark: '小林' },
  { uin: 10003, nickname: 'Aria', remark: '' },
];

const groups: Group[] = [
  { uin: 20001, name: 'Cecilia 开发组', memberCount: 4, capacity: 200 },
  { uin: 20002, name: '摸鱼小队', memberCount: 3, capacity: 500 },
];

const members: GroupMember[] = [
  { uin: 10001, nickname: '星野', card: '星野 | 前端' },
  { uin: 10002, nickname: 'kobayashi', card: '' },
  { uin: 10003, nickname: 'Aria', card: '' },
  { uin: 10004, nickname: 'Momo', card: '桃桃' },
];

const lines = [
  '今天的构建又挂了',
  '我先看看日志',
  '周末有人一起吃火锅吗',
  '这个 bug 我能复现，稍等',
  '已经推上去了，帮忙 review 一下',
  '哈哈哈哈',
  '收到',
  '明天再说吧',
  '有人知道签名服务怎么配置吗？',
  '可以，晚上八点见',
];

/** History that exists before the mock starts, per conversation. */
const initialHistory = 120;

const svgUrl = (svg: string) => `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;

function placeholderQrCode(): string {
  const cells: string[] = [];
  for (let y = 0; y < 21; y++) {
    for (let x = 0; x < 21; x++) {
      if ((x * 7 + y * 13 + x * y) % 3 === 0) cells.push(`<rect x="${x}" y="${y}" width="1" height="1"/>`);
    }
  }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 23 23"><rect x="-1" y="-1" width="23" height="23" fill="#fff"/><g fill="#111">${cells.join('')}</g></svg>`;
  return svgUrl(svg);
}

interface Picture {
  width: number;
  height: number;
  /** Animated: its dot spins. */
  sticker: boolean;
  hue: number;
}

/** A gradient picture; mock file ids are its JSON, so `getImageUrl` can draw it again. */
function placeholderImage({ width, height, sticker, hue }: Picture): string {
  const [x, y, r] = [width / 2, height / 3, Math.min(width, height) / 8];
  const spin = sticker
    ? `<animateTransform attributeName="transform" type="rotate" from="0 ${x} ${height / 2}" to="360 ${x} ${height / 2}" dur="2s" repeatCount="indefinite"/>`
    : '';
  return svgUrl(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><defs><linearGradient id="g" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue} 70% 65%)"/><stop offset="1" stop-color="hsl(${hue + 60} 70% 45%)"/></linearGradient></defs><rect width="100%" height="100%" fill="url(#g)"/><circle cx="${x}" cy="${y}" r="${r}" fill="#fff" fill-opacity=".8">${spin}</circle></svg>`,
  );
}

/** A landscape photo, a portrait one, or an animated sticker. */
function mockImage(at: number): Segment {
  const hue = (at * 47) % 360;
  const [width, height, sticker] = [
    [1200, 800, false],
    [900, 1600, false],
    [240, 240, true],
  ][at % 3] as [number, number, boolean];
  const picture: Picture = { width, height, sticker, hue };
  return { type: 'image', fileId: JSON.stringify(picture), width, height, summary: sticker ? '[动画表情]' : '[图片]' };
}

function mockMarketFace(at: number): Segment {
  return {
    type: 'market-face',
    url: placeholderImage({ width: 300, height: 300, sticker: true, hue: (at * 83) % 360 }),
    summary: '[好耶]',
  };
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** ChatClient with made-up contacts and replies, for developing the interface without an account. */
export class MockClient extends ChatClient {
  private state: LoginState = { kind: 'idle' };
  /** The newest seq per conversation. */
  private readonly latest = new Map<string, number>();
  /**
   * Where seqs start this run. It grows with the clock, so a restarted mock never reuses a seq an earlier run left
   * in the database (the store would take the new message for one it already has).
   */
  private readonly base = Math.floor(Date.now() / 1000) - 1_700_000_000;
  private readonly timers = new Set<NodeJS.Timeout>();
  private loggedInBefore = true;
  private readonly ctx: ChatContext;

  constructor(ctx: ChatContext) {
    super();
    this.ctx = ctx;
  }

  get loginState(): LoginState {
    return this.state;
  }

  async login() {
    if (this.state.kind === 'online' || this.state.kind === 'logging-in' || this.state.kind === 'qrcode') return;
    if (this.loggedInBefore) {
      this.setState({ kind: 'logging-in' });
      await delay(400);
    } else {
      this.setState({ kind: 'qrcode', image: placeholderQrCode(), state: 'waiting' });
      await delay(1500);
      this.setState({ kind: 'qrcode', image: placeholderQrCode(), state: 'scanned' });
      await delay(1000);
    }
    this.loggedInBefore = true;
    this.setState({ kind: 'online', self });
  }

  async logout() {
    this.loggedInBefore = false;
    this.setState({ kind: 'idle' });
  }

  async applySettings() {}

  async getFriends(): Promise<Friend[]> {
    return friends;
  }

  async getGroups(): Promise<Group[]> {
    return groups;
  }

  async getGroupMembers(): Promise<GroupMember[]> {
    await delay(300);
    return members;
  }

  async getPinned(): Promise<PinnedPeers> {
    return { friends: [], groups: [20001] };
  }

  async sendMessage(peer: Peer, segments: OutgoingSegment[], clientSeq: number, random: number): Promise<SendResult> {
    this.ensureOnline();
    await delay(250);
    const text = segments.map((segment) => (segment.type === 'text' ? segment.text : '')).join('');
    if (text.includes('失败')) throw new AppError('bad_gateway', '模拟的发送失败');
    const seq = this.nextSeq(peer);
    const time = Math.floor(Date.now() / 1000);
    // The server echoes messages sent from this account back to every device, this one included.
    this.ctx.emit('client/message', {
      peer,
      seq,
      time,
      sender: { uin: self.uin, name: self.nickname },
      segments: toLocalSegments(segments),
      clientSeq,
      random,
    });
    this.later(1200, () => this.receive(peer, [{ type: 'text', text: `收到：${text}` }]));
    return { seq, time };
  }

  async getHistory(peer: Peer, limit: number, startSeq?: number): Promise<RemoteMessage[]> {
    this.ensureOnline();
    await delay(150);
    const end = Math.min(startSeq ?? this.latestSeq(peer), this.latestSeq(peer));
    const messages: RemoteMessage[] = [];
    // Only `initialHistory` messages exist before this run's first seq.
    for (let seq = Math.max(this.base - initialHistory + 1, end - limit + 1); seq <= end; seq++) {
      // Every message is made up from its seq, so asking twice gives the same history.
      const sender = this.senderOf(peer, seq);
      const time = (at: number) => Math.floor(Date.now() / 1000) - (this.latestSeq(peer) - at + 1) * 300;
      const text = (at: number): Segment => ({ type: 'text', text: lines[at % lines.length] ?? '' });
      // A few messages are a picture, a market face, or a picture between two lines of text.
      const content = (at: number): Segment[] => {
        if (at % 10 === 1) return [mockImage(at)];
        if (at % 10 === 5) return [mockMarketFace(at)];
        if (at % 10 === 8) return [text(at), mockImage(at), text(at + 1)];
        return [text(at)];
      };
      const segments = content(seq);
      // Every third group message quotes the one before it, without the quoted sender's name.
      if (peer.scene === 'group' && seq % 3 === 0) {
        segments.unshift({
          type: 'reply',
          seq: seq - 1,
          senderUin: this.senderOf(peer, seq - 1).uin,
          senderName: null,
          time: time(seq - 1),
          segments: content(seq - 1),
        });
      }
      messages.push({
        peer,
        seq,
        time: time(seq),
        sender,
        segments,
        clientSeq: 0,
        random: 0,
      });
    }
    return messages;
  }

  async markRead() {}

  async getImageUrl(fileId: string): Promise<string> {
    this.ensureOnline();
    await delay(300);
    return placeholderImage(JSON.parse(fileId) as Picture);
  }

  /** Some group chatter now and then, so there is something to receive. */
  startChatter() {
    this.later(
      4000,
      () => {
        if (this.state.kind === 'online') {
          const group = groups[Math.floor(Math.random() * groups.length)];
          if (group) {
            const text = lines[Math.floor(Math.random() * lines.length)] ?? '';
            this.receive({ scene: 'group', uin: group.uin }, [{ type: 'text', text }]);
          }
        }
        this.startChatter();
      },
      20_000,
    );
  }

  private receive(peer: Peer, segments: Segment[]) {
    if (this.state.kind !== 'online') return;
    const seq = this.nextSeq(peer);
    this.ctx.emit('client/message', {
      peer,
      seq,
      time: Math.floor(Date.now() / 1000),
      sender: this.senderOf(peer, seq),
      segments,
      clientSeq: 0,
      random: 0,
    });
  }

  private senderOf(peer: Peer, seq: number): Sender {
    if (peer.scene === 'friend') {
      // Every fourth message is from this account.
      if (seq % 4 === 0) return { uin: self.uin, name: self.nickname };
      return { uin: peer.uin, name: '' };
    }
    const member = members[seq % members.length] ?? members[0];
    // Like acidify, every third message (one with a reply) comes without the sender's name.
    return { uin: member?.uin ?? 0, name: member && seq % 3 !== 0 ? memberName(member) : '' };
  }

  private latestSeq(peer: Peer): number {
    return this.latest.get(peerKey(peer)) ?? this.base;
  }

  private nextSeq(peer: Peer): number {
    const seq = this.latestSeq(peer) + 1;
    this.latest.set(peerKey(peer), seq);
    return seq;
  }

  private ensureOnline() {
    if (this.state.kind !== 'online') throw new AppError('precondition_failed', '尚未登录');
  }

  private later(ms: number, callback: () => void, jitter = 0) {
    const timer = setTimeout(
      () => {
        this.timers.delete(timer);
        callback();
      },
      ms + Math.random() * jitter,
    );
    this.timers.add(timer);
  }

  private setState(state: LoginState) {
    this.state = state;
    this.ctx.emit('client/login', state);
  }

  dispose() {
    for (const timer of this.timers) clearTimeout(timer);
    this.timers.clear();
  }
}
