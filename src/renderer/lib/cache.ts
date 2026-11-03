import type { Conversation } from '#core/model/contact.ts';
import type { ChatMessage } from '#core/model/message.ts';
import { type Peer, samePeer } from '#core/model/peer.ts';
import { type ChatUpdate, core, queryClient } from './rpc.ts';

// Every change pushed by the account process lands in the query cache here, so screens only read queries.

export const historyInput = (peer: Peer) => ({ peer: { scene: peer.scene, uin: peer.uin } });

/** Pinned first, then newest first; the same order the account process lists them in. */
function byListOrder(a: Conversation, b: Conversation) {
  return Number(b.pinned) - Number(a.pinned) || b.time - a.time;
}

function applyConversation(conversation: Conversation) {
  queryClient.setQueryData(core.conversations.list.queryKey(), (list) => {
    if (!list) return list;
    const rest = list.filter((item) => !samePeer(item.peer, conversation.peer));
    return [...rest, conversation].sort(byListOrder);
  });
}

/** Sent messages without a seq sort after every other, as the store orders them. */
function byConversationOrder(a: ChatMessage, b: ChatMessage) {
  const seq = (message: ChatMessage) => message.seq ?? Number.MAX_SAFE_INTEGER;
  return seq(a) - seq(b) || a.id - b.id;
}

function applyMessage(message: ChatMessage) {
  queryClient.setQueryData(core.messages.history.infiniteQueryKey(historyInput(message.peer)), (data) => {
    // Nothing to update until the conversation is opened; it loads from the store then.
    if (!data) return data;
    let found = false;
    const pages = data.pages.map((page) => {
      if (!page.messages.some((item) => item.id === message.id)) return page;
      found = true;
      return { ...page, messages: page.messages.map((item) => (item.id === message.id ? message : item)) };
    });
    const [newest, ...older] = pages;
    if (found || !newest) return { ...data, pages };
    return {
      ...data,
      pages: [{ ...newest, messages: [...newest.messages, message].sort(byConversationOrder) }, ...older],
    };
  });
}

export function applyUpdate(update: ChatUpdate) {
  switch (update.type) {
    case 'login':
      queryClient.setQueryData(core.login.state.queryKey(), update.state);
      // Coming online may bring conversations and history the store did not have.
      if (update.state.kind === 'online')
        void queryClient.invalidateQueries({ queryKey: core.conversations.list.queryKey() });
      break;
    case 'conversation':
      applyConversation(update.conversation);
      break;
    case 'message':
      applyMessage(update.message);
      break;
    case 'contacts':
      void queryClient.invalidateQueries({ queryKey: core.contacts.pathKey() });
      break;
  }
}
