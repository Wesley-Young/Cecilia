import type { ChatClient } from '#core/client/service.ts';
import { publicProcedure } from '../procedures.ts';

export function loginRoutes(client: ChatClient) {
  return {
    state: publicProcedure.query(() => client.loginState),
    /** Returns once login has started; progress, such as the QR code, follows as updates. */
    start: publicProcedure.mutation(() => {
      void client.login();
    }),
    logout: publicProcedure.mutation(() => client.logout()),
  };
}
