import type { LogMessage } from '@fraqjs/kernel';
import type { MessagePortMain } from 'electron';

import { createAccount } from './app.ts';
import type { AccountMessage, HostMessage } from './control.ts';
import { RpcServer } from './rpc/index.ts';
import { HostSecrets } from './secrets.ts';

import { mkdirSync } from 'node:fs';
import { inspect } from 'node:util';

// Process-level lifecycle of an account: wait for `init`, start the context, hand RPC ports to it, stop on request.
// Everything else is a plugin.

const parent = process.parentPort;
const post = (message: AccountMessage) => parent.postMessage(message);
const describe = (value: unknown) =>
  value instanceof Error ? (value.stack ?? value.message) : inspect(value, { depth: 4, breakLength: Infinity });

const secrets = new HostSecrets();
let rpc: RpcServer | undefined;
/** Windows that connect before the context has started wait here. */
const waitingPorts: MessagePortMain[] = [];
let stopping = false;
let stop: (() => Promise<void>) | undefined;

function forwardLog(message: LogMessage) {
  post({
    type: 'log',
    log: {
      level: message.level,
      module: message.module,
      message: message.message,
      error: message.error === undefined ? undefined : describe(message.error),
      time: message.time,
    },
  });
}

async function start(message: Extract<HostMessage, { type: 'init' }>) {
  mkdirSync(message.config.profileDir, { recursive: true });
  const ctx = createAccount(message.config, secrets);
  ctx.logBus.on('log', forwardLog);
  stop = () => ctx.stop();
  await ctx.start();
  rpc = ctx.resolve(RpcServer);
  for (const port of waitingPorts.splice(0)) rpc.connect(port);
  post({ type: 'ready' });
}

function shutdown() {
  if (stopping) return;
  stopping = true;
  (stop?.() ?? Promise.resolve())
    .catch((error: unknown) => post({ type: 'failed', error: describe(error) }))
    .finally(() => {
      post({ type: 'stopped' });
      process.exit(0);
    });
}

parent.on('message', (event: { data: HostMessage; ports: MessagePortMain[] }) => {
  const message = event.data;
  switch (message.type) {
    case 'init':
      start(message).catch((error: unknown) => {
        post({ type: 'failed', error: describe(error) });
        process.exit(1);
      });
      break;
    case 'connect':
      for (const port of event.ports) {
        if (rpc) rpc.connect(port);
        else waitingPorts.push(port);
      }
      break;
    case 'secret-result':
      secrets.settle(message.id, message.value, message.error);
      break;
    case 'stop':
      shutdown();
      break;
  }
});
