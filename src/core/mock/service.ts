import { ChatClient, type PinnedPeers, type SendResult } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import { AppError } from '#core/errors.ts';
import type { Friend, Group, SelfInfo } from '#core/model/contact.ts';
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

function placeholderQrCode(): string {
  const cells: string[] = [];
  for (let y = 0; y < 21; y++) {
    for (let x = 0; x < 21; x++) {
      if ((x * 7 + y * 13 + x * y) % 3 === 0) cells.push(`<rect x="${x}" y="${y}" width="1" height="1"/>`);
    }
  }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 23 23"><rect x="-1" y="-1" width="23" height="23" fill="#fff"/><g fill="#111">${cells.join('')}</g></svg>`;
  return `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;
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

  async getFriends(): Promise<Friend[]> {
    return friends;
  }

  async getGroups(): Promise<Group[]> {
    return groups;
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
      messages.push({
        peer,
        seq,
        time: Math.floor(Date.now() / 1000) - (this.latestSeq(peer) - seq + 1) * 300,
        sender,
        segments: [{ type: 'text', text: lines[seq % lines.length] ?? '' }],
        clientSeq: 0,
        random: 0,
      });
    }
    return messages;
  }

  async markRead() {}

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
    const member = friends[seq % friends.length] ?? friends[0];
    return { uin: member?.uin ?? 0, name: member ? member.remark || member.nickname : '' };
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
