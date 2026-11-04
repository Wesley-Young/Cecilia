import { z } from 'zod';

import type { ChatStore } from '#core/chat-store/service.ts';
import { publicProcedure } from '../procedures.ts';

export function mediaRoutes(store: ChatStore) {
  return {
    imageUrl: publicProcedure
      .input(z.object({ fileId: z.string().min(1) }))
      .query(({ input }) => store.imageUrl(input.fileId)),
  };
}
