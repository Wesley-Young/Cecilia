import { z } from 'zod';

import type { ContactService } from '#core/contacts/service.ts';
import { publicProcedure } from '../procedures.ts';

const uin = z.number().int().positive();

export function contactRoutes(contacts: ContactService) {
  return {
    /** Null until the friend list has loaded, or for someone who is no longer a friend. */
    friend: publicProcedure.input(z.object({ uin })).query(({ input }) => contacts.friend(input.uin) ?? null),
    profile: publicProcedure.input(z.object({ uin })).query(({ input }) => contacts.profile(input.uin)),
    /** Null until the group list has loaded, or for a group this account has left. */
    group: publicProcedure.input(z.object({ uin })).query(({ input }) => contacts.group(input.uin) ?? null),
    members: publicProcedure.input(z.object({ group: uin })).query(({ input }) => contacts.membersOf(input.group)),
    /** An empty card clears it. */
    setMemberCard: publicProcedure
      .input(z.object({ group: uin, uin, card: z.string().trim() }))
      .mutation(({ input }) => contacts.setMemberCard(input.group, input.uin, input.card)),
  };
}
