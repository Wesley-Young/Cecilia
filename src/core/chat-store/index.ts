import { KyselyService } from '@fraqjs/plugin-kysely';

import { ChatClient } from '#core/client/service.ts';
import { ContactService } from '#core/contacts/service.ts';
import { definePlugin } from '#core/context.ts';
import { messageSchema } from './schema.ts';
import { ChatStore } from './service.ts';

export const ChatStorePlugin = definePlugin({
  name: 'chat-store',
  inject: { kysely: KyselyService, client: ChatClient, contacts: ContactService },
  provides: [ChatStore],
  apply(ctx) {
    ctx.kysely.schemas.register(messageSchema);
    const store = new ChatStore(ctx, ctx.kysely, ctx.client, ctx.contacts);
    ctx.provide(ChatStore, store);
    ctx.on('client/message', (message) => store.receive(message, true).then(() => undefined));
    ctx.on('client/recall', ({ peer, seq }) => store.recall(peer, seq));
  },
});
