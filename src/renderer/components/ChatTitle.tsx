import { Button, Popover, Skeleton } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { Check, ChevronDown, ChevronRight, Copy, Megaphone } from 'lucide-react';
import { type ReactNode, useEffect, useRef, useState } from 'react';

import { type Conversation, friendName, type Gender } from '#core/model/contact.ts';
import type { Peer } from '#core/model/peer.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { fullDate } from '@/lib/format';
import { core } from '@/lib/rpc';

/** Profiles change rarely; one this old is fetched again the next time the card opens. */
const profileLifetime = 5 * 60 * 1000;
/** How long the copy button shows that it copied. */
const copiedDuration = 1500;

const genderLabels = { male: '男', female: '女', unknown: '' } satisfies Record<Gender, string>;

function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // The window was not focused; the button stays as it was, so the copy can be tried again.
      return;
    }
    setCopied(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), copiedDuration);
  };
  return (
    <Button
      variant="ghost"
      size="sm"
      isIconOnly
      aria-label={copied ? '已复制' : label}
      onPress={copy}
      className="size-6"
    >
      {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
    </Button>
  );
}

function CardHeader(props: { peer: Peer; name: string; subtitle: string | undefined; copyLabel: string }) {
  const { peer, name, subtitle, copyLabel } = props;
  return (
    <div className="flex items-center gap-3 px-4 pt-4 pb-3">
      <PeerAvatar peer={peer} name={name} size="lg" />
      <div className="flex min-w-0 flex-col">
        <span className="selectable truncate font-semibold text-base">{name}</span>
        {subtitle && <span className="selectable truncate text-muted text-xs">{subtitle}</span>}
        <span className="flex items-center gap-0.5 text-muted text-xs">
          <span className="selectable font-mono">{peer.uin}</span>
          <CopyButton text={String(peer.uin)} label={copyLabel} />
        </span>
      </div>
    </div>
  );
}

/** A bio or a group description: a few lines, the rest cut off. */
function About({ text }: { text: string }) {
  return <p className="selectable line-clamp-3 whitespace-pre-wrap break-words px-4 pb-3 text-muted">{text}</p>;
}

/** Label and value rows; hidden when every row is left out. */
function Fields({ children }: { children: ReactNode }) {
  return <dl className="border-separator border-t px-4 py-2.5 empty:hidden">{children}</dl>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1">
      <dt className="shrink-0 text-muted">{label}</dt>
      <dd className="selectable min-w-0 truncate text-right">{children}</dd>
    </div>
  );
}

/** Stands in for a value that is still loading, at about the height of a line. */
const Pending = () => <Skeleton className="h-4 w-16 rounded-md" />;

function FriendCard({ uin, name }: { uin: number; name: string }) {
  const friend = useQuery(core.contacts.friend.queryOptions({ uin })).data ?? undefined;
  const profile = useQuery(core.contacts.profile.queryOptions({ uin }, { staleTime: profileLifetime }));
  const location = profile.data && [profile.data.country, profile.data.city].filter(Boolean).join(' ');
  const level = profile.data?.level ?? 0;
  const loading = profile.isPending;
  return (
    <>
      <CardHeader
        peer={{ scene: 'friend', uin }}
        name={friend ? friendName(friend) : name}
        subtitle={friend?.remark && friend.nickname !== friend.remark ? friend.nickname : undefined}
        copyLabel="复制 QQ 号"
      />
      {friend?.bio && <About text={friend.bio} />}
      <Fields>
        {friend?.qid && (
          <Field label="QID">
            <span className="font-mono">{friend.qid}</span>
          </Field>
        )}
        {friend && friend.gender !== 'unknown' && <Field label="性别">{genderLabels[friend.gender]}</Field>}
        {friend && friend.age > 0 && <Field label="年龄">{friend.age} 岁</Field>}
        {(loading || location) && <Field label="所在地">{loading ? <Pending /> : location}</Field>}
        {(loading || level > 0) && <Field label="等级">{loading ? <Pending /> : level}</Field>}
        {friend?.category && <Field label="分组">{friend.category}</Field>}
      </Fields>
    </>
  );
}

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
