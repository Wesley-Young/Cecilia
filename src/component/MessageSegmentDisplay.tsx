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
      default:
        return <span attributes={TextAttributes.DIM}>[{seg.type}]</span>;
    }
  });
}
