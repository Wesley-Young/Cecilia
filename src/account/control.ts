// The control channel between the main process and an account process (`process.parentPort`). RPC from windows
// does not go through here: each window gets its own MessagePort, handed over with a `connect` message.

/** How the main process starts an account. Everything about logging in is in the account's own settings. */
export interface AccountConfig {
  /** Holds the database and media cache of this account. */
  profileDir: string;
  /** Use the mock client instead of logging in. */
  mock: boolean;
}

export type SecretOperation =
  | { op: 'get'; key: string }
  | { op: 'set'; key: string; value: string }
  | { op: 'delete'; key: string };

export interface ForwardedLog {
  level: 'debug' | 'info' | 'warn' | 'error';
  module: string;
  message: string;
  /** The error's stack, already formatted: errors do not survive the channel as objects. */
  error?: string;
  time: number;
}

/** Main process → account process. */
export type HostMessage =
  | { type: 'init'; config: AccountConfig }
  /** Carries a MessagePort for a new RPC connection. */
  | { type: 'connect' }
  | { type: 'stop' }
  | { type: 'secret-result'; id: number; value?: string | undefined; error?: string };

/** Account process → main process. */
export type AccountMessage =
  | { type: 'ready' }
  | { type: 'failed'; error: string }
  | { type: 'log'; log: ForwardedLog }
  | ({ type: 'secret'; id: number } & SecretOperation)
  | { type: 'stopped' };
