import type { Peer } from './peer.ts';
import type { Segment } from './segment.ts';

export interface Sender {
  uin: number;
  /** Group card, remark or nickname, whichever applies; may be empty when unknown. */
  name: string;
}

/** A message as the server describes it. */
export interface RemoteMessage {
  peer: Peer;
  seq: number;
  /** Unix time in seconds. */
  time: number;
  sender: Sender;
  segments: Segment[];
  /** Echoed back for messages this account sent, so a pending local copy can be matched. */
  clientSeq: number;
  random: number;
}

export type MessageStatus = 'sending' | 'sent' | 'failed' | 'recalled';

/** A message as the local store keeps it. */
export interface ChatMessage {
  /** Local row id; stable from the moment a message is sent, before the server assigns `seq`. */
  id: number;
  peer: Peer;
  /** Null while sending, or when sending failed. */
  seq: number | null;
  time: number;
  sender: Sender;
  segments: Segment[];
  status: MessageStatus;
  /** Sent by this account, from this device or another. */
  outgoing: boolean;
}
