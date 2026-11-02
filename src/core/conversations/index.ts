import { KyselyService } from '@fraqjs/plugin-kysely';

import { ChatClient } from '#core/client/service.ts';
import { ContactService } from '#core/contacts/service.ts';
import { definePlugin } from '#core/context.ts';
import { conversationSchema } from './schema.ts';
import { ConversationService } from './service.ts';

export const ConversationsPlugin = definePlugin({
  name: 'conversations',
  inject: { kysely: KyselyService, client: ChatClient, contacts: ContactService },
  provides: [ConversationService],
  apply(ctx) {
    ctx.kysely.schemas.register(conversationSchema);
    const conversations = new ConversationService(ctx, ctx.kysely, ctx.client, ctx.contacts);
    ctx.provide(ConversationService, conversations);
    ctx.on('store/message', (message) => conversations.update(message));
    ctx.on('contacts/updated', () => conversations.rename());
    ctx.on('client/login', async (state) => {
      if (state.kind === 'online') await conversations.syncPinned();
    });
  },
});
