import { Button } from '@heroui/react';
import { useMutation } from '@tanstack/react-query';
import { Reply, X } from 'lucide-react';
import { type KeyboardEvent, useEffect, useRef, useState } from 'react';

import type { ChatMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import { summarize } from '#core/model/segment.ts';
import { core, errorMessage } from '@/lib/rpc';

/** The message being replied to, above the text. */
function ReplyBar({ message, onCancel }: { message: ChatMessage; onCancel: () => void }) {
  return (
    <div className="mx-3 mt-2 flex shrink-0 items-center gap-2 rounded-lg bg-default py-1 pr-1 pl-3 text-xs">
      <Reply className="size-3.5 shrink-0 text-muted" aria-label="回复" />
      <p className="min-w-0 flex-1 truncate">
        <span className="font-medium">{message.sender.name || message.sender.uin}</span>
        <span className="text-muted">：{summarize(message.segments) || '[消息]'}</span>
      </p>
      <Button variant="ghost" size="sm" isIconOnly aria-label="取消回复" onPress={onCancel} className="size-6 shrink-0">
        <X className="size-3.5" />
      </Button>
    </div>
  );
}

interface Props {
  peer: Peer;
  disabled: boolean;
  replyTo: ChatMessage | undefined;
  onCancelReply: () => void;
}

/** Plain text for now: Enter sends, Shift+Enter starts a new line, Backspace at the start drops the reply. */
export default function Composer({ peer, disabled, replyTo, onCancelReply }: Props) {
  const [text, setText] = useState('');
  const send = useMutation(core.messages.send.mutationOptions());
  const field = useRef<HTMLTextAreaElement>(null);
  const canSend = !disabled && text.trim().length > 0;

  // Choosing a message to reply to goes straight on to typing the reply.
  useEffect(() => {
    if (replyTo) field.current?.focus();
  }, [replyTo]);

  const submit = () => {
    if (!canSend) return;
    send.mutate({ peer, text, reply: replyTo?.seq });
    setText('');
    onCancelReply();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter while an IME is composing picks a candidate instead.
    if (event.nativeEvent.isComposing) return;
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    } else if (event.key === 'Backspace' && replyTo) {
      // The reply sits before the text, so Backspace at the very start deletes it like a character.
      const { selectionStart, selectionEnd } = event.currentTarget;
      if (selectionStart !== 0 || selectionEnd !== 0) return;
      event.preventDefault();
      onCancelReply();
    }
  };

  // The whole block is the field: no border or send button, Enter is the way to send.
  // It takes a share of the window's height, within bounds for very short and very tall windows.
  return (
    <div className="flex h-[clamp(8rem,25vh,16rem)] shrink-0 flex-col border-separator border-t bg-surface text-surface-foreground">
      {send.error && <p className="px-5 pt-2 text-xs text-danger">{errorMessage(send.error)}</p>}
      {replyTo && <ReplyBar message={replyTo} onCancel={onCancelReply} />}
      <textarea
        ref={field}
        aria-label="消息"
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder={disabled ? '离线中，暂时无法发送' : '输入消息，Enter 发送，Shift + Enter 换行'}
        disabled={disabled}
        className="selectable min-h-0 flex-1 resize-none bg-transparent px-5 py-3 text-[14px] leading-relaxed outline-none placeholder:text-muted disabled:cursor-not-allowed"
      />
    </div>
  );
}
