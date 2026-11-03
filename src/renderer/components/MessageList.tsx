import { Spinner } from '@heroui/react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { VList, type VListHandle } from 'virtua';

import { type ChatMessage, recallNotice } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import { ImageViewerProvider } from '@/components/ImageViewer';
import MessageBubble, { enterClass } from '@/components/MessageBubble';
import { historyInput } from '@/lib/cache';
import { messageTime } from '@/lib/format';
import { core, errorMessage } from '@/lib/rpc';

/** A time separator appears when this much passes between two messages. */
const separatorGap = 5 * 60;
/** Load older messages when the top is this close. */
const loadThreshold = 400;
/** Counts as "at the bottom", where new messages keep the view pinned to the end. */
const bottomThreshold = 80;
/** A reply jumps to the quoted message only when it is loaded and at most this many messages back. */
const jumpLimit = 50;
/** How long the message jumped to flashes, in ms; the length of `animate-flash`. */
const highlightDuration = 1500;
/** How long a new message takes to rise into place, in ms; the length of `animate-message-enter`. */
const enterDuration = 180;

interface MessageRow {
  kind: 'message';
  key: string;
  message: ChatMessage;
  /** The previous message is from the same sender. */
  continued: boolean;
  /** The next message is from the same sender. */
  followed: boolean;
  /** The row of the quoted message, when the reply can jump to it. */
  replyTarget: number | undefined;
}

type Row = { kind: 'time'; key: string; time: number } | MessageRow;

function toRows(messages: ChatMessage[]): Row[] {
  const rows: Row[] = [];
  const bySeq = new Map<number, { row: number; index: number }>();
  let previous: MessageRow | undefined;
  for (const [index, message] of messages.entries()) {
    const gap = !previous || message.time - previous.message.time > separatorGap;
    if (gap) rows.push({ kind: 'time', key: `time:${message.id}`, time: message.time });
    const continued =
      !gap &&
      previous?.message.sender.uin === message.sender.uin &&
      recallNotice(previous.message) === undefined &&
      recallNotice(message) === undefined;
    if (continued && previous) previous.followed = true;
    const reply = message.segments.find((segment) => segment.type === 'reply');
    const target = reply && bySeq.get(reply.seq);
    const replyTarget = target && index - target.index <= jumpLimit ? target.row : undefined;
    previous = { kind: 'message', key: `message:${message.id}`, message, continued, followed: false, replyTarget };
    if (message.seq !== null) bySeq.set(message.seq, { row: rows.length, index });
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
  // An empty conversation counts as loaded too, so its first message rises in.
  const loaded = history.data !== undefined;
  /** The rows of the last render; undefined until the first page has loaded. */
  const known = useRef<Set<string>>(undefined);
  const [entering, setEntering] = useState<ReadonlySet<string>>(new Set());
  const enterTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(enterTimer.current), []);
  // Older pages are prepended: virtua keeps the visible rows in place when `shift` is set for that render.
  const first = rows[0]?.key;
  const lastRow = rows.at(-1);
  const last = lastRow?.key;
  const shift = edges.current.first !== undefined && first !== edges.current.first && last === edges.current.last;
  // A message this window just started sending takes the view to the end wherever the reader was. One that failed at
  // once counts too: it may arrive already failed.
  const sending =
    lastRow?.kind === 'message' &&
    lastRow.message.outgoing &&
    (lastRow.message.status === 'sending' || lastRow.message.status === 'failed');

  // Rows that arrive after the first page rise into place; the first page and older pages just appear. Set before
  // paint, so a new row never shows without its animation.
  useLayoutEffect(() => {
    if (!loaded) return;
    const before = known.current;
    known.current = new Set(rows.map((row) => row.key));
    if (!before || shift) return;
    const arrived = rows.filter((row) => !before.has(row.key)).map((row) => row.key);
    if (arrived.length === 0) return;
    setEntering(new Set(arrived));
    clearTimeout(enterTimer.current);
    enterTimer.current = setTimeout(() => setEntering(new Set()), enterDuration);
  }, [loaded, rows, shift]);

  useLayoutEffect(() => {
    const appended = last !== edges.current.last;
    edges.current = { first, last };
    if (!appended || rows.length === 0 || !(atBottom.current || sending)) return;
    atBottom.current = true;
    list.current?.scrollToIndex(rows.length - 1, { align: 'end' });
  }, [first, last, rows.length, sending]);

  // Reading the newest message clears the unread count.
  useEffect(() => {
    if (last && atBottom.current) onReachEnd();
  }, [last, onReachEnd]);

  const [highlighted, setHighlighted] = useState<number>();
  const highlightTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(highlightTimer.current), []);

  const jumpToReply = (from: MessageRow) => {
    const index = from.replyTarget;
    const target = index === undefined ? undefined : rows[index];
    if (index === undefined || target?.kind !== 'message') return;
    list.current?.scrollToIndex(index, { align: 'center', smooth: true });
    setHighlighted(target.message.id);
    clearTimeout(highlightTimer.current);
    highlightTimer.current = setTimeout(() => setHighlighted(undefined), highlightDuration);
  };

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
    <ImageViewerProvider>
      <VList ref={list} data={rows} shift={shift} onScroll={onScroll} className="flex-1 pb-3">
        {(row) =>
          row.kind === 'time' ? (
            <p
              key={row.key}
              className={`pt-4 pb-1 text-center text-[11px] text-muted ${entering.has(row.key) ? enterClass : ''}`}
            >
              {messageTime(row.time)}
            </p>
          ) : (
            <MessageBubble
              key={row.key}
              message={row.message}
              continued={row.continued}
              followed={row.followed}
              highlighted={row.message.id === highlighted}
              entering={entering.has(row.key)}
              onJumpToReply={row.replyTarget === undefined ? undefined : () => jumpToReply(row)}
            />
          )
        }
      </VList>
    </ImageViewerProvider>
  );
}
