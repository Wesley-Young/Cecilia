import { serviceToken } from '@fraqjs/kernel';
import { fetchRequestHandler } from '@trpc/server/adapters/fetch';
import { protocol } from 'electron';

import { definePlugin } from '#main/context.ts';
import { createShellRouter, type ShellInfo } from '#main/shell-rpc.ts';
import { type AssetOptions, createAssetHandler } from './assets.ts';
import { appHost, appOrigin, appScheme, rpcEndpoint } from './scheme.ts';

export interface ProtocolOptions extends AssetOptions {
  info: ShellInfo;
}

/** Serves app://cecilia: `/api/trpc` goes to the shell router, everything else is the renderer. */
export class AppProtocol {
  static readonly token = serviceToken<AppProtocol>('cecilia/AppProtocol');
  /** The origin pages are loaded from. */
  readonly origin = appOrigin;

  dispose() {
    protocol.unhandle(appScheme);
  }
}

export const ProtocolPlugin = definePlugin({
  name: 'protocol',
  provides: [AppProtocol],
  apply(ctx, options: ProtocolOptions) {
    const router = createShellRouter(options.info);
    const serveAsset = createAssetHandler(options);
    protocol.handle(appScheme, async (request) => {
      const url = new URL(request.url);
      if (url.host !== appHost) return new Response('Not Found', { status: 404 });
      if (url.pathname === rpcEndpoint || url.pathname.startsWith(`${rpcEndpoint}/`)) {
        return fetchRequestHandler({
          endpoint: rpcEndpoint,
          req: request,
          router,
          onError: ({ error, path }) => {
            if (error.code === 'INTERNAL_SERVER_ERROR') ctx.logger.error(`RPC ${path ?? '<unknown>'} failed`, error);
          },
        });
      }
      return serveAsset(request, url);
    });
    ctx.provide(AppProtocol, new AppProtocol());
  },
});
