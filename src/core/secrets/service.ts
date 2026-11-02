import { serviceToken } from '@fraqjs/kernel';

/**
 * Small encrypted values of this account, such as the login session. Defined here and implemented by the host, which
 * has the OS keychain: the account process forwards to the main process's safeStorage.
 */
export abstract class Secrets {
  static readonly token = serviceToken<Secrets>('cecilia/Secrets');

  abstract get(key: string): Promise<string | undefined>;
  abstract set(key: string, value: string): Promise<void>;
  abstract delete(key: string): Promise<void>;
}
