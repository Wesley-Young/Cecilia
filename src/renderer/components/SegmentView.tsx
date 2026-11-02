import { File, Image, Mic, Video } from 'lucide-react';
import type { ReactNode } from 'react';

import { type Segment, summarize } from '#core/model/segment.ts';

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
      return <Placeholder icon={<Image className="size-3.5" />}>{segment.summary || '图片'}</Placeholder>;
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

export function ReplyQuote({ segment }: { segment: Extract<Segment, { type: 'reply' }> }) {
  return (
    <div className="mb-1.5 border-current/40 border-l-2 pl-2 text-xs opacity-75">
      {segment.senderName && <span className="font-medium">{segment.senderName}: </span>}
      <span className="line-clamp-2">{summarize(segment.segments) || '[消息]'}</span>
    </div>
  );
}
