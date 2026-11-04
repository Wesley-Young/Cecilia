import { type GroupMember, type MemberRole, memberName } from '#core/model/contact.ts';

/** Roles in the order members are listed: the owner, then admins, then everyone else. */
export const memberRoles: readonly MemberRole[] = ['owner', 'admin', 'member'];

const collator = new Intl.Collator('zh-Hans-CN', { numeric: true });

/** Members in the order they are listed: by role, then by name within each role. */
export function sortMembers(members: readonly GroupMember[]): GroupMember[] {
  return members.toSorted(
    (a, b) =>
      memberRoles.indexOf(a.role) - memberRoles.indexOf(b.role) || collator.compare(memberName(a), memberName(b)),
  );
}
