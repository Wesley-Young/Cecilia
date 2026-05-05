import { TextAttributes } from '@opentui/core';
import { useState } from 'react';

import type { Message } from '../shared/model';

export type MessageBubbleProps = {
  message: Message;
};

export default function MessageBubble(props: MessageBubbleProps) {
  const { message } = props;
  const [isHovered, setHovered] = useState(false);

  return (
    <box
      paddingX={1}
      backgroundColor={isHovered ? '#303030' : undefined}
      onMouseOver={() => setHovered(true)}
      onMouseOut={() => setHovered(false)}
    >
      <box flexDirection="row" gap={1}>
        <text attributes={TextAttributes.BOLD}>{message.senderName}</text>
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
            <text attributes={TextAttributes.BOLD}>{message.reply.senderName ?? message.reply.senderUin}</text>
            <text attributes={TextAttributes.DIM}>
              {new Date(message.reply.time * 1000).toLocaleTimeString([], { hour12: false })}
            </text>
          </box>
          <text>{message.reply.content}</text>
        </box>
      )}
      <text>{message.content}</text>
    </box>
  );
}
