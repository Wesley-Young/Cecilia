import { type Logger, serviceToken } from '@fraqjs/kernel';
import type { MessagePortMain } from 'electron';

import { ChatStore } from '#core/chat-store/service.ts';
import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { ConversationService } from '#core/conversations/service.ts';
import { servePort } from './port-server.ts';
import { type CoreRouter, createCoreRouter } from './router.ts';

/** Accepts RPC connections, one MessagePort per window. */
export class RpcServer {
  static readonly token = serviceToken<RpcServer>('cecilia/RpcServer');
  private readonly connections = new Set<() => void>();
  private readonly router: CoreRouter;
  private readonly logger: Logger;

  constructor(router: CoreRouter, logger: Logger) {
    this.router = router;
    this.logger = logger;
  }

  connect(port: MessagePortMain) {
    const close = servePort(port, {
      router: this.router,
      createContext: () => ({ logger: this.logger }),
      onError: (error, path) => this.logger.error(`RPC ${path} failed`, error),
    });
    this.connections.add(close);
    port.on('close', () => this.connections.delete(close));
  }

  dispose() {
    for (const close of this.connections) close();
    this.connections.clear();
  }
}

export const RpcPlugin = definePlugin({
  name: 'rpc',
  inject: { client: ChatClient, store: ChatStore, conversations: ConversationService },
  provides: [RpcServer],
  apply(ctx) {
    const router = createCoreRouter({
      ctx,
      client: ctx.client,
      store: ctx.store,
      conversations: ctx.conversations,
    });
    ctx.provide(RpcServer, new RpcServer(router, ctx.logger));
  },
});
