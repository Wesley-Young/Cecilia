import type { AnyRouter } from '@trpc/server';
import {
  callProcedure,
  getErrorShape,
  getTRPCErrorFromUnknown,
  isAsyncIterable,
} from '@trpc/server/unstable-core-do-not-import';
import type { MessagePortMain } from 'electron';

import type { PortClientMessage, PortServerMessage } from './port-protocol.ts';

export interface PortServerOptions<TContext> {
  router: AnyRouter;
  createContext: () => TContext;
  onError?: (error: unknown, path: string) => void;
}

/**
 * Serves tRPC over one MessagePort. Values pass by structured clone, so bigint, Uint8Array and Date arrive as they
 * are, without a transformer. Returns a function that closes the port and ends its subscriptions.
 */
export function servePort<TContext>(port: MessagePortMain, options: PortServerOptions<TContext>): () => void {
  const { router } = options;
  const subscriptions = new Map<number, AbortController>();
  const send = (message: PortServerMessage) => port.postMessage(message);

  const handle = async (message: PortClientMessage) => {
    if (message.kind === 'stop') {
      subscriptions.get(message.id)?.abort();
      subscriptions.delete(message.id);
      return;
    }
    const { id, type, path, input } = message;
    const abort = new AbortController();
    if (type === 'subscription') subscriptions.set(id, abort);
    const ctx = options.createContext();
    try {
      const result = await callProcedure({
        router,
        path,
        type,
        ctx,
        getRawInput: async () => input,
        signal: abort.signal,
        batchIndex: 0,
      });
      if (type !== 'subscription') {
        send({ kind: 'result', id, data: result });
        return;
      }
      if (!isAsyncIterable(result)) throw new Error(`Subscription ${path} did not return an async iterable`);
      for await (const item of result) {
        if (abort.signal.aborted) break;
        send({ kind: 'data', id, data: item });
      }
      if (!abort.signal.aborted) send({ kind: 'stopped', id });
    } catch (cause) {
      if (abort.signal.aborted) return;
      const error = getTRPCErrorFromUnknown(cause);
      options.onError?.(error, path);
      send({
        kind: 'error',
        id,
        error: getErrorShape({ config: router._def._config, error, type, path, input, ctx }),
      });
    } finally {
      if (type === 'subscription') subscriptions.delete(id);
    }
  };

  const close = () => {
    for (const abort of subscriptions.values()) abort.abort();
    subscriptions.clear();
  };

  port.on('message', (event) => void handle(event.data as PortClientMessage));
  port.on('close', close);
  port.start();
  return () => {
    close();
    port.close();
  };
}
