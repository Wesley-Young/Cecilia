import type { IncomingSegment, OutgoingSegment } from '@saltify/milky-types';

export type MessageContent =
  | {
      type: 'incoming';
      segments: IncomingSegment[];
    }
  | {
      type: 'outgoing';
      segments: OutgoingSegment[];
    };

export interface Contact {
  scene: 'friend' | 'group';
  uin: number;
  displayName: string;
  isPinned: boolean;
  unreadCount?: number;
  lastMsg?: {
    time: number;
    content: MessageContent;
  };
}

export interface Message {
  scene: 'friend' | 'group';
  peerUin: number;
  sequence: number;
  senderUin: number;
  time: number;
  content: MessageContent;
  reply?: {
    sequence: number;
    senderUin: number;
    senderName?: string;
    time: number;
    content: MessageContent;
  };
}
