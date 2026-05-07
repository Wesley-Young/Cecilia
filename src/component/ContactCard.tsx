import { TextAttributes } from '@opentui/core';
import type { BoxProps } from '@opentui/react';

import type { Contact } from '../shared/model';
import { formatShortDateTime } from '../shared/transform';
import LimitedLineText from './LimitedLineText';
import MessageSegmentDisplay from './MessageSegmentDisplay';

export type ContactCardProps = BoxProps & {
  contact: Contact;
  selected?: boolean;
  active?: boolean;
};

export default function ContactCard(props: ContactCardProps) {
  const c = props.contact;
  const defaultBg = c.isPinned ? '#202020' : undefined;
  const activeColor = c.scene === 'friend' ? 'cyan' : 'brightGreen';
  const bg = props.active ? activeColor : defaultBg;
  const fg = props.active ? 'black' : props.selected ? activeColor : undefined;

  return (
    <box paddingX={1} backgroundColor={bg} {...props}>
      <box flexDirection="row" gap={1}>
        <box flexGrow={1}>
          <LimitedLineText attributes={c.isPinned ? TextAttributes.BOLD : undefined} fg={fg}>
            {c.displayName}
          </LimitedLineText>
        </box>
        {c.lastMsg && (
          <LimitedLineText attributes={TextAttributes.DIM} fg={fg}>
            {formatShortDateTime(c.lastMsg.time)}
          </LimitedLineText>
        )}
      </box>
      {c.lastMsg ? (
        <LimitedLineText attributes={TextAttributes.DIM} maxLines={2} fg={fg}>
          <MessageSegmentDisplay content={c.lastMsg.content} noFg />
        </LimitedLineText>
      ) : (
        <text attributes={TextAttributes.DIM} fg={fg}>
          No messages yet
        </text>
      )}
    </box>
  );
}
