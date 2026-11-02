import { protocol } from 'electron';

/** The renderer and the RPC endpoint are both served from this privileged origin. */
export const appScheme = 'app';
export const appHost = 'cecilia';
export const appOrigin = `${appScheme}://${appHost}`;
export const rpcEndpoint = '/api/trpc';

/** Must run before the app is ready. */
export function registerAppScheme() {
  protocol.registerSchemesAsPrivileged([
    {
      scheme: appScheme,
      privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true },
    },
  ]);
}
