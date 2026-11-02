import { z } from 'zod';

import type { ConversationService } from '#core/conversations/service.ts';
import { peerInput, publicProcedure } from '../procedures.ts';

export function conversationRoutes(conversations: ConversationService) {
  return {
    list: publicProcedure.query(() => conversations.list()),
    markRead: publicProcedure
      .input(z.object({ peer: peerInput }))
      .mutation(({ input }) => conversations.markRead(input.peer)),
  };
}
