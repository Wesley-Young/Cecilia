import { pinyin } from 'pinyin-pro';

/** What a contact or group member is found by. */
export interface SearchFields {
  /** Remark, nickname, group card, group name: whichever the entry has. Empty ones are skipped. */
  names: readonly string[];
  /** The QQ number or group number; absent for entries without one, such as 全体成员. */
  number?: number;
}

/** How well an entry matches; lower is better. */
const MatchRank = {
  /** A whole name, or the whole number. */
  Exact: 0,
  /** From the start of a name or the number. */
  Start: 1,
  /** Anywhere else. */
  Inside: 2,
} as const;
type MatchRank = (typeof MatchRank)[keyof typeof MatchRank];

/** A name, prepared for matching: one entry per character, holding every way the character can be typed. */
interface PreparedName {
  text: string;
  units: readonly (readonly string[])[];
}

const han = /\p{Script=Han}/u;
/** Only letters and digits are spelled: 星野 | 前端 is spelled xyqd. */
const spelledChar = /[\p{L}\p{N}]/u;
/** Every reading of one character, cached: names repeat a small set of characters. */
const readingCache = new Map<string, readonly string[]>();

/**
 * A Chinese character can be typed as itself or as any of its readings, in pinyin without tones. ü is written as v,
 * as input methods do, and as u, as people often do. Any other character is typed as itself.
 */
function readingsOf(char: string): readonly string[] {
  const cached = readingCache.get(char);
  if (cached) return cached;
  let readings: readonly string[] = [char.toLowerCase()];
  if (han.test(char)) {
    const spelled = pinyin(char, { toneType: 'none', type: 'array', multiple: true, v: true });
    readings = [...new Set([char, ...spelled, ...spelled.map((reading) => reading.replaceAll('v', 'u'))])];
  }
  readingCache.set(char, readings);
  return readings;
}

function prepare(name: string): PreparedName {
  const chars = Array.from(name).filter((char) => spelledChar.test(char));
  return { text: name.toLowerCase(), units: chars.map(readingsOf) };
}

/**
 * Whether `query` spells the characters from `start` on, each by a prefix of one of its readings: so 星野 is found
 * by its initials (xy), its full pinyin (xingye), and anything between (xingy, xye).
 */
function spells(units: PreparedName['units'], query: string, start: number): boolean {
  const from = (unit: number, at: number): boolean => {
    if (at === query.length) return true;
    const readings = units[unit];
    if (!readings) return false;
    for (const reading of readings) {
      // Longest first: a whole syllable is the likelier intent than its first letter.
      for (let length = Math.min(reading.length, query.length - at); length > 0; length--) {
        if (query.startsWith(reading.slice(0, length), at) && from(unit + 1, at + length)) return true;
      }
    }
    return false;
  };
  return from(start, 0);
}

function rankName(name: PreparedName, query: string, spelled: string): MatchRank | undefined {
  if (name.text === query) return MatchRank.Exact;
  if (name.text.startsWith(query)) return MatchRank.Start;
  if (spelled && spells(name.units, spelled, 0)) return MatchRank.Start;
  if (name.text.includes(query)) return MatchRank.Inside;
  for (let start = 1; spelled && start < name.units.length; start++) {
    if (spells(name.units, spelled, start)) return MatchRank.Inside;
  }
  return undefined;
}

function rankNumber(number: string, query: string): MatchRank | undefined {
  if (number === query) return MatchRank.Exact;
  if (number.startsWith(query)) return MatchRank.Start;
  if (number.includes(query)) return MatchRank.Inside;
  return undefined;
}

interface Entry<T> {
  item: T;
  names: readonly PreparedName[];
  /** Empty without a number. */
  number: string;
}

/**
 * Finds contacts or group members by name, by the pinyin of a name (initials, full, or a mix of both), and by
 * number. Preparing the names is the costly part, so build one index per list and keep it while the list stays.
 */
export class SearchIndex<T> {
  private readonly entries: readonly Entry<T>[];

  constructor(items: readonly T[], fields: (item: T) => SearchFields) {
    this.entries = items.map((item) => {
      const { names, number } = fields(item);
      return { item, names: names.filter(Boolean).map(prepare), number: number === undefined ? '' : String(number) };
    });
  }

  /** How well `item`'s entry matches; undefined when it does not. */
  private rank(entry: Entry<T>, query: string, spelled: string): MatchRank | undefined {
    let best: MatchRank | undefined;
    for (const name of entry.names) {
      const rank = rankName(name, query, spelled);
      if (rank !== undefined && (best === undefined || rank < best)) best = rank;
    }
    if (entry.number && /^\d+$/.test(query)) {
      const rank = rankNumber(entry.number, query);
      if (rank !== undefined && (best === undefined || rank < best)) best = rank;
    }
    return best;
  }

  /** The items matching `query`, best first and otherwise in their original order; all of them for a blank query. */
  search(query: string): T[] {
    const needle = query.trim().toLowerCase();
    if (!needle) return this.entries.map((entry) => entry.item);
    // Spaces between syllables are allowed when spelling.
    const spelled = needle.replace(/\s+/g, '');
    return this.entries
      .flatMap((entry) => {
        const rank = this.rank(entry, needle, spelled);
        return rank === undefined ? [] : [{ item: entry.item, rank }];
      })
      .sort((a, b) => a.rank - b.rank)
      .map((match) => match.item);
  }
}
