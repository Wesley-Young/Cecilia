import { Pin } from 'lucide-react';

import type { Conversation } from '#core/model/contact.ts';
import { type Peer, peerKey, samePeer } from '#core/model/peer.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { listTime } from '@/lib/format';

interface Props {
  conversations: Conversation[];
  selected: Peer | undefined;
  onSelect: (peer: Peer) => void;
}

export default function ConversationList({ conversations, selected, onSelect }: Props) {
  if (conversations.length === 0) {
    return <p className="px-6 py-10 text-center text-sm text-muted">还没有会话，收到的消息会出现在这里</p>;
  }
  return (
    <ul className="flex flex-col gap-0.5 px-2 pb-2">
      {conversations.map((conversation) => {
        const active = selected !== undefined && samePeer(selected, conversation.peer);
        return (
          <li key={peerKey(conversation.peer)}>
            <button
              type="button"
              onClick={() => onSelect(conversation.peer)}
              className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left outline-none transition-colors focus-visible:ring-2 focus-visible:ring-accent ${
                active ? 'bg-accent text-accent-foreground' : 'hover:bg-default-hover'
              }`}
            >
              <PeerAvatar peer={conversation.peer} name={conversation.name} />
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <div className="flex items-center gap-2">
                  <span className="truncate font-medium text-sm">{conversation.name}</span>
                  {conversation.pinned && (
                    <Pin className={`size-3 shrink-0 ${active ? 'opacity-80' : 'text-muted'}`} aria-label="已置顶" />
                  )}
                  <span className={`ml-auto shrink-0 text-xs ${active ? 'opacity-80' : 'text-muted'}`}>
                    {listTime(conversation.time)}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`truncate text-xs ${active ? 'opacity-80' : 'text-muted'}`}>
                    {conversation.summary || ' '}
                  </span>
                  {conversation.unread > 0 && (
                    <span
                      className={`ml-auto min-w-5 shrink-0 rounded-full px-1.5 text-center font-medium text-[11px] leading-5 ${
                        active ? 'bg-accent-foreground text-accent' : 'bg-accent text-accent-foreground'
                      }`}
                    >
                      {conversation.unread > 99 ? '99+' : conversation.unread}
                    </span>
                  )}
                </div>
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
