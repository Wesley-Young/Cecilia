import type { SelfInfo } from './contact.ts';

export type QrCodeState = 'waiting' | 'scanned' | 'expired' | 'canceled';

/** Where the account is in logging in; drives the login screen and the online status. */
export type LoginState =
  /** Nothing is happening; logging in needs the user to start it. */
  | { kind: 'idle' }
  /** The client cannot log in until it is configured, for example without a sign service. */
  | { kind: 'unconfigured'; message: string }
  | { kind: 'logging-in' }
  /** `image` is a data URL. */
  | { kind: 'qrcode'; image: string; state: QrCodeState }
  | { kind: 'online'; self: SelfInfo }
  /** Lost the connection after being online; `retryAt` is when the next attempt starts, in epoch milliseconds. */
  | { kind: 'offline'; reason: string; retryAt: number | null }
  | { kind: 'failed'; message: string };

export type LoginStateKind = LoginState['kind'];
