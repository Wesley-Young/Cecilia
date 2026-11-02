import {
  AppInfo,
  Bot,
  type BotOutgoingMessageBuilder,
  CoroutineScope,
  LagrangeUrlSignProvider,
  LogHandler,
  LogLevel,
  SessionStore,
} from '@acidify/core';
import type { Logger } from '@fraqjs/kernel';

import { ChatClient, type PinnedPeers, type SendResult } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import { AppError } from '#core/errors.ts';
import type { Friend, Group } from '#core/model/contact.ts';
import type { LoginState } from '#core/model/login.ts';
import type { RemoteMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import type { OutgoingSegment } from '#core/model/segment.ts';
import type { Secrets } from '#core/secrets/service.ts';
import { toDataUrl, toFriend, toGroup, toPeer, toQrCodeState, toRemoteMessage, toUinList } from './mapper.ts';

export interface AcidifyOptions {
  /** The account to log in as. The sign service needs it before the QR code is scanned. */
  uin: number | undefined;
  /** Which bundled PC protocol to present as. */
  protocol: 'Linux' | 'Mac';
  /** A Lagrange V2 sign service; without it the account cannot log in. */
  sign?: { url: string; token: string } | undefined;
}

const sessionKey = 'session';
/** Delays between reconnect attempts after going offline; the last one repeats. */
const retryDelays = [5_000, 15_000, 60_000, 300_000];

function qua(appInfo: AppInfo): string {
  const os = appInfo.os === 'Mac' ? 'MAC' : appInfo.os === 'Windows' ? 'WIN' : 'LNX';
  return `V1_${os}_NQ_${appInfo.currentVersion.replace('-', '_')}_GW_B`;
}

function logHandler(logger: Logger): LogHandler {
  return {
    handleLog(level, tag, message, throwable) {
      const line = `[${tag}] ${message}`;
      switch (level.name) {
        case 'VERBOSE':
        case 'DEBUG':
          return logger.debug(line);
        case 'INFO':
          return logger.info(line);
        case 'WARN':
          return logger.warn(line, throwable ?? undefined);
        case 'ERROR':
          return logger.error(line, throwable ?? undefined);
      }
    },
    [LogHandler.Symbol]: true,
  };
}

const describe = (error: unknown) => (error instanceof Error ? error.message : String(error));

/** ChatClient backed by an acidify PC protocol bot. */
export class AcidifyClient extends ChatClient {
  private state: LoginState;
  private readonly scope = new CoroutineScope(true);
  private readonly bot: Bot | undefined;
  private hasSession: boolean;
  private loggingIn: Promise<void> | undefined;
  private retries = 0;
  private retryTimer: NodeJS.Timeout | undefined;
  private disposed = false;
  private readonly ctx: ChatContext;
  private readonly secrets: Secrets;
  private readonly options: AcidifyOptions;

  constructor(ctx: ChatContext, secrets: Secrets, options: AcidifyOptions, savedSession: string | undefined) {
    super();
    this.ctx = ctx;
    this.secrets = secrets;
    this.options = options;
    this.hasSession = savedSession !== undefined;
    const { uin, sign } = options;
    if (uin === undefined || !sign) {
      this.state = { kind: 'unconfigured', message: uin === undefined ? '未配置 QQ 号' : '未配置签名服务' };
      return;
    }
    this.state = { kind: 'idle' };

    const appInfo = AppInfo.Bundled[options.protocol];
    const sessionStore = savedSession ? SessionStore.fromJson(savedSession) : SessionStore.empty();
    const guid = Buffer.from(sessionStore.guid.buffer, sessionStore.guid.byteOffset, sessionStore.guid.byteLength);
    const signProvider = new LagrangeUrlSignProvider(
      this.scope,
      sign.url,
      sign.token,
      BigInt(uin),
      guid.toString('hex'),
      qua(appInfo),
    );
    const bot = Bot.create(appInfo, sessionStore, signProvider, this.scope, LogLevel.INFO, logHandler(ctx.logger));
    this.bot = bot;

    bot.onQRCodeGenerated((event) => this.setState({ kind: 'qrcode', image: toDataUrl(event.png), state: 'waiting' }));
    bot.onQRCodeStateQuery((event) => {
      const qrState = toQrCodeState(event.state);
      if (qrState && this.state.kind === 'qrcode' && this.state.state !== qrState) {
        this.setState({ ...this.state, state: qrState });
      }
    });
    bot.onSessionStoreUpdated((event) => {
      this.hasSession = true;
      this.secrets
        .set(sessionKey, event.sessionStore.toJson())
        .catch((error: unknown) => ctx.logger.error('Cannot save the session', error));
    });
    bot.onBotOffline((event) => {
      if (this.state.kind === 'online') this.scheduleRetry(event.reason);
    });
    bot.onMessageReceive((event) => {
      const message = toRemoteMessage(event.message);
      if (message) ctx.emit('client/message', message);
    });
    bot.onMessageRecall((event) => {
      const peer = toPeer(event.scene, event.peerUin);
      if (peer) ctx.emit('client/recall', { peer, seq: Number(event.messageSeq) });
    });
  }

  get loginState(): LoginState {
    return this.state;
  }

  /** Logs in on startup when a session is saved, so the user does not have to. */
  start() {
    if (this.bot && this.hasSession) void this.login();
  }

  login(): Promise<void> {
    this.loggingIn ??= this.doLogin().finally(() => {
      this.loggingIn = undefined;
    });
    return this.loggingIn;
  }

  private async doLogin() {
    const bot = this.bot;
    if (!bot) return;
    clearTimeout(this.retryTimer);
    this.setState({ kind: 'logging-in' });
    try {
      if (this.hasSession) await bot.login();
      else await bot.qrCodeLogin();
      if (Number(bot.uin) !== this.options.uin) {
        await bot.offline();
        throw new AppError('precondition_failed', `登录的账号 ${bot.uin} 与配置的 ${this.options.uin} 不一致`);
      }
      const info = await bot.fetchUserInfoByUin(bot.uin);
      this.retries = 0;
      this.setState({ kind: 'online', self: { uin: Number(bot.uin), nickname: info.nickname } });
    } catch (error) {
      if (this.disposed) return;
      this.ctx.logger.warn('Login failed', error);
      // A dropped connection with a valid session is worth retrying; anything else needs the user.
      if (this.hasSession && this.retries > 0) this.scheduleRetry(describe(error));
      else this.setState({ kind: 'failed', message: describe(error) });
    }
  }

  private scheduleRetry(reason: string) {
    if (this.disposed) return;
    const delay = retryDelays[Math.min(this.retries, retryDelays.length - 1)] ?? 60_000;
    this.retries += 1;
    this.setState({ kind: 'offline', reason, retryAt: Date.now() + delay });
    clearTimeout(this.retryTimer);
    this.retryTimer = setTimeout(() => void this.login(), delay);
  }

  async logout() {
    clearTimeout(this.retryTimer);
    if (this.bot?.isLoggedIn) await this.bot.offline();
    await this.secrets.delete(sessionKey);
    this.hasSession = false;
    this.retries = 0;
    this.setState({ kind: 'idle' });
  }

  private online(): Bot {
    if (!this.bot || this.state.kind !== 'online') throw new AppError('precondition_failed', '尚未登录');
    return this.bot;
  }

  async getFriends(forceUpdate = false): Promise<Friend[]> {
    return (await this.online().getFriends(forceUpdate)).map(toFriend);
  }

  async getGroups(forceUpdate = false): Promise<Group[]> {
    return (await this.online().getGroups(forceUpdate)).map(toGroup);
  }

  async getPinned(): Promise<PinnedPeers> {
    const pins = await this.online().getPins();
    return { friends: toUinList(pins.friendUins), groups: toUinList(pins.groupUins) };
  }

  async sendMessage(peer: Peer, segments: OutgoingSegment[], clientSeq: number, random: number): Promise<SendResult> {
    const bot = this.online();
    const uin = BigInt(peer.uin);
    const build = (builder: BotOutgoingMessageBuilder) => {
      for (const segment of segments) {
        switch (segment.type) {
          case 'text':
            builder.text(segment.text);
            break;
          case 'mention':
            builder.mention(segment.uin == null ? null : BigInt(segment.uin), segment.name);
            break;
          case 'face':
            builder.face(segment.faceId, segment.isLarge ?? false);
            break;
          case 'reply':
            builder.reply(BigInt(segment.seq));
            break;
        }
      }
    };
    const result =
      peer.scene === 'friend'
        ? await bot.sendFriendMessageRich(uin, BigInt(clientSeq), random, build)
        : await bot.sendGroupMessageRich(uin, BigInt(clientSeq), random, build);
    return { seq: Number(result.sequence), time: Number(result.sendTime) };
  }

  async getHistory(peer: Peer, limit: number, startSeq?: number): Promise<RemoteMessage[]> {
    const bot = this.online();
    const uin = BigInt(peer.uin);
    const start = startSeq === undefined ? null : BigInt(startSeq);
    const history =
      peer.scene === 'friend'
        ? await bot.getFriendHistoryMessages(uin, limit, start)
        : await bot.getGroupHistoryMessages(uin, limit, start);
    return history.messages
      .asJsReadonlyArrayView()
      .flatMap((message) => toRemoteMessage(message) ?? [])
      .sort((a, b) => a.seq - b.seq);
  }

  async markRead(peer: Peer, seq: number, time: number) {
    const bot = this.online();
    if (peer.scene === 'friend') await bot.markFriendMessagesAsRead(BigInt(peer.uin), BigInt(seq), BigInt(time));
    else await bot.markGroupMessagesAsRead(BigInt(peer.uin), BigInt(seq));
  }

  private setState(state: LoginState) {
    this.state = state;
    this.ctx.emit('client/login', state);
  }

  async dispose() {
    this.disposed = true;
    clearTimeout(this.retryTimer);
    try {
      if (this.bot?.isLoggedIn) await this.bot.offline();
    } catch (error) {
      this.ctx.logger.warn('Cannot go offline cleanly', error);
    } finally {
      this.scope.cancel();
    }
  }
}
