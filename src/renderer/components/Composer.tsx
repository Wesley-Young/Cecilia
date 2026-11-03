import { useMutation } from '@tanstack/react-query';
import { type KeyboardEvent, useState } from 'react';

import type { Peer } from '#core/model/peer.ts';
import { core, errorMessage } from '@/lib/rpc';

/** Plain text for now: Enter sends, Shift+Enter starts a new line. */
export default function Composer({ peer, disabled }: { peer: Peer; disabled: boolean }) {
  const [text, setText] = useState('');
  const send = useMutation(core.messages.send.mutationOptions());
  const canSend = !disabled && text.trim().length > 0;

  const submit = () => {
    if (!canSend) return;
    send.mutate({ peer, text });
    setText('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter while an IME is composing picks a candidate instead.
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  // The whole block is the field: no border or send button, Enter is the way to send.
  // It takes a share of the window's height, within bounds for very short and very tall windows.
  return (
    <div className="flex h-[clamp(8rem,25vh,16rem)] shrink-0 flex-col border-separator border-t bg-surface text-surface-foreground">
      {send.error && <p className="px-5 pt-2 text-xs text-danger">{errorMessage(send.error)}</p>}
      <textarea
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
