import { serviceToken } from '@fraqjs/kernel';

import type { Friend, Group, GroupMember, SelfInfo } from '#core/model/contact.ts';
import type { LoginState } from '#core/model/login.ts';
import type { RemoteMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import type { OutgoingSegment } from '#core/model/segment.ts';
import type { AccountSettings } from '#core/model/settings.ts';

export interface SendResult {
  seq: number;
  time: number;
}

export interface PinnedPeers {
  friends: number[];
  groups: number[];
}

/**
 * The connection to the IM service, in domain terms. The acidify plugin provides it for real accounts and the mock
 * plugin for development; exactly one of them is installed. Login state changes and incoming messages are reported
 * as `client/*` events on the context.
 */
export abstract class ChatClient {
  static readonly token = serviceToken<ChatClient>('cecilia/ChatClient');

  abstract get loginState(): LoginState;

  /** The logged-in account; undefined until the first login succeeds. */
  get self(): SelfInfo | undefined {
    const state = this.loginState;
    return state.kind === 'online' ? state.self : undefined;
  }

  /** Logs in with the saved session, or shows a QR code when there is none. */
  abstract login(): Promise<void>;

  /** Goes offline and forgets the saved session, so the next login needs a QR code. */
  abstract logout(): Promise<void>;

  /** Reconnects with new account settings, going offline first. The mock has nothing to configure. */
  abstract applySettings(settings: AccountSettings): Promise<void>;

  abstract getFriends(forceUpdate?: boolean): Promise<Friend[]>;
  abstract getGroups(forceUpdate?: boolean): Promise<Group[]>;
  abstract getGroupMembers(group: number, forceUpdate?: boolean): Promise<GroupMember[]>;
  abstract getPinned(): Promise<PinnedPeers>;

  /** `clientSeq` and `random` come back on the echoed message, so the caller can match it to its pending copy. */
  abstract sendMessage(peer: Peer, segments: OutgoingSegment[], clientSeq: number, random: number): Promise<SendResult>;

  /** Messages up to and including `startSeq` (the latest when omitted), oldest first. At most 30. */
  abstract getHistory(peer: Peer, limit: number, startSeq?: number): Promise<RemoteMessage[]>;

  abstract markRead(peer: Peer, seq: number, time: number): Promise<void>;

  /** A temporary URL for an image segment's `fileId`; it expires after a while. */
  abstract getImageUrl(fileId: string): Promise<string>;
}
