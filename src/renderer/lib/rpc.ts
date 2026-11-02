import { QueryClient } from '@tanstack/react-query';
import { createTRPCClient, httpLink, TRPCClientError } from '@trpc/client';
import { createTRPCOptionsProxy } from '@trpc/tanstack-react-query';

// The only renderer file allowed to reach into other processes, and only for types.
import type { CoreRouter } from '#account/rpc/router.ts';
import type { ShellRouter } from '#main/shell-rpc.ts';
import { PortConnection, portLink } from './port-link.ts';

export type { ChatUpdate, CoreInputs, CoreOutputs } from '#account/rpc/router.ts';

/** The account process, over the MessagePort the preload hands over (again after every restart). */
const connection = new PortConnection();
window.addEventListener('message', (event) => {
  const port = event.ports[0];
  if (event.source === window && event.data === 'cecilia:core-port' && port) connection.attach(port);
});

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Kept current by the updates subscription instead of refetching.
      staleTime: Number.POSITIVE_INFINITY,
      refetchOnWindowFocus: false,
      retry: (count, error) => count < 1 && !(error instanceof TRPCClientError),
    },
    mutations: { retry: false, gcTime: 0 },
  },
});

export const coreClient = createTRPCClient<CoreRouter>({ links: [portLink(connection)] });
export const core = createTRPCOptionsProxy<CoreRouter>({ client: coreClient, queryClient });

/** The main process, served from app://cecilia like the page itself. */
export const shell = createTRPCOptionsProxy<ShellRouter>({
  client: createTRPCClient<ShellRouter>({ links: [httpLink({ url: '/api/trpc' })] }),
  queryClient,
});

/** The user-facing message of a failed request. */
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/** The message for each invalid input field of a failed request, by field name. */
export function fieldErrors(error: unknown): Record<string, string | undefined> {
  if (!(error instanceof TRPCClientError)) return {};
  const fields = (error as TRPCClientError<CoreRouter>).data?.fieldErrors ?? {};
  return Object.fromEntries(Object.entries(fields).map(([field, messages]) => [field, messages?.[0]]));
}
