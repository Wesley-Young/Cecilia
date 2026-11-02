import { ChatStore } from '#core/chat-store/service.ts';
import { ChatClient } from '#core/client/service.ts';
import { definePlugin } from '#core/context.ts';
import { ConversationService } from '#core/conversations/service.ts';
import { SettingsStore } from '#core/settings/service.ts';
import { AccountService } from './service.ts';

export const AccountPlugin = definePlugin({
  name: 'account',
  inject: { settings: SettingsStore, client: ChatClient, store: ChatStore, conversations: ConversationService },
  provides: [AccountService],
  apply(ctx) {
    ctx.provide(AccountService, new AccountService(ctx.settings, ctx.client, ctx.store, ctx.conversations));
  },
});
