import { Button, Chip, Skeleton } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { BellRing, Cake, Check, Copy, Mars, MessageCircle, Venus } from 'lucide-react';
import { type ReactNode, useEffect, useRef, useState } from 'react';

import { friendName, type Gender, memberName, type UserProfile } from '#core/model/contact.ts';
import type { Peer } from '#core/model/peer.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { fullDate, messageTime, roleLabels } from '@/lib/format';
import { core } from '@/lib/rpc';

/** Profiles change rarely; one this old is fetched again the next time a card opens. */
const profileLifetime = 5 * 60 * 1000;
/** How long the copy button shows that it copied. */
const copiedDuration = 1500;

const genderLabels = { male: '男', female: '女', unknown: '' } satisfies Record<Gender, string>;

export function useProfile(uin: number) {
  return useQuery(core.contacts.profile.queryOptions({ uin }, { staleTime: profileLifetime }));
}

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

export function CardHeader(props: { peer: Peer; name: string; subtitle: string | undefined; copyLabel: string }) {
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
export function About({ text }: { text: string }) {
  return <p className="selectable line-clamp-3 whitespace-pre-wrap break-words px-4 pb-3 text-muted">{text}</p>;
}

/** Label and value rows; hidden when every row is left out. */
export function Fields({ children }: { children: ReactNode }) {
  return <dl className="border-separator border-t px-4 py-2.5 empty:hidden">{children}</dl>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1">
      <dt className="shrink-0 text-muted">{label}</dt>
      <dd className="selectable min-w-0 truncate text-right">{children}</dd>
    </div>
  );
}

/** Stands in for a value that is still loading, at about the height of a line. */
const Pending = () => <Skeleton className="h-4 w-16 rounded-md" />;

const locationOf = (profile: UserProfile | undefined) =>
  profile && [profile.country, profile.city].filter(Boolean).join(' ');

/** A row of chips under the header; hidden when it holds none. */
function Badges({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap gap-1.5 px-4 pb-3 empty:hidden">{children}</div>;
}

/**
 * Gender as an icon next to the age: "♂ 20 岁". Without an age, the gender is spelled out; without a gender, a cake
 * stands in for the icon; with neither, there is no chip.
 */
function GenderAgeChip({ gender, age }: { gender: Gender; age: number }) {
  const Icon = gender === 'male' ? Mars : gender === 'female' ? Venus : Cake;
  if (gender === 'unknown' && age <= 0) return null;
  return (
    <Chip size="sm" variant="soft" className="gap-1">
      <Icon className="size-3" aria-hidden="true" />
      {/* The icon says the gender to those who see it. */}
      {age > 0 && gender !== 'unknown' && <span className="sr-only">{genderLabels[gender]}，</span>}
      {age > 0 ? `${age} 岁` : genderLabels[gender]}
    </Chip>
  );
}

/** Stands in for a chip that is still loading. */
const PendingChip = () => <Skeleton className="h-5 w-12 rounded-full" />;

export function FriendCard({ uin, name }: { uin: number; name: string }) {
  const friend = useQuery(core.contacts.friend.queryOptions({ uin })).data ?? undefined;
  const profile = useProfile(uin);
  const location = locationOf(profile.data);
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
      <Badges>{friend && <GenderAgeChip gender={friend.gender} age={friend.age} />}</Badges>
      {friend?.bio && <About text={friend.bio} />}
      <Fields>
        {friend?.qid && (
          <Field label="QID">
            <span className="font-mono">{friend.qid}</span>
          </Field>
        )}
        {(loading || location) && <Field label="所在地">{loading ? <Pending /> : location}</Field>}
        {(loading || level > 0) && <Field label="等级">{loading ? <Pending /> : level}</Field>}
        {friend?.category && <Field label="分组">{friend.category}</Field>}
      </Fields>
    </>
  );
}

/** Someone who is not a friend, such as this account itself: only what their public profile says. */
export function UserCard({ uin, name }: { uin: number; name: string }) {
  const profile = useProfile(uin);
  const { data } = profile;
  const loading = profile.isPending;
  const location = locationOf(data);
  return (
    <>
      <CardHeader
        peer={{ scene: 'friend', uin }}
        name={data?.nickname || name}
        subtitle={undefined}
        copyLabel="复制 QQ 号"
      />
      <Badges>
        {loading && <PendingChip />}
        {data && <GenderAgeChip gender={data.gender} age={data.age} />}
      </Badges>
      {data?.bio && <About text={data.bio} />}
      <Fields>
        {data?.qid && (
          <Field label="QID">
            <span className="font-mono">{data.qid}</span>
          </Field>
        )}
        {(loading || location) && <Field label="所在地">{loading ? <Pending /> : location}</Field>}
        {(loading || (data?.level ?? 0) > 0) && <Field label="等级">{loading ? <Pending /> : data?.level}</Field>}
      </Fields>
    </>
  );
}

interface MemberCardProps {
  group: number;
  uin: number;
  /** The name to show until the member list has loaded. */
  name: string;
  /** Puts a mention of the member in the composer; absent for this account itself. */
  onMention: (() => void) | undefined;
  /** Opens the chat with the member; absent when they are not a friend. */
  onOpenChat: (() => void) | undefined;
}

/** A group member: their place in the group first, then their public profile. */
export function MemberCard({ group, uin, name, onMention, onOpenChat }: MemberCardProps) {
  const members = useQuery(core.contacts.members.queryOptions({ group }));
  const member = members.data?.find((candidate) => candidate.uin === uin);
  const profile = useProfile(uin);
  const { data } = profile;
  const loading = profile.isPending;
  const location = locationOf(data);
  const display = member ? memberName(member) : name;
  return (
    <>
      <CardHeader
        peer={{ scene: 'friend', uin }}
        name={display}
        subtitle={member?.card && member.nickname !== member.card ? `昵称：${member.nickname}` : undefined}
        copyLabel="复制 QQ 号"
      />
      <Badges>
        {member && member.role !== 'member' && (
          <Chip size="sm" variant="soft" color="accent">
            {roleLabels[member.role]}
          </Chip>
        )}
        {member?.title && (
          <Chip size="sm" variant="soft">
            {member.title}
          </Chip>
        )}
        {member && member.level > 0 && (
          <Chip size="sm" variant="soft">
            LV {member.level}
          </Chip>
        )}
        {loading && <PendingChip />}
        {data && <GenderAgeChip gender={data.gender} age={data.age} />}
      </Badges>
      {data?.bio && <About text={data.bio} />}
      <Fields>
        {(loading || location) && <Field label="所在地">{loading ? <Pending /> : location}</Field>}
        {member && member.joinedAt > 0 && <Field label="入群时间">{fullDate(member.joinedAt)}</Field>}
        {member && member.lastSpokeAt > 0 && <Field label="最后发言">{messageTime(member.lastSpokeAt)}</Field>}
      </Fields>
      {(onMention || onOpenChat) && (
        <div className="flex gap-1 border-separator border-t p-1.5">
          {onMention && (
            <Button variant="ghost" size="sm" className="flex-1" onPress={onMention}>
              <BellRing className="size-4" />
              @TA
            </Button>
          )}
          {onOpenChat && (
            <Button variant="ghost" size="sm" className="flex-1" onPress={onOpenChat}>
              <MessageCircle className="size-4" />
              发消息
            </Button>
          )}
        </div>
      )}
    </>
  );
}
