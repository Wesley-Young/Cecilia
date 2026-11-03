import { Button, Chip, SearchField, Spinner } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { VList } from 'virtua';

import { type GroupMember, type MemberRole, memberName } from '#core/model/contact.ts';
import PeerAvatar from '@/components/PeerAvatar';
import { roleLabels } from '@/lib/format';
import { core, errorMessage } from '@/lib/rpc';

const roles: MemberRole[] = ['owner', 'admin', 'member'];

const collator = new Intl.Collator('zh-Hans-CN', { numeric: true });

type Row = { kind: 'section'; key: string; label: string } | { kind: 'member'; key: string; member: GroupMember };

/** Members matching `query` by name or number, under a heading per role and in name order within it. */
function toRows(members: GroupMember[], query: string): Row[] {
  const needle = query.trim().toLowerCase();
  const matches = (member: GroupMember) =>
    member.card.toLowerCase().includes(needle) ||
    member.nickname.toLowerCase().includes(needle) ||
    String(member.uin).includes(needle);
  const found = needle ? members.filter(matches) : members;
  const rows: Row[] = [];
  for (const role of roles) {
    const inRole = found
      .filter((member) => member.role === role)
      .sort((a, b) => collator.compare(memberName(a), memberName(b)));
    if (inRole.length === 0) continue;
    // There is one owner; counting it says nothing.
    const label = role === 'owner' ? roleLabels[role] : `${roleLabels[role]} · ${inRole.length}`;
    rows.push({ kind: 'section', key: `section:${role}`, label });
    for (const member of inRole) rows.push({ kind: 'member', key: `member:${member.uin}`, member });
  }
  return rows;
}

function MemberRow({ member }: { member: GroupMember }) {
  const name = memberName(member);
  return (
    <div className="flex items-center gap-2 px-3 py-1">
      <PeerAvatar peer={{ scene: 'friend', uin: member.uin }} name={name} size="xs" />
      <span className="min-w-0 flex-1 truncate text-[13px]">{name}</span>
      {member.title && (
        <Chip size="sm" variant="soft" className="shrink-0">
          {member.title}
        </Chip>
      )}
    </div>
  );
}

interface Props {
  group: number;
  /** Beside the messages for good; otherwise it slides over them while open. */
  docked: boolean;
  isOpen: boolean;
  onClose: () => void;
}

/** A group's members, in a column to the right of the conversation. */
export default function MemberPanel({ group, docked, isOpen, onClose }: Props) {
  const members = useQuery(core.contacts.members.queryOptions({ group }));
  const [query, setQuery] = useState('');
  const panel = useRef<HTMLElement>(null);

  // Floating over the messages, it closes on a press anywhere else. The header's toggle is left out: it closes the
  // column itself, and closing here first would have it open again.
  useEffect(() => {
    if (docked || !isOpen) return;
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Element) || panel.current?.contains(target)) return;
      if (target.closest('[data-members-toggle]')) return;
      onClose();
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [docked, isOpen, onClose]);
  const rows = useMemo(() => (members.data ? toRows(members.data, query) : []), [members.data, query]);

  let body: ReactNode;
  if (members.isPending) {
    body = (
      <div className="flex flex-1 items-center justify-center">
        <Spinner />
      </div>
    );
  } else if (members.isError) {
    body = (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 px-4 text-center">
        <p className="text-danger text-sm">{errorMessage(members.error)}</p>
        <Button size="sm" variant="secondary" onPress={() => void members.refetch()} isPending={members.isFetching}>
          重试
        </Button>
      </div>
    );
  } else if (rows.length === 0) {
    body = <p className="px-4 py-8 text-center text-muted text-sm">没有找到成员</p>;
  } else {
    body = (
      <VList data={rows} className="flex-1 pb-2">
        {(row) =>
          row.kind === 'section' ? (
            <p key={row.key} className="px-3 pt-3 pb-1 text-muted text-xs">
              {row.label}
            </p>
          ) : (
            <MemberRow key={row.key} member={row.member} />
          )
        }
      </VList>
    );
  }

  // Closed, the floating column stays mounted so that it can slide out.
  const floating = `absolute inset-y-0 right-0 z-10 transition-[translate] duration-200 ease-out motion-reduce:transition-none ${
    isOpen ? 'shadow-overlay' : 'translate-x-full'
  }`;
  return (
    <aside
      ref={panel}
      aria-label="群成员"
      inert={!docked && !isOpen}
      className={`flex w-52 shrink-0 flex-col border-separator border-l bg-background ${docked ? '' : floating}`}
    >
      <div className="flex h-12 shrink-0 items-center justify-between gap-2 pt-1 pr-2 pl-3">
        <h3 className="font-semibold text-sm">
          成员 {members.data && <span className="font-normal text-muted">{members.data.length}</span>}
        </h3>
        {!docked && (
          <Button variant="ghost" size="sm" isIconOnly aria-label="关闭" onPress={onClose}>
            <X className="size-4" />
          </Button>
        )}
      </div>
      <SearchField aria-label="搜索成员" value={query} onChange={setQuery} className="shrink-0 px-2 pb-1">
        <SearchField.Group>
          <SearchField.SearchIcon />
          <SearchField.Input placeholder="搜索" />
          <SearchField.ClearButton />
        </SearchField.Group>
      </SearchField>
      {body}
    </aside>
  );
}
