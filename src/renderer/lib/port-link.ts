import { TRPCClientError, type TRPCLink } from '@trpc/client';
import type { AnyRouter } from '@trpc/server';
import { observable } from '@trpc/server/observable';

import type { PortClientMessage, PortProcedureType, PortServerMessage } from '#account/rpc/port-protocol.ts';

interface Call {
  type: PortProcedureType;
  path: string;
  input: unknown;
  /** Whether the request went out on the current port. */
  sent: boolean;
  onData: (data: unknown) => void;
  onDone: () => void;
  onError: (error: unknown) => void;
}

/**
 * The page's connection to its account process. The port arrives from the preload after load, and a new one
 * replaces it whenever the account process restarts: queries and mutations in flight on the old port fail,
 * subscriptions are sent again on the new one.
 */
export class PortConnection {
  private port: MessagePort | undefined;
  private nextId = 1;
  private readonly calls = new Map<number, Call>();

  attach(port: MessagePort) {
    this.port?.close();
    this.port = port;
    port.onmessage = (event: MessageEvent<PortServerMessage>) => this.receive(event.data);
    for (const [id, call] of this.calls) {
      if (call.sent && call.type !== 'subscription') {
        this.calls.delete(id);
        call.onError(new Error('与账号的连接已重置，请重试'));
        continue;
      }
      this.send(id, call);
    }
  }

  call(call: Omit<Call, 'sent'>): number {
    const id = this.nextId++;
    const entry = { ...call, sent: false };
    this.calls.set(id, entry);
    this.send(id, entry);
    return id;
  }

  cancel(id: number) {
    const call = this.calls.get(id);
    if (!call) return;
    this.calls.delete(id);
    if (call.type === 'subscription' && call.sent) this.post({ kind: 'stop', id });
  }

  private send(id: number, call: Call) {
    if (!this.port) return;
    call.sent = true;
    this.post({ kind: 'request', id, type: call.type, path: call.path, input: call.input });
  }

  private post(message: PortClientMessage) {
    this.port?.postMessage(message);
  }

  private receive(message: PortServerMessage) {
    const call = this.calls.get(message.id);
    if (!call) return;
    switch (message.kind) {
      case 'result':
        this.calls.delete(message.id);
        call.onData(message.data);
        call.onDone();
        break;
      case 'data':
        call.onData(message.data);
        break;
      case 'stopped':
        this.calls.delete(message.id);
        call.onDone();
        break;
      case 'error':
        this.calls.delete(message.id);
        call.onError(message.error);
        break;
    }
  }
}

/** A terminating tRPC link that sends every operation over the connection. */
export function portLink<TRouter extends AnyRouter>(connection: PortConnection): TRPCLink<TRouter> {
  return () =>
    ({ op }) =>
      observable((observer) => {
        const id = connection.call({
          type: op.type,
          path: op.path,
          input: op.input,
          onData: (data) => observer.next({ result: { type: 'data', data } }),
          onDone: () => observer.complete(),
          onError: (error) =>
            observer.error(
              error instanceof Error
                ? TRPCClientError.from(error)
                : TRPCClientError.from({ error } as Parameters<typeof TRPCClientError.from>[0]),
            ),
        });
        return () => connection.cancel(id);
      });
}
