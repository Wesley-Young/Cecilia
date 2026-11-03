import {
  type BotFriend,
  type BotGroup,
  type BotGroupMember,
  type BotIncomingMessage,
  BotIncomingSegment,
  type KtList,
  MessageScene,
  type QRCodeState,
} from '@acidify/core';

import type { Friend, Group, GroupMember } from '#core/model/contact.ts';
import type { QrCodeState } from '#core/model/login.ts';
import type { RemoteMessage } from '#core/model/message.ts';
import type { Peer, Scene } from '#core/model/peer.ts';
import type { Segment } from '#core/model/segment.ts';

// The only module that reads acidify's Kotlin objects; everything past it sees plain data.

const list = <T>(items: KtList<T>): readonly T[] => items.asJsReadonlyArrayView();

/** Temporary sessions are not shown. */
function toScene(scene: MessageScene): Scene | undefined {
  if (scene === MessageScene.FRIEND) return 'friend';
  if (scene === MessageScene.GROUP) return 'group';
  return undefined;
}

export function toPeer(scene: MessageScene, uin: bigint): Peer | undefined {
  const mapped = toScene(scene);
  return mapped ? { scene: mapped, uin: Number(uin) } : undefined;
}

export function toSegment(segment: BotIncomingSegment): Segment {
  if (segment instanceof BotIncomingSegment.Text) return { type: 'text', text: segment.text };
  if (segment instanceof BotIncomingSegment.Mention) {
    return { type: 'mention', uin: segment.uin == null ? null : Number(segment.uin), name: segment.name };
  }
  if (segment instanceof BotIncomingSegment.Face) {
    return { type: 'face', faceId: segment.faceId, summary: segment.summary, isLarge: segment.isLarge };
  }
  if (segment instanceof BotIncomingSegment.Reply) {
    return {
      type: 'reply',
      seq: Number(segment.sequence),
      senderUin: Number(segment.senderUin),
      senderName: segment.senderName ?? null,
      time: Number(segment.timestamp),
      segments: list(segment.segments).map(toSegment),
    };
  }
  if (segment instanceof BotIncomingSegment.Image) {
    return {
      type: 'image',
      fileId: segment.fileId,
      width: segment.width,
      height: segment.height,
      summary: segment.summary,
    };
  }
  if (segment instanceof BotIncomingSegment.Record) {
    return { type: 'record', fileId: segment.fileId, duration: segment.duration };
  }
  if (segment instanceof BotIncomingSegment.Video) {
    return {
      type: 'video',
      fileId: segment.fileId,
      duration: segment.duration,
      width: segment.width,
      height: segment.height,
    };
  }
  if (segment instanceof BotIncomingSegment.File) {
    return {
      type: 'file',
      fileId: segment.fileId,
      name: segment.fileName,
      size: Number(segment.fileSize),
      hash: segment.fileHash ?? null,
    };
  }
  if (segment instanceof BotIncomingSegment.Forward) {
    return {
      type: 'forward',
      resId: segment.resId,
      title: segment.title,
      preview: [...list(segment.preview)],
      summary: segment.summary,
    };
  }
  if (segment instanceof BotIncomingSegment.MarketFace) {
    return { type: 'market-face', url: segment.url, summary: segment.summary };
  }
  if (segment instanceof BotIncomingSegment.LightApp) {
    return { type: 'light-app', appName: segment.appName, payload: segment.jsonPayload };
  }
  if (segment instanceof BotIncomingSegment.Markdown) return { type: 'markdown', content: segment.content };
  return { type: 'unknown', summary: '[不支持的消息]' };
}

export function toRemoteMessage(message: BotIncomingMessage): RemoteMessage | undefined {
  const peer = toPeer(message.scene, message.peerUin);
  if (!peer) return undefined;
  const extra = message.extraInfo;
  return {
    peer,
    seq: Number(message.sequence),
    time: Number(message.timestamp),
    // Friend messages carry no name; the store fills it in from the contact list.
    sender: { uin: Number(message.senderUin), name: extra ? extra.groupCard || extra.nick : '' },
    segments: list(message.segments).map(toSegment),
    clientSeq: Number(message.clientSequence),
    random: message.random,
  };
}

export function toFriend(friend: BotFriend): Friend {
  return { uin: Number(friend.uin), nickname: friend.nickname, remark: friend.remark };
}

export function toGroup(group: BotGroup): Group {
  return { uin: Number(group.uin), name: group.name, memberCount: group.memberCount, capacity: group.capacity };
}

export function toGroupMember(member: BotGroupMember): GroupMember {
  return { uin: Number(member.uin), nickname: member.nickname, card: member.card };
}

export function toUinList(uins: KtList<bigint>): number[] {
  return list(uins).map(Number);
}

/** Null for states that need no change on screen (the code was confirmed, or the state is unknown). */
export function toQrCodeState(state: QRCodeState): QrCodeState | null {
  switch (state.name) {
    case 'WAITING_FOR_SCAN':
      return 'waiting';
    case 'WAITING_FOR_CONFIRMATION':
      return 'scanned';
    case 'CODE_EXPIRED':
      return 'expired';
    case 'CANCELLED':
      return 'canceled';
    default:
      return null;
  }
}

export function toDataUrl(png: Int8Array): string {
  return `data:image/png;base64,${Buffer.from(png.buffer, png.byteOffset, png.byteLength).toString('base64')}`;
}
