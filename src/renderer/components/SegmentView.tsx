import { useQuery } from '@tanstack/react-query';
import { File, ImageOff, Mic, Video } from 'lucide-react';
import { type ReactNode, useState } from 'react';

import { type Segment, summarize } from '#core/model/segment.ts';
import { useImageViewer } from '@/components/ImageViewer';
import { core } from '@/lib/rpc';

/** Images are scaled down to fit this box, keeping their shape. */
const imageBox = 240;
/** Very long or wide images are cropped to at most this ratio between their long and short sides. */
const maxAspect = 3;
/** Market faces are square. */
const marketFaceSize = 120;
/** Image URLs expire; one this old is fetched again the next time it is shown. */
const imageUrlLifetime = 30 * 60 * 1000;

export type PictureSegment = Extract<Segment, { type: 'image' | 'market-face' }>;

export const isPicture = (segment: Segment): segment is PictureSegment =>
  segment.type === 'image' || segment.type === 'market-face';

/** Pictures take lines of their own, so the line breaks next to them would only leave blank lines. */
export function trimAroundPictures(segments: readonly Segment[]): Segment[] {
  return segments.map((segment, index) => {
    if (segment.type !== 'text') return segment;
    const before = segments[index - 1];
    const after = segments[index + 1];
    let { text } = segment;
    if (before && isPicture(before)) text = text.replace(/^\r?\n/, '');
    if (after && isPicture(after)) text = text.replace(/\r?\n$/, '');
    return { ...segment, text };
  });
}

function fit(width: number, height: number) {
  // Some images come without a size.
  if (width <= 0 || height <= 0) return { width: imageBox / 2, height: imageBox / 2 };
  // The long side shrinks to the box; the short side follows the clamped ratio, and the picture is cropped to it.
  const ratio = Math.min(Math.max(width / height, 1 / maxAspect), maxAspect);
  const long = Math.min(imageBox, Math.max(width, height));
  return ratio >= 1
    ? { width: long, height: Math.round(long / ratio) }
    : { width: Math.round(long * ratio), height: long };
}

/** Keeps its size while loading, so the message list does not jump when the picture arrives; opens in the viewer. */
function Picture(props: {
  src: string | undefined;
  failed: boolean;
  width: number;
  height: number;
  alt: string;
  className: string;
}) {
  const { src, width, height, alt, className } = props;
  const openViewer = useImageViewer();
  const [loaded, setLoaded] = useState<string>();
  const [broken, setBroken] = useState<string>();
  const failed = props.failed || (src !== undefined && src === broken);
  const box = `block max-w-full overflow-hidden ${src === loaded ? '' : 'bg-current/10'} ${className}`;
  const style = { width, aspectRatio: `${width} / ${height}` };
  if (failed) {
    return (
      <span className={`${box} flex items-center justify-center`} style={style}>
        <ImageOff className="size-5 opacity-60" aria-label="图片加载失败" />
      </span>
    );
  }
  const image = src && (
    <img
      src={src}
      alt={alt}
      onLoad={() => setLoaded(src)}
      onError={() => setBroken(src)}
      className="size-full object-cover"
    />
  );
  if (!openViewer || !src || src !== loaded) {
    return (
      <span className={box} style={style}>
        {image}
      </span>
    );
  }
  return (
    <button
      type="button"
      onClick={() => openViewer({ src, alt })}
      className={`${box} cursor-zoom-in outline-none focus-visible:ring-2 focus-visible:ring-focus`}
      style={style}
    >
      {image}
    </button>
  );
}

function ImageView({ segment, className }: { segment: Extract<Segment, { type: 'image' }>; className: string }) {
  const url = useQuery(core.media.imageUrl.queryOptions({ fileId: segment.fileId }, { staleTime: imageUrlLifetime }));
  return (
    <Picture
      src={url.data}
      failed={url.isError}
      {...fit(segment.width, segment.height)}
      alt={segment.summary}
      className={className}
    />
  );
}

/** An image or a market face, on a line of its own. */
export function PictureView({ segment, className }: { segment: PictureSegment; className: string }) {
  if (segment.type === 'image') return <ImageView segment={segment} className={className} />;
  return (
    <Picture
      src={segment.url}
      failed={false}
      width={marketFaceSize}
      height={marketFaceSize}
      alt={segment.summary}
      className={className}
    />
  );
}

/** A placeholder for media the skeleton does not load yet. */
function Placeholder({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-md bg-current/10 px-1.5 py-0.5 align-middle text-[13px]">
      {icon}
      {children}
    </span>
  );
}

export function SegmentView({ segment }: { segment: Segment }) {
  switch (segment.type) {
    case 'text':
      return <>{segment.text}</>;
    case 'mention':
      return <span className="font-medium">{segment.name} </span>;
    case 'face':
      return <span>{segment.summary}</span>;
    case 'image':
    case 'market-face':
      return <PictureView segment={segment} className="my-1 rounded-lg" />;
    case 'record':
      return <Placeholder icon={<Mic className="size-3.5" />}>语音 {segment.duration}″</Placeholder>;
    case 'video':
      return <Placeholder icon={<Video className="size-3.5" />}>视频</Placeholder>;
    case 'file':
      return <Placeholder icon={<File className="size-3.5" />}>{segment.name}</Placeholder>;
    case 'reply':
      // Rendered by the bubble as a quote above the text.
      return null;
    default:
      return <span className="opacity-80">{summarize([segment])}</span>;
  }
}

/** The quoted message above a reply; with `onJump`, it scrolls to that message. */
export function ReplyQuote({ segment, onJump }: { segment: Extract<Segment, { type: 'reply' }>; onJump?: () => void }) {
  const quote = 'mb-1.5 block border-current/40 border-l-2 pl-2 text-xs opacity-75';
  // Spans, since a button only holds phrasing content.
  const content = (
    <>
      {segment.senderName && <span className="block truncate font-medium">{segment.senderName}</span>}
      <span className="line-clamp-2">{summarize(segment.segments) || '[消息]'}</span>
    </>
  );
  if (!onJump) return <div className={quote}>{content}</div>;
  return (
    <button
      type="button"
      onClick={onJump}
      className={`${quote} w-full cursor-pointer text-left outline-none transition-opacity hover:opacity-100 focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-current/60`}
    >
      {content}
    </button>
  );
}
