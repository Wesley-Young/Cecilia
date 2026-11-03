import { Spinner } from '@heroui/react';
import { CircleAlert } from 'lucide-react';

import type { ChatMessage } from '#core/model/message.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { ReplyQuote, SegmentView } from '@/components/SegmentView';

interface Props {
  message: ChatMessage;
  /** The previous message is from the same sender, so the name and avatar are not repeated. */
  continued: boolean;
  /** The next message is from the same sender, so this bubble joins it. */
  followed: boolean;
}

// A run from one sender reads as one column: the corner toward the avatar is the tail, and the corners between
// bubbles of the run are tightened.
const corners = {
  in: { end: 'rounded-tl-md', joined: 'rounded-tl-md rounded-bl-md' },
  out: { end: 'rounded-tr-md', joined: 'rounded-tr-md rounded-br-md' },
};

export default function MessageBubble({ message, continued, followed }: Props) {
  if (message.status === 'recalled') {
    return (
      <p className="py-1 text-center text-xs text-muted">
        {message.outgoing ? '你' : message.sender.name}撤回了一条消息
      </p>
    );
  }

  const { outgoing } = message;
  const reply = message.segments.find((segment) => segment.type === 'reply');
  const showName = !outgoing && !continued && message.peer.scene === 'group';
  return (
    <div className={`flex gap-2.5 px-5 ${continued ? 'pt-0.5' : 'pt-3'} ${outgoing ? 'flex-row-reverse' : ''}`}>
      <div className="w-8 shrink-0">
        {!continued && (
          <PeerAvatar peer={{ scene: 'friend', uin: message.sender.uin }} name={message.sender.name} size="sm" />
        )}
      </div>
      <div className={`flex min-w-0 max-w-[70%] flex-col ${outgoing ? 'items-end' : 'items-start'}`}>
        {showName && <span className="mb-1 px-1 text-xs text-muted">{message.sender.name}</span>}
        <div className="flex items-end gap-1.5">
          {outgoing && message.status === 'sending' && <Spinner size="sm" className="mb-2" />}
          {outgoing && message.status === 'failed' && (
            <CircleAlert className="mb-2 size-4 shrink-0 text-danger" aria-label="发送失败" />
          )}
          <div
            className={`selectable whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2 text-[14px] leading-relaxed shadow-xs ${
              corners[outgoing ? 'out' : 'in'][followed ? 'joined' : 'end']
            } ${
              outgoing ? 'bg-bubble-out text-bubble-out-foreground' : 'bg-bubble-in text-bubble-in-foreground'
            } ${message.status === 'sending' ? 'opacity-70' : ''}`}
          >
            {reply && <ReplyQuote segment={reply} />}
            {message.segments.map((segment, index) => (
              // Segments never reorder within a message.
              // biome-ignore lint/suspicious/noArrayIndexKey: see above
              <SegmentView key={index} segment={segment} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
