import { z } from 'zod';

import type { ContactService } from '#core/contacts/service.ts';
import { publicProcedure } from '../procedures.ts';

export function contactRoutes(contacts: ContactService) {
  return {
    /** Null until the group list has loaded, or for a group this account has left. */
    group: publicProcedure
      .input(z.object({ uin: z.number().int().positive() }))
      .query(({ input }) => contacts.group(input.uin) ?? null),
  };
}
