/** biome-ignore-all lint/correctness/useJsxKeyInIterable: the rendered message content is guaranteed to be stable */

import { TextAttributes } from '@opentui/core';
import type { IncomingSegment, OutgoingSegment } from '@saltify/milky-types';

import { facesMap } from '../shared/faces';

export function IncomingSegmentDisplay(segments: IncomingSegment[]) {
  return segments.map((seg) => {
    switch (seg.type) {
      case 'text':
        return seg.data.text;
      case 'mention':
        return <span fg="cyan">{seg.data.name}</span>;
      case 'mention_all':
        return <span fg="cyan">@全体成员</span>;
      case 'face':
        return <span fg="brightYellow">{facesMap.get(seg.data.face_id)?.qDes ?? `/表情#${seg.data.face_id}`}</span>;
      case 'reply':
        return null;
      case 'image':
        return (
          <span fg="brightMagenta">
            {seg.data.summary} ({seg.data.width}x{seg.data.height})
          </span>
        );
      case 'record':
        return <span fg="brightMagenta">[语音] ({seg.data.duration}s)</span>;
      case 'video':
        return (
          <span fg="brightMagenta">
            [视频] ({seg.data.width}x{seg.data.height} {seg.data.duration}s)
          </span>
        );
      case 'file':
        return <span fg="brightMagenta">[文件] {seg.data.file_name}</span>;
      case 'forward':
        return <span fg="brightMagenta">[聊天记录]</span>;
      case 'market_face':
        return <span fg="brightYellow">{seg.data.summary}</span>;
      case 'light_app':
        return <span fg="brightMagenta">[小程序] {seg.data.app_name}</span>;
      default:
        return <span attributes={TextAttributes.DIM}>[{seg.type}]</span>;
    }
  });
}

export function OutgoingSegmentDisplay(segments: OutgoingSegment[]) {
  return segments.map((seg) => {
    switch (seg.type) {
      case 'text':
        return seg.data.text;
      case 'mention':
        return <span fg="cyan">{seg.data.user_id}</span>;
      case 'mention_all':
        return <span fg="cyan">@全体成员</span>;
      case 'face':
        return <span fg="brightYellow">{facesMap.get(seg.data.face_id)?.qDes ?? `/表情#${seg.data.face_id}`}</span>;
      case 'reply':
        return null;
      case 'image':
        return (
          <span fg="brightMagenta">
            {seg.data.summary ?? (seg.data.sub_type === 'normal' ? '[图片]' : '[动画表情]')}
          </span>
        );
      case 'record':
        return <span fg="brightMagenta">[语音]</span>;
      case 'video':
        return <span fg="brightMagenta">[视频]</span>;
      case 'forward':
        return <span fg="brightMagenta">[聊天记录]</span>;
      case 'light_app':
        return <span fg="brightMagenta">[小程序]</span>;
      default:
        // @ts-expect-error exhaustive check
        return <span attributes={TextAttributes.DIM}>[{seg.type}]</span>;
    }
  });
}
