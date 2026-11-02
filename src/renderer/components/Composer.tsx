import { Button, TextArea } from '@heroui/react';
import { useMutation } from '@tanstack/react-query';
import { SendHorizontal } from 'lucide-react';
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

  return (
    <div className="border-separator border-t px-4 pt-3 pb-4">
      {send.error && <p className="mb-2 text-xs text-danger">{errorMessage(send.error)}</p>}
      <div className="flex items-end gap-2">
        <TextArea
          aria-label="消息"
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder={disabled ? '离线中，暂时无法发送' : '输入消息'}
          disabled={disabled}
          rows={2}
          fullWidth
          className="max-h-40 resize-none"
        />
        <Button variant="primary" isIconOnly aria-label="发送" isDisabled={!canSend} onPress={submit}>
          <SendHorizontal className="size-4" />
        </Button>
      </div>
    </div>
  );
}
