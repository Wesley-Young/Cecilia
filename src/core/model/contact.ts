import type { Peer } from './peer.ts';

export interface Friend {
  uin: number;
  nickname: string;
  remark: string;
}

export interface Group {
  uin: number;
  name: string;
  memberCount: number;
  capacity: number;
}

export interface SelfInfo {
  uin: number;
  nickname: string;
}

export interface Conversation {
  peer: Peer;
  name: string;
  /** One-line preview of the last message, with the sender's name for groups. */
  summary: string;
  /** Unix time in seconds of the last message; 0 when there is none yet. */
  time: number;
  unread: number;
  pinned: boolean;
}

export function friendName(friend: Friend): string {
  return friend.remark || friend.nickname || String(friend.uin);
}
