import { serviceToken } from '@fraqjs/kernel';

import type { ChatClient } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import { type Friend, friendName, type Group, type GroupMember, memberName } from '#core/model/contact.ts';
import type { Peer } from '#core/model/peer.ts';

/** A member list is fetched again for an unknown member at most this often. */
const memberRefreshInterval = 60_000;

interface MemberFetch {
  at: number;
  done: Promise<void>;
}

/**
 * The friend and group lists, kept in memory and refreshed each time the account comes online. Group member lists
 * are large, so each is fetched only once its group has been opened.
 */
export class ContactService {
  static readonly token = serviceToken<ContactService>('cecilia/ContactService');
  private friends = new Map<number, Friend>();
  private groups = new Map<number, Group>();
  private members = new Map<number, Map<number, GroupMember>>();
  private memberFetches = new Map<number, MemberFetch>();
  private readonly ctx: ChatContext;
  private readonly client: ChatClient;

  constructor(ctx: ChatContext, client: ChatClient) {
    this.ctx = ctx;
    this.client = client;
  }

  async refresh(forceUpdate = false) {
    const [friends, groups] = await Promise.all([
      this.client.getFriends(forceUpdate),
      this.client.getGroups(forceUpdate),
    ]);
    this.friends = new Map(friends.map((friend) => [friend.uin, friend]));
    this.groups = new Map(groups.map((group) => [group.uin, group]));
    this.members = new Map();
    this.memberFetches = new Map();
    this.ctx.emit('contacts/updated', { friends, groups });
  }

  friend(uin: number): Friend | undefined {
    return this.friends.get(uin);
  }

  group(uin: number): Group | undefined {
    return this.groups.get(uin);
  }

  /** The display name of a conversation, when the contact is known. */
  nameOf(peer: Peer): string | undefined {
    if (peer.scene === 'group') return this.groups.get(peer.uin)?.name;
    const friend = this.friends.get(peer.uin);
    return friend ? friendName(friend) : undefined;
  }

  /**
   * Fetches a group's member list if it has not been yet. With `uin`, also refetches a list that does not have that
   * member, such as one who just joined.
   */
  async loadMembers(group: number, uin?: number) {
    const previous = this.memberFetches.get(group);
    if (previous) {
      await previous.done;
      const latest = this.memberFetches.get(group);
      if (latest !== previous) return latest?.done;
      if (uin === undefined || this.members.get(group)?.has(uin)) return;
      if (Date.now() - previous.at < memberRefreshInterval) return;
    }
    const done = this.client.getGroupMembers(group, previous !== undefined).then(
      (members) => {
        this.members.set(group, new Map(members.map((member) => [member.uin, member])));
      },
      (error) => this.ctx.logger.warn(`Cannot fetch members of group ${group}`, error),
    );
    this.memberFetches.set(group, { at: Date.now(), done });
    await done;
  }

  /** A member's group card or nickname; undefined until the group's member list has been loaded. */
  async memberName(group: number, uin: number): Promise<string | undefined> {
    if (!this.memberFetches.has(group)) return undefined;
    if (!this.members.get(group)?.has(uin)) await this.loadMembers(group, uin);
    const member = this.members.get(group)?.get(uin);
    return member && memberName(member);
  }
}
