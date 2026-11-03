import { Spinner } from '@heroui/react';
import { CircleAlert } from 'lucide-react';

import type { ChatMessage } from '#core/model/message.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { isPicture, PictureView, ReplyQuote, SegmentView, trimAroundPictures } from '@/components/SegmentView';

interface Props {
  message: ChatMessage;
  /** The previous message is from the same sender, so the name and avatar are not repeated. */
  continued: boolean;
  /** The next message is from the same sender, so this bubble joins it. */
  followed: boolean;
  /** Just jumped to from a reply. */
  highlighted: boolean;
  /** Just arrived: it rises into place. */
  entering: boolean;
  /** Scrolls to the quoted message; absent when it is not loaded or too far back. */
  onJumpToReply: (() => void) | undefined;
}

// A run from one sender reads as one column: the corner toward the avatar is the tail, and the corners between
// bubbles of the run are tightened.
const corners = {
  in: { end: 'rounded-tl-md', joined: 'rounded-tl-md rounded-bl-md' },
  out: { end: 'rounded-tr-md', joined: 'rounded-tr-md rounded-br-md' },
};

/** Flashes the row behind a message jumped to from a reply; 2px past the content on each side. */
function Flash({ className }: { className: string }) {
  return (
    <span
      aria-hidden
      className={`pointer-events-none absolute inset-x-0 -z-10 animate-flash bg-accent/15 ${className}`}
    />
  );
}

/** Rises into place; also used for the time separators above new messages. */
export const enterClass = 'animate-message-enter motion-reduce:animate-none';

export default function MessageBubble(props: Props) {
  const { message, continued, followed, highlighted, entering, onJumpToReply } = props;
  const enter = entering ? enterClass : '';
  if (message.status === 'recalled') {
    return (
      <p className={`relative isolate py-1 text-center text-xs text-muted ${enter}`}>
        {highlighted && <Flash className="inset-y-0.5" />}
        {message.outgoing ? '你' : message.sender.name}撤回了一条消息
      </p>
    );
  }

  const { outgoing } = message;
  const reply = message.segments.find((segment) => segment.type === 'reply');
  // A lone picture stands without a bubble.
  const [only] = message.segments;
  const picture = message.segments.length === 1 && only && isPicture(only) ? only : undefined;
  const sending = message.status === 'sending' ? 'opacity-70' : '';
  const showName = !outgoing && !continued && message.peer.scene === 'group';
  return (
    <div
      className={`relative isolate flex gap-2.5 px-5 ${continued ? 'pt-0.5' : 'pt-3'} ${outgoing ? 'flex-row-reverse' : ''} ${enter}`}
    >
      {highlighted && <Flash className={`-bottom-0.5 ${continued ? 'top-0' : 'top-2.5'}`} />}
      <div className="w-8 shrink-0">
        {!continued && (
          <PeerAvatar peer={{ scene: 'friend', uin: message.sender.uin }} name={message.sender.name} size="sm" />
        )}
      </div>
      {/* At most 70% of the row, and no wider than reads comfortably on a wide window. */}
      <div className={`flex min-w-0 max-w-[min(70%,36rem)] flex-col ${outgoing ? 'items-end' : 'items-start'}`}>
        {showName && <span className="mb-1 px-1 text-xs text-muted">{message.sender.name}</span>}
        <div className="flex max-w-full items-end gap-1.5">
          {outgoing && message.status === 'sending' && <Spinner size="sm" className="mb-2" />}
          {outgoing && message.status === 'failed' && (
            <CircleAlert className="mb-2 size-4 shrink-0 text-danger" aria-label="发送失败" />
          )}
          {picture ? (
            <PictureView segment={picture} className={`rounded-2xl ${sending}`} />
          ) : (
            <div
              // Long links and other unbroken runs wrap anywhere instead of widening the bubble.
              className={`selectable min-w-0 whitespace-pre-wrap wrap-anywhere rounded-2xl px-3.5 py-2 text-[14px] leading-relaxed shadow-xs ${
                corners[outgoing ? 'out' : 'in'][followed ? 'joined' : 'end']
              } ${outgoing ? 'bg-bubble-out text-bubble-out-foreground' : 'bg-bubble-in text-bubble-in-foreground'} ${sending}`}
            >
              {reply && <ReplyQuote segment={reply} onJump={onJumpToReply} />}
              {trimAroundPictures(message.segments).map((segment, index) => (
                // Segments never reorder within a message.
                // biome-ignore lint/suspicious/noArrayIndexKey: see above
                <SegmentView key={index} segment={segment} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
