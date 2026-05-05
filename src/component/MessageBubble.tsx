import { TextAttributes } from '@opentui/core';

import type { Message } from '../shared/model';

export type MessageBubbleProps = {
  message: Message;
};

export default function MessageBubble(props: MessageBubbleProps) {
  const { message } = props;
  return (
    <box paddingX={1}>
      <box flexDirection="row" justifyContent="space-between">
        <text attributes={TextAttributes.BOLD}>{message.senderName} </text>
        <text attributes={TextAttributes.DIM} flexGrow={1}>
          ({message.senderUin})
        </text>
        <text attributes={TextAttributes.DIM}>#{message.sequence} </text>
        <text attributes={TextAttributes.DIM}>
          {new Date(message.time * 1000).toLocaleTimeString([], { hour12: false })}
        </text>
      </box>
      {message.reply && (
        <box paddingX={1} backgroundColor="#404040">
          <text attributes={TextAttributes.BOLD}>{message.reply.senderName ?? message.reply.senderUin}</text>
          <text>{message.reply.content}</text>
        </box>
      )}
      <text>{message.content}</text>
    </box>
  );
}
