import { serviceToken } from '@fraqjs/kernel';

import type { ChatClient } from '#core/client/service.ts';
import type { ChatContext } from '#core/context.ts';
import { type Friend, friendName, type Group } from '#core/model/contact.ts';
import type { Peer } from '#core/model/peer.ts';

/** The friend and group lists, kept in memory and refreshed each time the account comes online. */
export class ContactService {
  static readonly token = serviceToken<ContactService>('cecilia/ContactService');
  private friends = new Map<number, Friend>();
  private groups = new Map<number, Group>();
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
    this.ctx.emit('contacts/updated', { friends, groups });
  }

  friend(uin: number): Friend | undefined {
    return this.friends.get(uin);
  }

  /** The display name of a conversation, when the contact is known. */
  nameOf(peer: Peer): string | undefined {
    if (peer.scene === 'group') return this.groups.get(peer.uin)?.name;
    const friend = this.friends.get(peer.uin);
    return friend ? friendName(friend) : undefined;
  }
}
