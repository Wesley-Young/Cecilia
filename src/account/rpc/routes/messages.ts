import { z } from 'zod';

import type { ChatStore } from '#core/chat-store/service.ts';
import type { OutgoingSegment } from '#core/model/segment.ts';
import { peerInput, publicProcedure } from '../procedures.ts';

export function messageRoutes(store: ChatStore) {
  return {
    /** Pages run from the newest backwards; `cursor` is the seq the next older page ends before. */
    history: publicProcedure
      .input(z.object({ peer: peerInput, cursor: z.number().int().positive().nullish() }))
      .query(async ({ input }) => {
        const page = await store.history(input.peer, input.cursor ?? undefined);
        const oldest = page.messages.find((message) => message.seq !== null)?.seq;
        return { ...page, nextCursor: page.hasMore && oldest != null ? oldest : null };
      }),
    send: publicProcedure
      .input(
        z.object({
          peer: peerInput,
          text: z.string().trim().min(1, '消息不能为空').max(5000, '消息太长了'),
          /** The seq of the message replied to. */
          reply: z.number().int().positive().nullish(),
        }),
      )
      .mutation(({ input }) => {
        const text: OutgoingSegment = { type: 'text', text: input.text };
        // A reply always comes first.
        return store.send(input.peer, input.reply == null ? [text] : [{ type: 'reply', seq: input.reply }, text]);
      }),
  };
}
