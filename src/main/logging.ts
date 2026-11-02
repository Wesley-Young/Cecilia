import type { LogMessage } from '@fraqjs/kernel';
import log from 'electron-log/node';

import type { ForwardedLog } from '#account/control.ts';

import { join } from 'node:path';
import { inspect } from 'node:util';

const describe = (value: unknown) =>
  value instanceof Error ? (value.stack ?? value.message) : inspect(value, { depth: 4, breakLength: Infinity });

const line = (level: string, module: string, text: string, time = Date.now()) =>
  `${new Date(time).toISOString()} ${level.toUpperCase()} [${module}] ${text}`;

// electron-log only carries lines formatted here: to the console, and to a file once `openLogFile` names one. The
// Node entry is used because the Electron one registers IPC handlers.
log.transports.console.format = '{text}';
log.transports.file.format = '{text}';
log.transports.file.level = false;

/**
 * Also write logs to `cecilia.log` in `dir`; past 5 MB it becomes `cecilia.old.log`. Only the instance holding the
 * single-instance lock may call this, so two processes never rotate the same file.
 */
export function openLogFile(dir: string) {
  const file = log.transports.file;
  file.resolvePathFn = () => join(dir, 'cecilia.log');
  file.maxSize = 5 * 1024 * 1024;
  file.writeOptions = { ...file.writeOptions, mode: 0o600 };
  file.level = 'debug';
}

/** Writes kernel log messages of the main process. */
export function writeLog(message: LogMessage) {
  const text = message.error === undefined ? message.message : `${message.message}: ${describe(message.error)}`;
  log[message.level](line(message.level, message.module, text, message.time));
}

/** Writes a log line an account process forwarded; its errors arrive already formatted. */
export function writeForwardedLog(prefix: string, message: ForwardedLog) {
  const text = message.error === undefined ? message.message : `${message.message}: ${message.error}`;
  log[message.level](line(message.level, `${prefix}/${message.module}`, text, message.time));
}

/** For the process itself, before or outside the plugin tree. */
export function logError(message: string, error?: unknown) {
  log.error(line('error', 'cecilia', error === undefined ? message : `${message}: ${describe(error)}`));
}
