/** A received message segment. Plain data only, so it survives structured clone and JSON. */
export type Segment =
  | { type: 'text'; text: string }
  /** `uin` is null for @all. `name` already starts with '@'. */
  | { type: 'mention'; uin: number | null; name: string }
  | { type: 'face'; faceId: number; summary: string; isLarge: boolean }
  | {
      type: 'reply';
      seq: number;
      senderUin: number;
      senderName: string | null;
      time: number;
      segments: Segment[];
    }
  | { type: 'image'; fileId: string; width: number; height: number; summary: string }
  | { type: 'record'; fileId: string; duration: number }
  | { type: 'video'; fileId: string; duration: number; width: number; height: number }
  | { type: 'file'; fileId: string; name: string; size: number; hash: string | null }
  | { type: 'forward'; resId: string; title: string; preview: string[]; summary: string }
  | { type: 'market-face'; url: string; summary: string }
  | { type: 'light-app'; appName: string; payload: string }
  | { type: 'markdown'; content: string }
  /** A segment this version does not understand yet; `summary` is what to show. */
  | { type: 'unknown'; summary: string };

export type SegmentType = Segment['type'];

/** The image formats the server takes. */
export const imageFormats = ['png', 'jpeg', 'gif', 'webp', 'bmp', 'tiff'] as const;
export type ImageFormat = (typeof imageFormats)[number];

/** The largest image that can be sent, in bytes. */
export const maxImageSize = 30 * 1024 * 1024;

/** A segment the client can send. */
export type OutgoingSegment =
  | { type: 'text'; text: string }
  | { type: 'mention'; uin: number | null; name: string }
  | { type: 'face'; faceId: number; isLarge?: boolean }
  | { type: 'reply'; seq: number }
  | { type: 'image'; data: Uint8Array; format: ImageFormat; width: number; height: number };

const unreachable = (value: never): never => {
  throw new Error(`Unhandled segment ${JSON.stringify(value)}`);
};

/** One-line text for conversation previews and notifications. */
export function summarize(segments: readonly Segment[]): string {
  return segments
    .map((segment) => {
      switch (segment.type) {
        case 'text':
          return segment.text;
        case 'mention':
          return segment.name;
        case 'face':
        case 'image':
        case 'forward':
        case 'market-face':
        case 'unknown':
          return segment.summary;
        case 'reply':
          return '';
        case 'record':
          return '[语音]';
        case 'video':
          return '[视频]';
        case 'file':
          return `[文件] ${segment.name}`;
        case 'light-app':
          return `[${segment.appName || '小程序'}]`;
        case 'markdown':
          return segment.content;
        default:
          return unreachable(segment);
      }
    })
    .join('')
    .replace(/\s+/g, ' ')
    .trim();
}

/** What an outgoing message looks like locally before the server echoes it back. */
export function toLocalSegments(segments: readonly OutgoingSegment[]): Segment[] {
  return segments.flatMap((segment): Segment[] => {
    switch (segment.type) {
      case 'text':
        return [segment];
      case 'mention':
        return [segment];
      case 'face':
        return [{ type: 'face', faceId: segment.faceId, summary: '[表情]', isLarge: segment.isLarge ?? false }];
      case 'reply':
        // Showing it needs the quoted message, which the store fills in.
        return [];
      case 'image':
        // Showing it needs a file the store saves first.
        return [];
      default:
        return unreachable(segment);
    }
  });
}
