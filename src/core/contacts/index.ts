import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { ContactService } from './service.ts';

export const ContactsPlugin = definePlugin({
  name: 'contacts',
  inject: { client: ChatClient },
  provides: [ContactService],
  apply(ctx) {
    const contacts = new ContactService(ctx, ctx.client);
    ctx.provide(ContactService, contacts);
    ctx.on('client/login', async (state) => {
      if (state.kind === 'online') await contacts.refresh();
    });
  },
});
