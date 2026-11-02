import type { Peer } from '#core/model/peer.ts';

export function avatarUrl(peer: Peer): string {
  return peer.scene === 'group'
    ? `https://p.qlogo.cn/gh/${peer.uin}/${peer.uin}/100`
    : `https://q1.qlogo.cn/g?b=qq&nk=${peer.uin}&s=100`;
}

const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

const pad = (value: number) => String(value).padStart(2, '0');

/** Short time for the conversation list: the clock today, 昨天, then the date. */
export function listTime(seconds: number): string {
  if (!seconds) return '';
  const date = new Date(seconds * 1000);
  const now = new Date();
  if (sameDay(date, now)) return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(date, yesterday)) return '昨天';
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}/${date.getDate()}`;
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`;
}

/** Full time for the separators in a conversation. */
export function messageTime(seconds: number): string {
  const date = new Date(seconds * 1000);
  const clock = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  return sameDay(date, new Date()) ? clock : `${listTime(seconds)} ${clock}`;
}

/** First character for avatar fallbacks. */
export function initial(name: string): string {
  return Array.from(name.trim())[0] ?? '?';
}
