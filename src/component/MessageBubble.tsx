import { TextAttributes } from '@opentui/core';
import { useState } from 'react';

import { useRuntimeCache } from '../shared/cache';
import type { Message } from '../shared/model';
import MessageSegmentDisplay from './MessageSegmentDisplay';

export type MessageBubbleProps = {
  message: Message;
};

export default function MessageBubble(props: MessageBubbleProps) {
  const { message } = props;
  const { selfInfo, friends, groupMembers } = useRuntimeCache();
  const [isHovered, setHovered] = useState(false);

  let senderName: string = message.senderUin.toString();
  switch (message.scene) {
    case 'friend':
      if (message.senderUin === message.peerUin) {
        senderName =
          friends[message.senderUin]?.remark || friends[message.senderUin]?.nickname || message.senderUin.toString();
      } else {
        senderName = selfInfo.nickname;
      }
      break;
    case 'group':
      senderName =
        groupMembers[message.peerUin]?.[message.senderUin]?.card ||
        groupMembers[message.peerUin]?.[message.senderUin]?.nickname ||
        message.senderUin.toString();
      break;
  }

  return (
    <box
      paddingX={1}
      backgroundColor={isHovered ? '#303030' : undefined}
      onMouseOver={() => setHovered(true)}
      onMouseOut={() => setHovered(false)}
    >
      <box flexDirection="row" gap={1}>
        <text attributes={TextAttributes.BOLD}>{senderName}</text>
        <text attributes={TextAttributes.DIM} flexGrow={1}>
          ({message.senderUin})
        </text>
        <text attributes={TextAttributes.DIM}>#{message.sequence}</text>
        <text attributes={TextAttributes.DIM}>
          {new Date(message.time * 1000).toLocaleTimeString([], { hour12: false })}
        </text>
      </box>
      {message.reply && (
        <box border borderStyle="rounded" paddingX={1}>
          <box flexDirection="row" gap={1}>
            <text attributes={TextAttributes.BOLD}>
              {message.reply.senderName ||
                (message.senderUin === selfInfo.uin
                  ? selfInfo.nickname
                  : message.scene === 'friend'
                    ? friends[message.reply.senderUin]?.remark || friends[message.reply.senderUin]?.nickname
                    : groupMembers[message.peerUin]?.[message.reply.senderUin]?.card ||
                      groupMembers[message.peerUin]?.[message.reply.senderUin]?.nickname) ||
                message.reply.senderUin}
            </text>
            <text attributes={TextAttributes.DIM}>({message.reply.senderUin})</text>
            <text attributes={TextAttributes.DIM}>#{message.reply.sequence}</text>
            <text attributes={TextAttributes.DIM}>
              {new Date(message.reply.time * 1000).toLocaleTimeString([], { hour12: false })}
            </text>
          </box>
          <text attributes={TextAttributes.DIM}>
            <MessageSegmentDisplay content={message.reply.content} />
          </text>
        </box>
      )}
      <text>
        <MessageSegmentDisplay content={message.content} />
      </text>
    </box>
  );
}
