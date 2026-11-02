import { app, dialog } from 'electron';

import { createShell } from './app.ts';
import { applyUserDataPath, loadConfig } from './config.ts';
import type { ShellContext } from './context.ts';
import { logError, openLogFile, writeLog } from './logging.ts';
import { registerAppScheme } from './plugins/protocol/scheme.ts';
import { WindowService } from './plugins/window/service.ts';

// Process-level lifecycle only: the single-instance lock, startup and shutdown. Everything else is a plugin.
app.setName('Cecilia');
if (process.platform === 'win32') app.setAppUserModelId('io.github.wesley-young.cecilia');
applyUserDataPath();
registerAppScheme();

/** The running shell; stopped before quitting. */
let shell: ShellContext | undefined;

function showWindow() {
  shell?.tryResolve(WindowService)?.show();
}

/** Startup cannot continue. */
function fail(error: unknown) {
  dialog.showErrorBox('Cecilia 启动失败', error instanceof Error ? error.message : String(error));
  app.exit(1);
}

async function start() {
  const config = loadConfig();
  openLogFile(config.dirs.logs);
  await app.whenReady();
  const ctx = createShell(config);
  ctx.logBus.on('log', writeLog);
  await ctx.start();
  shell = ctx;
  app.on('activate', showWindow);
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', showWindow);

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  // Stop the shell before quitting: the account process goes offline and closes its database first.
  app.on('before-quit', (event) => {
    if (!shell) return;
    event.preventDefault();
    const ctx = shell;
    shell = undefined;
    ctx
      .stop()
      .catch((error: unknown) => logError('Shutdown failed', error))
      .finally(() => app.quit());
  });

  start().catch((error: unknown) => {
    logError('Startup failed', error);
    fail(error);
  });
}
