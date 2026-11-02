import { serviceToken } from '@fraqjs/kernel';
import { app, BrowserWindow, nativeTheme, shell } from 'electron';

import { definePlugin } from '#main/context.ts';
import { AccountHost } from '#main/plugins/accounts/service.ts';
import { AppProtocol } from '#main/plugins/protocol/service.ts';

import { join } from 'node:path';

function openExternal(url: string) {
  if (/^https?:\/\//.test(url)) void shell.openExternal(url);
}

/** The main window. There is at most one; it is created on demand and forgotten once closed. */
export class WindowService {
  static readonly token = serviceToken<WindowService>('cecilia/WindowService');
  private readonly origin: string;
  private readonly accounts: AccountHost;
  private window: BrowserWindow | undefined;

  constructor(origin: string, accounts: AccountHost) {
    this.origin = origin;
    this.accounts = accounts;
  }

  /** Bring the main window forward, creating it when there is none. */
  show() {
    if (!this.window) {
      this.window = this.create();
      this.window.once('closed', () => {
        this.window = undefined;
      });
      return;
    }
    if (this.window.isMinimized()) this.window.restore();
    this.window.focus();
  }

  private create() {
    const mac = process.platform === 'darwin';
    const window = new BrowserWindow({
      width: 1080,
      height: 720,
      minWidth: 760,
      minHeight: 520,
      show: false,
      title: app.name,
      backgroundColor: nativeTheme.shouldUseDarkColors ? '#18181b' : '#f5f5f5',
      ...(mac ? { titleBarStyle: 'hiddenInset' as const, trafficLightPosition: { x: 16, y: 18 } } : {}),
      webPreferences: {
        preload: join(import.meta.dirname, '../preload/index.cjs'),
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
        spellcheck: false,
      },
    });

    window.on('page-title-updated', (event) => event.preventDefault());
    // Only app:// pages load inside the window; http(s) links open in the system browser.
    window.webContents.setWindowOpenHandler(({ url }) => {
      openExternal(url);
      return { action: 'deny' };
    });
    window.webContents.on('will-navigate', (event, url) => {
      if (url.startsWith(`${this.origin}/`)) return;
      event.preventDefault();
      openExternal(url);
    });
    // Every load (including a reload) starts a fresh page that needs its own port.
    window.webContents.on('did-finish-load', () => this.accounts.connect(window.webContents));

    window.once('ready-to-show', () => window.show());
    void window.loadURL(`${this.origin}/`);
    return window;
  }

  dispose() {
    this.window?.destroy();
  }
}

export const WindowPlugin = definePlugin({
  name: 'window',
  inject: { protocol: AppProtocol, accounts: AccountHost },
  provides: [WindowService],
  apply(ctx) {
    ctx.provide(WindowService, new WindowService(ctx.protocol.origin, ctx.accounts));
  },
  start(ctx) {
    ctx.resolve(WindowService).show();
  },
});
