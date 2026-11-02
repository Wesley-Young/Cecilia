import type { AppConfig } from './config.ts';
import { ShellContext } from './context.ts';
import { AccountsPlugin } from './plugins/accounts/service.ts';
import { ProtocolPlugin } from './plugins/protocol/service.ts';
import { SecretsPlugin } from './plugins/secrets/service.ts';
import { WindowPlugin } from './plugins/window/service.ts';

import { join } from 'node:path';

/**
 * The composition root of the main process: every plugin gets exactly the configuration it needs here. The kernel
 * orders them by what they inject, so the order below does not matter.
 */
export function createShell(config: AppConfig): ShellContext {
  // One profile per account, so switching CECILIA_UIN never mixes two accounts' data.
  const profileDir = join(config.dirs.data, 'profiles', config.account.mock ? 'mock' : String(config.account.uin ?? 0));
  const ctx = ShellContext.create(undefined);
  ctx.install(SecretsPlugin, { dir: join(profileDir, 'secrets') });
  ctx.install(AccountsPlugin, { entry: config.accountEntry, config: { ...config.account, profileDir } });
  ctx.install(ProtocolPlugin, {
    rendererDir: config.rendererDir,
    rendererDevUrl: config.rendererDevUrl,
    info: { name: 'Cecilia', version: config.version, platform: process.platform, mock: config.account.mock },
  });
  ctx.install(WindowPlugin);
  return ctx;
}
