import { app } from 'electron';

import type { AccountConfig } from '#account/control.ts';

import { join } from 'node:path';

export interface AppConfig {
  version: string;
  /** Where Cecilia keeps its files. */
  dirs: { data: string; logs: string };
  /** Built renderer files, served in production. */
  rendererDir: string;
  /** Vite dev server URL; set by electron-vite in development only. */
  rendererDevUrl: string | undefined;
  /** The built account process entry. */
  accountEntry: string;
  account: Omit<AccountConfig, 'profileDir'>;
}

const env = process.env;

/**
 * With CECILIA_HOME, everything (including Chromium's state) lives there, so a development instance does not touch
 * the installed app. Must run before anything touches userData.
 */
export function applyUserDataPath() {
  if (env.CECILIA_HOME) app.setPath('userData', join(env.CECILIA_HOME, 'electron'));
}

function parseUin(value: string | undefined): number | undefined {
  const uin = Number(value);
  return Number.isSafeInteger(uin) && uin > 0 ? uin : undefined;
}

/**
 * Read once at startup; `app.ts` hands each plugin its part, and plugins never read `process.env` directly. Until
 * there is a settings screen, the account is configured with CECILIA_* environment variables (see README).
 */
export function loadConfig(): AppConfig {
  const base = env.CECILIA_HOME ?? app.getPath('userData');
  const mock = env.CECILIA_MOCK === '1';
  return {
    version: app.getVersion(),
    dirs: { data: join(base, 'data'), logs: join(base, 'logs') },
    rendererDir: join(import.meta.dirname, '../renderer'),
    rendererDevUrl: app.isPackaged ? undefined : env.ELECTRON_RENDERER_URL,
    accountEntry: join(import.meta.dirname, 'account.js'),
    account: {
      mock,
      uin: mock ? 10000 : parseUin(env.CECILIA_UIN),
      protocol: env.CECILIA_PROTOCOL === 'Mac' ? 'Mac' : 'Linux',
      sign: env.CECILIA_SIGN_URL ? { url: env.CECILIA_SIGN_URL, token: env.CECILIA_SIGN_TOKEN ?? '' } : undefined,
    },
  };
}
