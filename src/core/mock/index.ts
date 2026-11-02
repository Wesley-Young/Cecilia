import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { MockClient } from './service.ts';

/** Provides a made-up ChatClient for development. Installed instead of the acidify plugin. */
export const MockPlugin = definePlugin({
  name: 'mock',
  provides: [ChatClient],
  apply(ctx) {
    ctx.provide(ChatClient, new MockClient(ctx));
  },
  start(ctx) {
    const client = ctx.resolve(ChatClient) as MockClient;
    client.startChatter();
    void client.login();
  },
});
