import type {} from 'electron';

import { definePlugin } from '#core/context.ts';
import { Secrets } from '#core/secrets/service.ts';
import type { AccountMessage, SecretOperation } from './control.ts';

/** Secrets kept by the main process, which owns safeStorage; requests go over the control channel. */
export class HostSecrets extends Secrets {
  private nextId = 1;
  private readonly pending = new Map<
    number,
    { resolve: (value: string | undefined) => void; reject: (error: Error) => void }
  >();

  private request(operation: SecretOperation): Promise<string | undefined> {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      process.parentPort.postMessage({ type: 'secret', id, ...operation } satisfies AccountMessage);
    });
  }

  /** Called with the main process's answer to a request. */
  settle(id: number, value: string | undefined, error: string | undefined) {
    const pending = this.pending.get(id);
    if (!pending) return;
    this.pending.delete(id);
    if (error === undefined) pending.resolve(value);
    else pending.reject(new Error(error));
  }

  get(key: string) {
    return this.request({ op: 'get', key });
  }

  async set(key: string, value: string) {
    await this.request({ op: 'set', key, value });
  }

  async delete(key: string) {
    await this.request({ op: 'delete', key });
  }
}

export const HostSecretsPlugin = definePlugin({
  name: 'host-secrets',
  provides: [Secrets],
  apply(ctx, secrets: HostSecrets) {
    ctx.provide(Secrets, secrets);
  },
});
