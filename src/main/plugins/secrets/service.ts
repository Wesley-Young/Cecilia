import { serviceToken } from '@fraqjs/kernel';
import { safeStorage } from 'electron';

import { definePlugin } from '#main/context.ts';

import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Secrets of the account processes, encrypted with the OS keychain through safeStorage, one file per key. Where
 * safeStorage is unavailable (some Linux desktops) the value is stored as is, readable only by the user.
 */
export class SecretStore {
  static readonly token = serviceToken<SecretStore>('cecilia/SecretStore');
  private readonly dir: string;
  private readonly encrypted: boolean;

  constructor(dir: string, encrypted: boolean) {
    this.dir = dir;
    this.encrypted = encrypted;
  }

  private path(key: string) {
    if (!/^[\w-]+$/.test(key)) throw new Error(`Invalid secret key: ${key}`);
    return join(this.dir, `${key}.bin`);
  }

  async get(key: string): Promise<string | undefined> {
    let data: Buffer;
    try {
      data = await readFile(this.path(key));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined;
      throw error;
    }
    if (!this.encrypted) return data.toString('utf8');
    const { result, shouldReEncrypt } = await safeStorage.decryptStringAsync(data);
    if (shouldReEncrypt) await this.set(key, result);
    return result;
  }

  async set(key: string, value: string) {
    await mkdir(this.dir, { recursive: true, mode: 0o700 });
    const data = this.encrypted ? await safeStorage.encryptStringAsync(value) : Buffer.from(value);
    await writeFile(this.path(key), data, { mode: 0o600 });
  }

  async delete(key: string) {
    await rm(this.path(key), { force: true });
  }
}

export const SecretsPlugin = definePlugin({
  name: 'secrets',
  provides: [SecretStore],
  async apply(ctx, options: { dir: string }) {
    const encrypted = await safeStorage.isAsyncEncryptionAvailable();
    if (!encrypted) ctx.logger.warn('safeStorage is unavailable; secrets are not encrypted');
    ctx.provide(SecretStore, new SecretStore(options.dir, encrypted));
  },
});
