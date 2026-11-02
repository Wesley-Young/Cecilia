import { type ContextOf, createPluginFactory, defineContext } from '@fraqjs/kernel';

const ShellContextRuntime = defineContext<undefined>()
  .subsystems(() => ({}))
  .builtins(() => ({}))
  .build();

/** The context of the main process: windows, the app:// protocol and the account processes. */
export type ShellContext = ContextOf<typeof ShellContextRuntime>;
export const ShellContext = ShellContextRuntime;

export const definePlugin = createPluginFactory<ShellContext>();
