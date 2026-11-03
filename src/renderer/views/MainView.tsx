import { Button, Spinner, ToggleButton } from '@heroui/react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { LogOut, MessagesSquare, Settings, Users, WifiOff } from 'lucide-react';
import { useCallback, useState } from 'react';

import type { Conversation } from '#core/model/contact.ts';
import type { LoginState } from '#core/model/login.ts';
import { type Peer, peerKey, samePeer } from '#core/model/peer.ts';
import ChatTitle from '@/components/ChatTitle';
import Composer from '@/components/Composer';
import ConversationList from '@/components/ConversationList';
import MemberPanel from '@/components/MemberPanel';
import MessageList from '@/components/MessageList';
import PeerAvatar from '@/components/PeerAvatar';
import SettingsDialog from '@/components/SettingsDialog';
import { useMediaQuery } from '@/lib/media';
import { core, shell } from '@/lib/rpc';

function StatusLine({ state }: { state: LoginState }) {
  if (state.kind === 'online') return null;
  const text =
    state.kind === 'offline'
      ? `连接已断开${state.retryAt ? '，正在重连' : ''}`
      : state.kind === 'logging-in'
        ? '正在登录…'
        : '未登录';
  return (
    <div className="mx-3 mb-2 flex items-center gap-2 rounded-lg bg-warning-soft px-3 py-2 text-warning-soft-foreground text-xs">
      {state.kind === 'logging-in' ? <Spinner size="sm" /> : <WifiOff className="size-3.5" />}
      {text}
    </div>
  );
}

/** The member column stays beside the messages from this window width: the sidebar's 288px and 720px for the chat. */
const dockedQuery = '(min-width: 1008px)';

/** A group's size, once the group list has loaded; shows and hides the member column where it does not stay. */
function MemberToggle(props: { group: number; isSelected: boolean; onChange: (selected: boolean) => void }) {
  const { group, isSelected, onChange } = props;
  const info = useQuery(core.contacts.group.queryOptions({ uin: group }));
  if (!info.data) return null;
  return (
    <ToggleButton
      size="sm"
      variant="ghost"
      isSelected={isSelected}
      onChange={onChange}
      aria-label={`群成员，${info.data.memberCount} 人`}
      className="app-no-drag shrink-0 gap-1"
    >
      <Users className="size-3.5" />
      {info.data.memberCount}
    </ToggleButton>
  );
}

function ChatPane(props: { conversation: Conversation; self: number | undefined; online: boolean }) {
  const { conversation, self, online } = props;
  const markRead = useMutation(core.conversations.markRead.mutationOptions());
  const docked = useMediaQuery(dockedQuery);
  const [membersOpen, setMembersOpen] = useState(false);
  const { peer, unread } = conversation;
  const group = peer.scene === 'group';
  const { mutate } = markRead;
  const onReachEnd = useCallback(() => {
    if (unread > 0) mutate({ peer });
  }, [mutate, peer, unread]);

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-background">
      <header className="app-drag flex h-14 shrink-0 items-center gap-2 border-separator border-b px-5">
        <h2 className="-ml-2 flex min-w-0">
          <ChatTitle
            conversation={conversation}
            self={self}
            onShowMembers={docked ? undefined : () => setMembersOpen(true)}
          />
        </h2>
        {group && !docked && <MemberToggle group={peer.uin} isSelected={membersOpen} onChange={setMembersOpen} />}
      </header>
      {/* Clips the member column while it slides in and out. */}
      <div className="relative flex min-h-0 flex-1 overflow-hidden">
        <div className="flex min-w-0 flex-1 flex-col">
          <MessageList key={peerKey(peer)} peer={peer} onReachEnd={onReachEnd} />
          <Composer key={`composer:${peerKey(peer)}`} peer={peer} disabled={!online} />
        </div>
        {group && (
          <MemberPanel
            key={peer.uin}
            group={peer.uin}
            docked={docked}
            isOpen={membersOpen}
            onClose={() => setMembersOpen(false)}
          />
        )}
      </div>
    </section>
  );
}

export default function MainView({ state, conversations }: { state: LoginState; conversations: Conversation[] }) {
  const [selected, setSelected] = useState<Peer>();
  const logout = useMutation(core.login.logout.mutationOptions());
  const info = useQuery(shell.system.info.queryOptions());
  const [settingsOpen, setSettingsOpen] = useState(false);
  const current = selected && conversations.find((conversation) => samePeer(conversation.peer, selected));
  const self = state.kind === 'online' ? state.self : undefined;

  return (
    <div className="flex h-full">
      <aside className="flex w-72 shrink-0 flex-col border-separator border-r bg-sidebar">
        {/* Leaves room for the macOS traffic lights and drags the window. */}
        <div className="app-drag h-10 shrink-0" />
        <div className="flex items-center gap-3 px-4 pb-3">
          {self ? (
            <>
              <PeerAvatar peer={{ scene: 'friend', uin: self.uin }} name={self.nickname} size="sm" />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate font-medium text-sm">{self.nickname}</span>
                <span className="text-[11px] text-muted">{self.uin}</span>
              </div>
            </>
          ) : (
            <span className="flex-1 font-semibold">Cecilia</span>
          )}
          {info.data && !info.data.mock && (
            <Button variant="ghost" size="sm" isIconOnly aria-label="账号设置" onPress={() => setSettingsOpen(true)}>
              <Settings className="size-4" />
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            aria-label="退出登录"
            isDisabled={state.kind !== 'online'}
            onPress={() => logout.mutate()}
          >
            <LogOut className="size-4" />
          </Button>
        </div>
        <StatusLine state={state} />
        <div className="min-h-0 flex-1 overflow-y-auto">
          <ConversationList conversations={conversations} selected={selected} onSelect={setSelected} />
        </div>
      </aside>
      {current ? (
        <ChatPane conversation={current} self={self?.uin} online={state.kind === 'online'} />
      ) : (
        <section className="app-drag flex flex-1 flex-col items-center justify-center gap-3 text-muted">
          <MessagesSquare className="size-10 opacity-40" />
          <p className="text-sm">选择一个会话开始聊天</p>
        </section>
      )}
      <SettingsDialog isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  );
}
