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
        <text attributes={TextAttributes.BOLD}>{message.senderName}</text>
        <text attributes={TextAttributes.DIM} flexGrow={1}>
          #{message.sequence}
        </text>
        <text attributes={TextAttributes.DIM}>{new Date(message.time * 1000).toLocaleTimeString()}</text>
      </box>
      <text>{message.content}</text>
    </box>
  );
}
