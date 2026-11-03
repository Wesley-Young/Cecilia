import type { AccountService } from '#core/account/service.ts';
import type { ChatClient } from '#core/client/service.ts';
import { publicProcedure } from '../procedures.ts';

export function loginRoutes(client: ChatClient, account: AccountService) {
  return {
    state: publicProcedure.query(() => client.loginState),
    /** Returns once login has started; progress, such as the QR code, follows as updates. */
    start: publicProcedure.mutation(() => {
      void client.login();
    }),
    logout: publicProcedure.mutation(() => client.logout()),
    /** Drops the saved session and starts a QR code login, like `start`. */
    reset: publicProcedure.mutation(async () => {
      await account.resetSession();
      void client.login();
    }),
  };
}
