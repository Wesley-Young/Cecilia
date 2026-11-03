import type { ChatContext } from '#core/context.ts';
import type { Conversation } from '#core/model/contact.ts';
import type { LoginState } from '#core/model/login.ts';
import type { ChatMessage } from '#core/model/message.ts';
import { publicProcedure } from '../procedures.ts';

/** What the interface follows to stay current without polling. */
export type ChatUpdate =
  | { type: 'login'; state: LoginState }
  | { type: 'message'; message: ChatMessage }
  | { type: 'conversation'; conversation: Conversation }
  /** The friend and group lists were fetched again; contact queries are out of date. */
  | { type: 'contacts' }
  /** A group's member list was fetched again. */
  | { type: 'members'; group: number };

/** Buffers context events into an async iterable until `signal` aborts. */
async function* listen(ctx: ChatContext, signal: AbortSignal | undefined): AsyncGenerator<ChatUpdate> {
  const queue: ChatUpdate[] = [];
  let wake: (() => void) | undefined;
  const push = (update: ChatUpdate) => {
    queue.push(update);
    wake?.();
  };
  const unsubscribe = [
    ctx.on('client/login', (state) => push({ type: 'login', state })),
    ctx.on('store/message', (message) => push({ type: 'message', message })),
    ctx.on('store/conversation', (conversation) => push({ type: 'conversation', conversation })),
    ctx.on('contacts/updated', () => push({ type: 'contacts' })),
    ctx.on('contacts/members', ({ group }) => push({ type: 'members', group })),
  ];
  const stopped = new Promise<void>((resolve) => signal?.addEventListener('abort', () => resolve(), { once: true }));
  try {
    while (!signal?.aborted) {
      const update = queue.shift();
      if (update) {
        yield update;
        continue;
      }
      await Promise.race([new Promise<void>((resolve) => (wake = resolve)), stopped]);
      wake = undefined;
    }
  } finally {
    for (const off of unsubscribe) off();
  }
}

export function updateRoutes(ctx: ChatContext) {
  return {
    updates: publicProcedure.subscription(({ signal }) => listen(ctx, signal)),
  };
}
