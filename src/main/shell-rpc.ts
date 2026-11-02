import { type inferRouterOutputs, initTRPC } from '@trpc/server';

const t = initTRPC.create({ isServer: true });

export interface ShellInfo {
  name: string;
  version: string;
  platform: NodeJS.Platform;
  /** Running against the mock client instead of a real account. */
  mock: boolean;
}

/** The main process's own RPC surface, served over app://. Account features live in the core router instead. */
export function createShellRouter(info: ShellInfo) {
  return t.router({
    system: {
      info: t.procedure.query(() => info),
    },
  });
}

export type ShellRouter = ReturnType<typeof createShellRouter>;
export type ShellOutputs = inferRouterOutputs<ShellRouter>;
