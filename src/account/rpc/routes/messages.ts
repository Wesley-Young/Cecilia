import { z } from 'zod';

import type { ChatStore } from '#core/chat-store/service.ts';
import { imageFormats, maxImageSize, type OutgoingSegment } from '#core/model/segment.ts';
import { peerInput, publicProcedure } from '../procedures.ts';

/** What the composer sends, in order: text and the images between it. */
const contentInput = z
  .array(
    z.discriminatedUnion('type', [
      z.object({ type: z.literal('text'), text: z.string().min(1) }),
      z.object({
        type: z.literal('image'),
        data: z.instanceof(Uint8Array).refine((data) => data.byteLength <= maxImageSize, '图片不能超过 30 MB'),
        format: z.enum(imageFormats),
        width: z.number().int().positive(),
        height: z.number().int().positive(),
      }),
    ]),
  )
  .refine((segments) => segments.some((segment) => segment.type === 'image' || segment.text.trim()), '消息不能为空')
  .refine(
    (segments) =>
      segments.reduce((length, segment) => length + (segment.type === 'text' ? segment.text.length : 0), 0) <= 5000,
    '消息太长了',
  );

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
          content: contentInput,
          /** The seq of the message replied to. */
          reply: z.number().int().positive().nullish(),
        }),
      )
      .mutation(({ input }) => {
        const content: OutgoingSegment[] = input.content;
        // A reply always comes first.
        return store.send(
          input.peer,
          input.reply == null ? content : [{ type: 'reply', seq: input.reply }, ...content],
        );
      }),
  };
}
