import type { Conversation, Friend, Group } from './contact.ts';
import type { LoginState } from './login.ts';
import type { ChatMessage, RemoteMessage } from './message.ts';
import type { Peer } from './peer.ts';

/**
 * Every event on a ChatContext. `client/*` come from the ChatClient, as the server reports them; `contacts/*` and
 * `store/*` come from the plugins once they have taken the change in, and are what the interface follows.
 */
export type ChatEvents = {
  'client/login': LoginState;
  'client/message': RemoteMessage;
  'client/recall': { peer: Peer; seq: number };
  'contacts/updated': { friends: Friend[]; groups: Group[] };
  /** A group's member list was fetched. */
  'contacts/members': { group: number };
  'store/message': ChatMessage;
  'store/conversation': Conversation;
};
