export interface Contact {
  scene: 'friend' | 'group';
  uin: number;
  displayName: string;
  isPinned: boolean;
  unreadCount?: number;
  lastMsg?: {
    time: number;
    content: string;
  };
}

export interface Message {
  scene: 'friend' | 'group';
  peerUin: number;
  sequence: number;
  senderUin: number;
  senderName: string;
  time: number;
  content: string;
}
