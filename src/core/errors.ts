export type AppErrorKind = 'bad_request' | 'not_found' | 'conflict' | 'precondition_failed' | 'bad_gateway';

/** An error whose message is meant for the user. Anything else is reported as a generic failure. */
export class AppError extends Error {
  readonly kind: AppErrorKind;

  constructor(kind: AppErrorKind, message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'AppError';
    this.kind = kind;
  }
}
