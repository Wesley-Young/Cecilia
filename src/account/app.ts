import { KyselyPlugin } from '@fraqjs/plugin-kysely';

import { AcidifyPlugin } from '#core/acidify/index.ts';
import { ChatStorePlugin } from '#core/chat-store/index.ts';
import { ContactsPlugin } from '#core/contacts/index.ts';
import { ChatContext } from '#core/context.ts';
import { ConversationsPlugin } from '#core/conversations/index.ts';
import { DatabasePlugin } from '#core/database/index.ts';
import { MockPlugin } from '#core/mock/index.ts';
import type { AccountConfig } from './control.ts';
import { RpcPlugin } from './rpc/index.ts';
import { type HostSecrets, HostSecretsPlugin } from './secrets.ts';

import { join } from 'node:path';

/**
 * The composition root of an account process: every plugin gets exactly the configuration it needs here. The kernel
 * orders them by what they inject, so the order below does not matter.
 */
export function createAccount(config: AccountConfig, secrets: HostSecrets): ChatContext {
  const ctx = ChatContext.create(undefined);
  ctx.install(HostSecretsPlugin, secrets);
  ctx.install(KyselyPlugin, {
    sqliteUrl: join(config.profileDir, 'cecilia.db'),
    autoVacuum: { enabled: false },
  });
  ctx.install(DatabasePlugin);
  if (config.mock) {
    ctx.install(MockPlugin);
  } else {
    ctx.install(AcidifyPlugin, { uin: config.uin, protocol: config.protocol, sign: config.sign });
  }
  ctx.install(ContactsPlugin);
  ctx.install(ChatStorePlugin);
  ctx.install(ConversationsPlugin);
  ctx.install(RpcPlugin);
  return ctx;
}
