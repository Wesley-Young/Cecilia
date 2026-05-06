import type { FriendEntity, GroupEntity, IncomingMessage } from '@saltify/milky-types';

import { IncomingSegmentDisplay } from '../component/MessageSegmentDisplay';
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

export function transformIncomingMessage(message: IncomingMessage): Message | null {
  const replyData = message.segments[0]?.type === 'reply' ? message.segments[0].data : undefined;
  switch (message.message_scene) {
    case 'friend':
      return {
        scene: 'friend',
        peerUin: message.peer_id,
        sequence: message.message_seq,
        senderUin: message.sender_id,
        time: message.time,
        content: IncomingSegmentDisplay({ segments: message.segments }),
        reply: replyData && {
          senderUin: replyData.sender_id,
          senderName: replyData.sender_name ?? undefined,
          time: replyData.time,
          // @ts-expect-error
          content: IncomingSegmentDisplay({ segments: replyData.segments }),
        },
      };
    case 'group':
      return {
        scene: 'group',
        peerUin: message.peer_id,
        sequence: message.message_seq,
        senderUin: message.sender_id,
        time: message.time,
        content: IncomingSegmentDisplay({ segments: message.segments }),
        reply: replyData && {
          senderUin: replyData.sender_id,
          senderName: replyData.sender_name ?? undefined,
          time: replyData.time,
          // @ts-expect-error
          content: IncomingSegmentDisplay({ segments: replyData.segments }),
        },
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
