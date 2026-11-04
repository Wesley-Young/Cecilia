import { z } from 'zod';

import type { ChatStore } from '#core/chat-store/service.ts';
import { publicProcedure } from '../procedures.ts';

export function mediaRoutes(store: ChatStore) {
  return {
    imageUrl: publicProcedure
      .input(z.object({ fileId: z.string().min(1) }))
      .query(({ input }) => store.imageUrl(input.fileId)),
    /** Only addresses `imageUrl` gives or a market face has: the server's, and the images this account holds. */
    download: publicProcedure
      .input(z.object({ src: z.string().regex(/^(https:|data:image\/)/) }))
      .query(({ input }) => store.downloadImage(input.src)),
  };
}
