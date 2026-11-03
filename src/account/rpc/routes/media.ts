import { z } from 'zod';

import type { ChatClient } from '#core/client/service.ts';
import { publicProcedure } from '../procedures.ts';

export function mediaRoutes(client: ChatClient) {
  return {
    imageUrl: publicProcedure
      .input(z.object({ fileId: z.string().min(1) }))
      .query(({ input }) => client.getImageUrl(input.fileId)),
  };
}
