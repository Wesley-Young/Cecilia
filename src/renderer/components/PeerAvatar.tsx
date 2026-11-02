import { Avatar } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';

import type { Peer } from '#core/model/peer.ts';
import { avatarUrl, initial } from '@/lib/format';
import { shell } from '@/lib/rpc';

export default function PeerAvatar({ peer, name, size = 'md' }: { peer: Peer; name: string; size?: 'sm' | 'md' }) {
  // The mock's made-up uins belong to real accounts, whose avatars it should not show.
  const info = useQuery(shell.system.info.queryOptions());
  const remote = info.data !== undefined && !info.data.mock;
  return (
    <Avatar size={size} color="accent" variant="soft" className="shrink-0">
      {remote && <Avatar.Image src={avatarUrl(peer)} alt="" loading="lazy" />}
      <Avatar.Fallback>{initial(name)}</Avatar.Fallback>
    </Avatar>
  );
}
