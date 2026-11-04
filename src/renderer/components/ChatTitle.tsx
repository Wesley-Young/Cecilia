import { Button, Popover } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, ChevronRight, Megaphone } from 'lucide-react';
import { useState } from 'react';

import type { Conversation } from '#core/model/contact.ts';
import { About, CardHeader, Field, Fields, FriendCard } from '@/components/ProfileCard';
import { fullDate } from '@/lib/format';
import { core } from '@/lib/rpc';

function GroupCard(props: { uin: number; name: string; onShowMembers: (() => void) | undefined }) {
  const { uin, name, onShowMembers } = props;
  const group = useQuery(core.contacts.group.queryOptions({ uin })).data ?? undefined;
  return (
    <>
      <CardHeader peer={{ scene: 'group', uin }} name={group?.name ?? name} subtitle={undefined} copyLabel="复制群号" />
      {group?.description && <About text={group.description} />}
      {group?.announcement && (
        <div className="mx-4 mb-3 rounded-xl bg-default px-3 py-2">
          <p className="mb-0.5 flex items-center gap-1 text-muted text-xs">
            <Megaphone className="size-3" />
            公告
          </p>
          <p className="selectable line-clamp-2 whitespace-pre-wrap break-words">{group.announcement}</p>
        </div>
      )}
      <Fields>
        {group && !onShowMembers && (
          <Field label="成员">
            {group.memberCount} / {group.capacity}
          </Field>
        )}
        {group && onShowMembers && (
          <Field label="成员">
            <button
              type="button"
              onClick={onShowMembers}
              className="inline-flex cursor-pointer items-center gap-0.5 rounded text-accent outline-none hover:underline focus-visible:ring-2 focus-visible:ring-focus"
            >
              {group.memberCount} / {group.capacity}
              <ChevronRight className="size-3.5" />
            </button>
          </Field>
        )}
        {group && group.createdAt > 0 && <Field label="创建于">{fullDate(group.createdAt)}</Field>}
      </Fields>
    </>
  );
}

interface Props {
  conversation: Conversation;
  /** Shows the member column; absent while the column is always on screen. */
  onShowMembers: (() => void) | undefined;
}

/**
 * The conversation's name; opens a card about the friend or group. Its padding hangs into the header's gutter, so
 * the parent offsets it (a negative margin here would shrink the width it asks for and cut the name short).
 */
export default function ChatTitle({ conversation, onShowMembers }: Props) {
  const { peer, name } = conversation;
  const [open, setOpen] = useState(false);
  return (
    <Popover isOpen={open} onOpenChange={setOpen}>
      <Button variant="ghost" size="sm" className="app-no-drag min-w-0 max-w-full gap-1 px-2 font-semibold text-base">
        <span className="truncate">{name}</span>
        <ChevronDown className="size-4 shrink-0 text-muted" />
      </Button>
      <Popover.Content placement="bottom start" offset={6} className="app-no-drag w-80">
        <Popover.Dialog aria-label={peer.scene === 'group' ? '群资料' : '好友资料'} className="p-0 text-sm">
          {peer.scene === 'group' ? (
            <GroupCard
              uin={peer.uin}
              name={name}
              onShowMembers={
                onShowMembers &&
                (() => {
                  setOpen(false);
                  onShowMembers();
                })
              }
            />
          ) : (
            <FriendCard uin={peer.uin} name={name} />
          )}
        </Popover.Dialog>
      </Popover.Content>
    </Popover>
  );
}
