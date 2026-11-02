import { type ContextOf, createPluginFactory, defineContext } from '@fraqjs/kernel';
import mitt, { type Emitter } from 'mitt';

import type { ChatEvents } from './model/events.ts';

interface Subsystems {
  readonly events: Emitter<ChatEvents>;
}

interface Builtins {
  emit<K extends keyof ChatEvents>(type: K, event: ChatEvents[K]): void;
  /** Handlers stop receiving events once the context starts stopping; errors are logged, not rethrown. */
  on<K extends keyof ChatEvents>(type: K, handler: (event: ChatEvents[K]) => void | Promise<void>): () => void;
}

const ChatContextRuntime = defineContext<undefined>()
  .subsystems<Subsystems>(() => ({ events: mitt<ChatEvents>() }))
  .builtins<Builtins>(({ logger, systems, getState }) => ({
    emit: (type, event) => systems.events.emit(type, event),
    on(type, handler) {
      const wrapped = async (event: ChatEvents[typeof type]) => {
        const state = getState();
        if (state === 'stopping' || state === 'stopped') return;
        try {
          await handler(event);
        } catch (error) {
          logger.error(`Error handling event ${type}`, error);
        }
      };
      systems.events.on(type, wrapped);
      return () => systems.events.off(type, wrapped);
    },
  }))
  .build();

/** The context of one account process. Every core plugin runs in it. */
export type ChatContext = ContextOf<typeof ChatContextRuntime>;
export const ChatContext = ChatContextRuntime;

export const definePlugin = createPluginFactory<ChatContext>();
