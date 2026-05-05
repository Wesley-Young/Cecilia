import type { ReactNode } from 'react';

export interface Contact {
  scene: 'friend' | 'group';
  uin: number;
  displayName: string;
  isPinned: boolean;
  unreadCount?: number;
  lastMsg?: {
    time: number;
    content: ReactNode;
  };
}

export interface Message {
  scene: 'friend' | 'group';
  peerUin: number;
  sequence: number;
  senderUin: number;
  senderName: string;
  time: number;
  content: ReactNode;
  reply?: {
    senderUin: number;
    senderName?: string;
    content: ReactNode;
  };
}
