import type { inferRouterInputs, inferRouterOutputs } from '@trpc/server';

import type { AccountService } from '#core/account/service.ts';
import type { ChatStore } from '#core/chat-store/service.ts';
import type { ChatClient } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import type { ConversationService } from '#core/conversations/service.ts';
import { router } from './procedures.ts';
import { conversationRoutes } from './routes/conversations.ts';
import { loginRoutes } from './routes/login.ts';
import { messageRoutes } from './routes/messages.ts';
import { settingRoutes } from './routes/settings.ts';
import { updateRoutes } from './routes/updates.ts';

export type { ChatUpdate } from './routes/updates.ts';

/** The services behind the routes; the RPC plugin, which injects them, passes them in. */
export interface RouterServices {
  ctx: ChatContext;
  client: ChatClient;
  store: ChatStore;
  conversations: ConversationService;
  account: AccountService;
}

/** Combines every module's routes; this is the complete RPC surface of an account. */
export function createCoreRouter(services: RouterServices) {
  return router({
    login: loginRoutes(services.client),
    conversations: conversationRoutes(services.conversations),
    messages: messageRoutes(services.store),
    settings: settingRoutes(services.account),
    ...updateRoutes(services.ctx),
  });
}

export type CoreRouter = ReturnType<typeof createCoreRouter>;
export type CoreOutputs = inferRouterOutputs<CoreRouter>;
export type CoreInputs = inferRouterInputs<CoreRouter>;
