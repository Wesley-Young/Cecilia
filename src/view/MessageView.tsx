import type { ScrollBoxRenderable } from '@opentui/core';
import { useKeyboard } from '@opentui/react';
import { useCallback, useEffect, useRef, useState } from 'react';
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
  const [loadingError, setLoadingError] = useState<string | null>(null);

  const scrollRef = useRef<ScrollBoxRenderable>(null);
  const historyRequestRef = useRef(0);
  const activeScene = active?.scene;
  const activeUin = active?.uin;

  const loadHistoryMessages = useCallback(
    async (
      contact: {
        scene: 'friend' | 'group';
        uin: number;
      },
      options?: {
        mode?: 'replace' | 'prepend';
        startMessageSeq?: number;
        preserveScroll?: {
          scrollbox: ScrollBoxRenderable;
          beforeScrollTop: number;
          beforeScrollHeight: number;
        };
        scrollToBottom?: boolean;
      },
    ) => {
      const requestId = historyRequestRef.current + 1;
      historyRequestRef.current = requestId;

      setLoadingHistory(true);
      try {
        const { messages: historyMessages } = await milky.message.getHistoryMessages({
          message_scene: contact.scene,
          peer_id: contact.uin,
          limit: 30,
          start_message_seq: options?.startMessageSeq,
        });

        if (historyRequestRef.current !== requestId) {
          return;
        }

        const transformed = historyMessages
          .map(transformIncomingMessage)
          .filter((message): message is Message => message !== null);

        setMessages((messages) => {
          if (options?.mode === 'prepend') {
            const existingKeys = new Set(messages.map((message) => message.sequence));
            messages.unshift(...transformed.filter((message) => !existingKeys.has(message.sequence)));
            return;
          }

          const incomingKeys = new Set(transformed.map((message) => message.sequence));
          const realtimeMessages = messages.filter((message) => !incomingKeys.has(message.sequence));
          const mergedMessages = [...transformed, ...realtimeMessages].sort((a, b) => a.sequence - b.sequence);
          messages.splice(0, messages.length, ...mergedMessages);
        });

        if (options?.scrollToBottom) {
          setTimeout(() => {
            if (historyRequestRef.current !== requestId) {
              return;
            }

            const scrollbox = scrollRef.current;
            scrollbox?.scrollTo({ x: 0, y: scrollbox.scrollHeight });
          });
        } else if (options?.preserveScroll) {
          const preserveScroll = options.preserveScroll;
          setTimeout(() => {
            if (historyRequestRef.current !== requestId) {
              return;
            }

            const afterScrollHeight = preserveScroll.scrollbox.scrollHeight;
            preserveScroll.scrollbox.scrollTo({
              x: 0,
              y: preserveScroll.beforeScrollTop + (afterScrollHeight - preserveScroll.beforeScrollHeight),
            });
          });
        }
      } catch (e) {
        setLoadingError(e instanceof Error ? e.message : `Failed to load messages: ${String(e)}`);
      } finally {
        if (historyRequestRef.current === requestId) {
          setLoadingHistory(false);
        }
      }
    },
    [milky, setMessages],
  );

  useEffect(() => {
    setLoadingError(null);

    if (!activeScene || activeUin === undefined) {
      historyRequestRef.current += 1;
      setMessages([]);
      setLoadingHistory(false);
      return;
    }

    setMessages([]);
    void loadHistoryMessages({ scene: activeScene, uin: activeUin }, { scrollToBottom: true });

    return () => {
      historyRequestRef.current += 1;
    };
  }, [activeScene, activeUin, loadHistoryMessages, setMessages]);

  useEffect(() => {
    const listener = defineMilkyListener('message_receive', ({ data }) => {
      if (!activeScene || activeUin === undefined || activeScene !== data.message_scene || activeUin !== data.peer_id) {
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
  }, [eventSource, activeScene, activeUin, setMessages]);

  useKeyboard((e) => {
    const scrollbox = scrollRef.current;
    if (!scrollbox) return;

    const beforeScrollTop = scrollbox.scrollTop;
    if (
      activeScene &&
      activeUin !== undefined &&
      !isLoadingHistory &&
      beforeScrollTop === 0 &&
      focused &&
      e.name === 't'
    ) {
      const beforeSequence = messages[0]?.sequence;
      void loadHistoryMessages(
        { scene: activeScene, uin: activeUin },
        {
          mode: 'prepend',
          startMessageSeq: beforeSequence && beforeSequence - 1,
          preserveScroll: {
            scrollbox,
            beforeScrollTop,
            beforeScrollHeight: scrollbox.scrollHeight,
          },
        },
      );
    }
  });

  if (!active) {
    return null;
  }

  return (
    <scrollbox ref={scrollRef} stickyScroll>
      {loadingError ? (
        <box backgroundColor="brightRed" alignItems="center">
          <text fg="black">{loadingError}</text>
        </box>
      ) : !isLoadingHistory ? (
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
