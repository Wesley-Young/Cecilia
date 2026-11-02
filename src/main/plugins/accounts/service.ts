import { type Logger, serviceToken } from '@fraqjs/kernel';
import { MessageChannelMain, type UtilityProcess, utilityProcess, type WebContents } from 'electron';

import type { AccountConfig, AccountMessage, HostMessage } from '#account/control.ts';
import { definePlugin } from '#main/context.ts';
import { writeForwardedLog } from '#main/logging.ts';
import { SecretStore } from '#main/plugins/secrets/service.ts';

/** The channel a window's preload listens on for the port to its account. */
export const corePortChannel = 'cecilia:core-port';

/** Delays before restarting an account process that exited on its own; the last one repeats. */
const restartDelays = [1_000, 5_000, 30_000];
/** How long an account may take to go offline and close its database before it is killed. */
const stopTimeout = 5_000;

export interface AccountHostOptions {
  entry: string;
  config: AccountConfig;
}

/**
 * Runs the account in a utilityProcess, restarts it when it crashes, and connects windows to it: each window gets a
 * MessagePort of its own, again after every restart.
 */
export class AccountHost {
  static readonly token = serviceToken<AccountHost>('cecilia/AccountHost');
  private readonly options: AccountHostOptions;
  private readonly secrets: SecretStore;
  private readonly logger: Logger;
  private child: UtilityProcess | undefined;
  private readonly windows = new Set<WebContents>();
  private restarts = 0;
  private restartTimer: NodeJS.Timeout | undefined;
  private stopping = false;

  constructor(options: AccountHostOptions, secrets: SecretStore, logger: Logger) {
    this.options = options;
    this.secrets = secrets;
    this.logger = logger;
  }

  start() {
    const child = utilityProcess.fork(this.options.entry, [], { serviceName: 'Cecilia Account', stdio: 'inherit' });
    this.child = child;
    child.on('message', (message: AccountMessage) => this.handle(child, message));
    child.once('exit', (code) => {
      if (this.child === child) this.child = undefined;
      if (this.stopping) return;
      const delay = restartDelays[Math.min(this.restarts, restartDelays.length - 1)] ?? 30_000;
      this.restarts += 1;
      this.logger.warn(`Account process exited with code ${code}; restarting in ${delay / 1000}s`);
      this.restartTimer = setTimeout(() => this.start(), delay);
    });
    child.postMessage({ type: 'init', config: this.options.config } satisfies HostMessage);
    for (const window of this.windows) this.connectTo(window);
  }

  private handle(child: UtilityProcess, message: AccountMessage) {
    switch (message.type) {
      case 'ready':
        this.restarts = 0;
        this.logger.info('Account process is ready');
        break;
      case 'failed':
        this.logger.error(`Account process failed: ${message.error}`);
        break;
      case 'log':
        writeForwardedLog('account', message.log);
        break;
      case 'secret': {
        const reply = (result: { value?: string | undefined; error?: string }) =>
          child.postMessage({ type: 'secret-result', id: message.id, ...result } satisfies HostMessage);
        const run =
          message.op === 'get'
            ? this.secrets.get(message.key)
            : message.op === 'set'
              ? this.secrets.set(message.key, message.value).then(() => undefined)
              : this.secrets.delete(message.key).then(() => undefined);
        run
          .then((value) => reply({ value }))
          .catch((error: unknown) => {
            this.logger.error(`Secret ${message.op} ${message.key} failed`, error);
            reply({ error: error instanceof Error ? error.message : String(error) });
          });
        break;
      }
      case 'stopped':
        break;
    }
  }

  /** Gives the window a port to the account, now and after every restart, until the window is destroyed. */
  connect(window: WebContents) {
    if (!this.windows.has(window)) {
      this.windows.add(window);
      window.once('destroyed', () => this.windows.delete(window));
    }
    this.connectTo(window);
  }

  private connectTo(window: WebContents) {
    if (!this.child || window.isDestroyed()) return;
    const { port1, port2 } = new MessageChannelMain();
    this.child.postMessage({ type: 'connect' } satisfies HostMessage, [port1]);
    window.postMessage(corePortChannel, null, [port2]);
  }

  async dispose() {
    this.stopping = true;
    clearTimeout(this.restartTimer);
    const child = this.child;
    if (!child) return;
    await new Promise<void>((resolve) => {
      const timer = setTimeout(() => {
        this.logger.warn('Account process did not stop in time; killing it');
        child.kill();
      }, stopTimeout);
      child.once('exit', () => {
        clearTimeout(timer);
        resolve();
      });
      child.postMessage({ type: 'stop' } satisfies HostMessage);
    });
  }
}

export const AccountsPlugin = definePlugin({
  name: 'accounts',
  inject: { secrets: SecretStore },
  provides: [AccountHost],
  apply(ctx, options: AccountHostOptions) {
    ctx.provide(AccountHost, new AccountHost(options, ctx.secrets, ctx.logger));
  },
  start(ctx) {
    ctx.resolve(AccountHost).start();
  },
});
