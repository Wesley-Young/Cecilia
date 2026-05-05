import type { ScrollBoxRenderable } from '@opentui/core';
import { useKeyboard } from '@opentui/react';
import { useEffect, useRef, useState } from 'react';
import { useImmer } from 'use-immer';

import MessageBubble from '../component/MessageBubble';
import type { Message } from '../shared/model';
import { defineMilkyListener, useMilky, useMilkyEvent } from '../shared/protocol';
import { transformIncomingMessage } from '../shared/transform';

export type MessageViewProps = {
  active?: {
    scene: 'friend' | 'group';
    uin: number;
  };
  focused?: boolean;
};

export default function MessageView(props: MessageViewProps) {
  const { active, focused } = props;
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [messages, setMessages] = useImmer<Message[]>([]);
  const [isLoadingHistory, setLoadingHistory] = useState(false);
  const scrollRef = useRef<ScrollBoxRenderable>(null);

  useEffect(() => {
    setMessages([]);

    const listener = defineMilkyListener('message_receive', ({ data }) => {
      if (!active || active.scene !== data.message_scene || active.uin !== data.peer_id) {
        return;
      }
      setMessages((messages) => {
        const transformed = transformIncomingMessage(data);
        if (transformed) {
          messages.push(transformed);
        }
      });
    });

    eventSource.on('message_receive', listener);
    return () => {
      eventSource.off('message_receive', listener);
    };
  }, [eventSource, active, setMessages]);

  useKeyboard(async (e) => {
    const scrollbox = scrollRef.current;
    if (!scrollbox) return;

    const beforeScrollTop = scrollbox.scrollTop;
    if (active && !isLoadingHistory && beforeScrollTop === 0 && focused && e.name === 't') {
      setLoadingHistory(true);
      const beforeSequence = messages[0]?.sequence;
      try {
        const beforeScrollHeight = scrollbox.scrollHeight;
        const { messages: historyMessages } = await milky.message.getHistoryMessages({
          message_scene: active.scene,
          peer_id: active.uin,
          limit: 30,
          start_message_seq: beforeSequence && beforeSequence - 1,
        });
        setMessages((messages) => {
          const transformed = historyMessages.map(transformIncomingMessage).filter((m) => m !== null);
          messages.unshift(...transformed);
        });
        setTimeout(() => {
          const afterScrollHeight = scrollbox.scrollHeight;
          scrollbox.scrollTo({ x: 0, y: beforeScrollTop + (afterScrollHeight - beforeScrollHeight) });
        });
      } finally {
        setLoadingHistory(false);
      }
    }
  });

  if (!active) {
    return null;
  }

  return (
    <scrollbox ref={scrollRef} stickyScroll>
      {!isLoadingHistory ? (
        <box backgroundColor="brightGreen" alignItems="center">
          <text fg="black">
            Press <b>t</b> to load more history messages
          </text>
        </box>
      ) : (
        <box backgroundColor="brightYellow" alignItems="center">
          <text fg="black">Loading history messages, please wait...</text>
        </box>
      )}
      {messages.map((m) => {
        const id = `${m.scene}-${m.peerUin}-${m.sequence}`;
        return (
          <box id={id} key={id}>
            <MessageBubble message={m} />
            <box height={1} />
          </box>
        );
      })}
    </scrollbox>
  );
}
