import { Dropdown, Popover, Separator, toast } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { BellRing, Copy, MessageCircle, PenLine, UserRound } from 'lucide-react';
import { type Key, type MouseEvent, type ReactNode, useCallback, useRef, useState } from 'react';
import { Pressable } from 'react-aria-components';
import { createPortal } from 'react-dom';

import { type GroupMember, memberName } from '#core/model/contact.ts';
import type { Peer } from '#core/model/peer.ts';
import { useChatActions } from '@/components/ChatActions';
import EditCardDialog from '@/components/EditCardDialog';
import { FriendCard, MemberCard, UserCard } from '@/components/ProfileCard';
import { core } from '@/lib/rpc';

/** The owner can change anyone's group card, an admin those of ordinary members, and everyone their own. */
function canEditCard(me: GroupMember | undefined, target: GroupMember | undefined): boolean {
  if (!me || !target) return false;
  return me.uin === target.uin || me.role === 'owner' || (me.role === 'admin' && target.role === 'member');
}

/** What the card and the menu need to know about the person, looked up only while either is open. */
function usePerson(chat: Peer, uin: number) {
  const login = useQuery(core.login.state.queryOptions());
  const self = login.data?.kind === 'online' ? login.data.self.uin : undefined;
  const group = chat.scene === 'group' ? chat.uin : undefined;
  const members = useQuery({
    ...core.contacts.members.queryOptions({ group: group ?? 0 }),
    enabled: group !== undefined,
  });
  const friend = useQuery(core.contacts.friend.queryOptions({ uin })).data ?? undefined;
  return {
    group,
    isSelf: uin === self,
    me: members.data?.find((member) => member.uin === self),
    member: members.data?.find((member) => member.uin === uin),
    friend,
  };
}

interface Target {
  /** The conversation the person is shown in. */
  chat: Peer;
  uin: number;
  /** The name shown until their details load. */
  name: string;
}

function PersonCard({ chat, uin, name, onDone }: Target & { onDone: () => void }) {
  const actions = useChatActions();
  const { group, isSelf, member, friend } = usePerson(chat, uin);
  if (group === undefined) {
    return uin === chat.uin ? <FriendCard uin={uin} name={name} /> : <UserCard uin={uin} name={name} />;
  }
  const mention = actions && !isSelf ? () => actions.mention(uin, member ? memberName(member) : name) : undefined;
  const openChat = actions && friend && !isSelf ? () => actions.openChat(uin) : undefined;
  return (
    <MemberCard
      group={group}
      uin={uin}
      name={name}
      onMention={
        mention &&
        (() => {
          onDone();
          mention();
        })
      }
      onOpenChat={
        openChat &&
        (() => {
          onDone();
          openChat();
        })
      }
    />
  );
}

type Action = 'mention' | 'profile' | 'chat' | 'copy' | 'edit-card';

function PersonMenu({
  chat,
  uin,
  name,
  onAction,
}: Target & { onAction: (action: Action, member?: GroupMember) => void }) {
  const actions = useChatActions();
  const { group, isSelf, me, member, friend } = usePerson(chat, uin);
  const inGroup = group !== undefined;
  const editable = inGroup && canEditCard(me, member);
  const item = (id: Action, icon: ReactNode, label: string) => (
    // Tighter than HeroUI's default rows, for a menu of a few short commands.
    <Dropdown.Item id={id} textValue={label} className="min-h-7 gap-2 rounded-lg px-2 py-1 text-[13px]">
      {icon}
      {label}
    </Dropdown.Item>
  );
  return (
    // Opened without a trigger, the menu takes focus itself so the arrow keys and Enter work at once.
    <Dropdown.Menu
      aria-label={`${name} 的操作`}
      autoFocus="first"
      className="gap-0.5"
      onAction={(key: Key) => onAction(key as Action, member)}
    >
      {inGroup && actions && !isSelf && item('mention', <BellRing className="size-3.5" />, '@TA')}
      {item('profile', <UserRound className="size-3.5" />, '查看资料')}
      {inGroup && actions && friend && !isSelf && item('chat', <MessageCircle className="size-3.5" />, '发消息')}
      {item('copy', <Copy className="size-3.5" />, '复制 QQ 号')}
      {editable && <Separator />}
      {editable && item('edit-card', <PenLine className="size-3.5" />, isSelf ? '修改我的群名片…' : '修改群名片…')}
    </Dropdown.Menu>
  );
}

interface Props extends Target {
  /** Where the card opens, next to the button. */
  placement: 'left top' | 'right top';
  className: string;
  children: ReactNode;
}

/**
 * Someone shown in a chat, as an avatar or a member row: a click opens their card, a right click a menu of what can
 * be done with them there.
 */
export default function PersonButton({ chat, uin, name, placement, className, children }: Props) {
  const actions = useChatActions();
  const [cardOpen, setCardOpen] = useState(false);
  const [menuAt, setMenuAt] = useState<{ x: number; y: number }>();
  const [editing, setEditing] = useState<GroupMember>();
  const card = useRef<HTMLElement>(null);

  // React Aria caps the card's height at the room it had when it opened, so a card that grows as the profile loads
  // would spill past its bottom. Lifting the cap lets the card grow, which makes React Aria place it again.
  const cardContent = useCallback((content: HTMLDivElement | null) => {
    if (!content) return;
    const observer = new ResizeObserver(() => card.current?.style.removeProperty('max-height'));
    observer.observe(content);
    return () => observer.disconnect();
  }, []);

  const openMenu = (event: MouseEvent) => {
    event.preventDefault();
    setMenuAt({ x: event.clientX, y: event.clientY });
  };

  const act = (action: Action, member?: GroupMember) => {
    setMenuAt(undefined);
    switch (action) {
      case 'mention':
        actions?.mention(uin, member ? memberName(member) : name);
        break;
      case 'profile':
        setCardOpen(true);
        break;
      case 'chat':
        actions?.openChat(uin);
        break;
      case 'copy':
        navigator.clipboard.writeText(String(uin)).then(
          () => toast('已复制 QQ 号'),
          () => toast.danger('复制失败'),
        );
        break;
      case 'edit-card':
        setEditing(member);
        break;
    }
  };

  return (
    <>
      {/* HeroUI's popovers take their look from their root, so each one opens from inside it. */}
      <Popover isOpen={cardOpen} onOpenChange={setCardOpen}>
        <Popover.Trigger
          aria-label={`${name} 的资料`}
          onContextMenu={openMenu}
          className={`app-no-drag cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-focus ${className}`}
        >
          {children}
        </Popover.Trigger>
        <Popover.Content ref={card} placement={placement} offset={8} className="app-no-drag w-80">
          <Popover.Dialog aria-label={`${name} 的资料`} className="p-0 text-sm">
            <div ref={cardContent}>
              <PersonCard chat={chat} uin={uin} name={name} onDone={() => setCardOpen(false)} />
            </div>
          </Popover.Dialog>
        </Popover.Content>
      </Popover>
      {/* The menu opens at the pointer, like a desktop context menu: its trigger is an empty spot placed there. */}
      {menuAt &&
        createPortal(
          <Dropdown isOpen onOpenChange={(open) => !open && setMenuAt(undefined)}>
            <Pressable>
              <span
                tabIndex={-1}
                aria-hidden="true"
                className="pointer-events-none fixed size-0"
                style={{ left: menuAt.x, top: menuAt.y }}
              />
            </Pressable>
            <Dropdown.Popover placement="bottom start" offset={2} className="app-no-drag min-w-36 rounded-xl">
              <PersonMenu chat={chat} uin={uin} name={name} onAction={act} />
            </Dropdown.Popover>
          </Dropdown>,
          document.body,
        )}
      {editing && chat.scene === 'group' && (
        <EditCardDialog group={chat.uin} member={editing} onClose={() => setEditing(undefined)} />
      )}
    </>
  );
}
