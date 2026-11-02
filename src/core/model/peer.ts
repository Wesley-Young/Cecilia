export type Scene = 'friend' | 'group';

/** A conversation partner: a friend or a group, identified by its uin. */
export interface Peer {
  scene: Scene;
  uin: number;
}

/** A stable string key for maps and React lists. */
export function peerKey(peer: Peer): string {
  return `${peer.scene}:${peer.uin}`;
}

export function samePeer(a: Peer, b: Peer): boolean {
  return a.scene === b.scene && a.uin === b.uin;
}
