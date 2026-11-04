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
  apply(ctx, options: { mediaDir: string }) {
    ctx.kysely.schemas.register(messageSchema);
    const store = new ChatStore(ctx, ctx.kysely, ctx.client, ctx.contacts, options.mediaDir);
    ctx.provide(ChatStore, store);
    ctx.on('client/message', (message) => store.receive(message, true).then(() => undefined));
    ctx.on('client/recall', ({ peer, seq }) => store.recall(peer, seq));
  },
  // The table is there once plugin-kysely has migrated, which its own `start` does before this one runs.
  start(ctx) {
    ctx
      .resolve(ChatStore)
      .pruneImages()
      .catch((error: unknown) => ctx.logger.warn('Cannot delete the images of sent messages', error));
  },
});
