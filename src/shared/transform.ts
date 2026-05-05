import type {
  FriendEntity,
  GroupEntity,
  IncomingMessage,
  IncomingSegment,
  OutgoingSegment,
} from '@saltify/milky-types';

import type { Contact, Message } from './model';

export function friendToBaseContact(friend: FriendEntity): Contact {
  return {
    scene: 'friend',
    uin: friend.user_id,
    displayName: friend.remark || friend.nickname,
    isPinned: false,
  };
}

export function groupToBaseContact(group: GroupEntity): Contact {
  return {
    scene: 'group',
    uin: group.group_id,
    displayName: group.group_name,
    isPinned: false,
  };
}

export function contactComparator(a: Contact, b: Contact): number {
  // compare pinned status
  if (a.isPinned && !b.isPinned) return -1;
  if (!a.isPinned && b.isPinned) return 1;

  // compare message time
  const aTime = a.lastMsg?.time ?? 0;
  const bTime = b.lastMsg?.time ?? 0;
  if (aTime !== bTime) {
    return bTime - aTime;
  }

  // compare scene, group first
  if (a.scene !== b.scene) {
    return a.scene === 'group' ? -1 : 1;
  }

  // compare uin to ensure stable sorting
  return a.uin - b.uin;
}

export function incomingSegmentsToText(segments: IncomingSegment[]): string {
  return segments
    .map((seg) => {
      switch (seg.type) {
        case 'text':
          return seg.data.text;
        default:
          return `[${seg.type}]`;
      }
    })
    .join('');
}

export function outgoingSegmentsToText(segments: OutgoingSegment[]): string {
  return segments
    .map((seg) => {
      switch (seg.type) {
        case 'text':
          return seg.data.text;
        default:
          return `[${seg.type}]`;
      }
    })
    .join('');
}

export function transformIncomingMessage(message: IncomingMessage): Message | null {
  switch (message.message_scene) {
    case 'friend':
      return {
        scene: 'friend',
        peerUin: message.peer_id,
        sequence: message.message_seq,
        senderUin: message.peer_id, // must be friend itself
        senderName: message.friend.remark || message.friend.nickname,
        time: message.time,
        content: incomingSegmentsToText(message.segments),
      };
    case 'group':
      return {
        scene: 'group',
        peerUin: message.peer_id,
        sequence: message.message_seq,
        senderUin: message.sender_id,
        senderName: message.group_member.card || message.group_member.nickname,
        time: message.time,
        content: incomingSegmentsToText(message.segments),
      };
    default:
      return null;
  }
}

export function formatShortDateTime(epochSeconds: number): string {
  // within today: HH:mm (24h format)
  // within this year: MM-DD
  // otherwise: YYYY
  const date = new Date(epochSeconds * 1000);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
  } else if (date.getFullYear() === now.getFullYear()) {
    return date.toLocaleDateString([], { month: '2-digit', day: '2-digit' });
  } else {
    return date.getFullYear().toString();
  }
}
