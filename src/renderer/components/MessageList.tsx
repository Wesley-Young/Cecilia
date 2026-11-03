import { Spinner } from '@heroui/react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useMemo, useRef } from 'react';
import { VList, type VListHandle } from 'virtua';

import type { ChatMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import MessageBubble from '@/components/MessageBubble';
import { historyInput } from '@/lib/cache';
import { messageTime } from '@/lib/format';
import { core, errorMessage } from '@/lib/rpc';

/** A time separator appears when this much passes between two messages. */
const separatorGap = 5 * 60;
/** Load older messages when the top is this close. */
const loadThreshold = 400;
/** Counts as "at the bottom", where new messages keep the view pinned to the end. */
const bottomThreshold = 80;

interface MessageRow {
  kind: 'message';
  key: string;
  message: ChatMessage;
  /** The previous message is from the same sender. */
  continued: boolean;
  /** The next message is from the same sender. */
  followed: boolean;
}

type Row = { kind: 'time'; key: string; time: number } | MessageRow;

function toRows(messages: ChatMessage[]): Row[] {
  const rows: Row[] = [];
  let previous: MessageRow | undefined;
  for (const message of messages) {
    const gap = !previous || message.time - previous.message.time > separatorGap;
    if (gap) rows.push({ kind: 'time', key: `time:${message.id}`, time: message.time });
    const continued =
      !gap &&
      previous?.message.sender.uin === message.sender.uin &&
      previous.message.status !== 'recalled' &&
      message.status !== 'recalled';
    if (continued && previous) previous.followed = true;
    previous = { kind: 'message', key: `message:${message.id}`, message, continued, followed: false };
    rows.push(previous);
  }
  return rows;
}

export default function MessageList({ peer, onReachEnd }: { peer: Peer; onReachEnd: () => void }) {
  const history = useInfiniteQuery(
    core.messages.history.infiniteQueryOptions(historyInput(peer), {
      getNextPageParam: (page) => page.nextCursor,
    }),
  );
  const messages = useMemo(
    () => (history.data?.pages ?? []).toReversed().flatMap((page) => page.messages),
    [history.data],
  );
  const rows = useMemo(() => toRows(messages), [messages]);

  const list = useRef<VListHandle>(null);
  const atBottom = useRef(true);
  const edges = useRef<{ first?: string; last?: string }>({});
  // Older pages are prepended: virtua keeps the visible rows in place when `shift` is set for that render.
  const first = rows[0]?.key;
  const last = rows.at(-1)?.key;
  const shift = edges.current.first !== undefined && first !== edges.current.first && last === edges.current.last;

  useLayoutEffect(() => {
    const appended = last !== edges.current.last;
    edges.current = { first, last };
    if (appended && atBottom.current && rows.length > 0) list.current?.scrollToIndex(rows.length - 1, { align: 'end' });
  }, [first, last, rows.length]);

  // Reading the newest message clears the unread count.
  useEffect(() => {
    if (last && atBottom.current) onReachEnd();
  }, [last, onReachEnd]);

  const onScroll = (offset: number) => {
    const handle = list.current;
    if (!handle) return;
    const bottom = offset + handle.viewportSize >= handle.scrollSize - bottomThreshold;
    if (bottom && !atBottom.current) onReachEnd();
    atBottom.current = bottom;
    if (offset < loadThreshold && history.hasNextPage && !history.isFetchingNextPage) void history.fetchNextPage();
  };

  if (history.isPending) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Spinner />
      </div>
    );
  }
  if (history.isError) {
    return <p className="flex flex-1 items-center justify-center text-sm text-danger">{errorMessage(history.error)}</p>;
  }

  return (
    <VList ref={list} data={rows} shift={shift} onScroll={onScroll} className="flex-1 pb-3">
      {(row) =>
        row.kind === 'time' ? (
          <p key={row.key} className="pt-4 pb-1 text-center text-[11px] text-muted">
            {messageTime(row.time)}
          </p>
        ) : (
          <MessageBubble key={row.key} message={row.message} continued={row.continued} followed={row.followed} />
        )
      }
    </VList>
  );
}
