import type { Peer } from './peer.ts';

export type Gender = 'male' | 'female' | 'unknown';

export interface Friend {
  uin: number;
  nickname: string;
  remark: string;
  /** The friend's signature; empty when not set. */
  bio: string;
  /** The friend's QID; empty when not set. */
  qid: string;
  /** 0 when hidden. */
  age: number;
  gender: Gender;
  /** The friend group (分组) this friend is filed under. */
  category: string;
}

export interface Group {
  uin: number;
  name: string;
  memberCount: number;
  capacity: number;
  /** Unix time in seconds; 0 when unknown. */
  createdAt: number;
  description: string;
  /** The start of the latest announcement; empty when there is none. */
  announcement: string;
}

export type MemberRole = 'owner' | 'admin' | 'member';

export interface GroupMember {
  uin: number;
  nickname: string;
  /** The member's name in this group; empty when not set. */
  card: string;
  /** The title the owner gave this member; empty when not set. */
  title: string;
  level: number;
  role: MemberRole;
  /** Unix time in seconds. */
  joinedAt: number;
  /** Unix time in seconds. */
  lastSpokeAt: number;
}

/** What a user's profile adds to the friend list; fetched each time it is shown. */
/** What anyone's public profile says, friend or not. Empty strings and 0 stand for what is not filled in. */
export interface UserProfile {
  uin: number;
  nickname: string;
  bio: string;
  gender: Gender;
  age: number;
  qid: string;
  level: number;
  country: string;
  city: string;
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

export function memberName(member: GroupMember): string {
  return member.card || member.nickname || String(member.uin);
}
