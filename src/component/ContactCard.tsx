import { TextAttributes } from '@opentui/core';
import type { BoxProps } from '@opentui/react';
import { useState } from 'react';

import type { Contact } from '../model';
import SingleLineText from './SingleLineText';

export type ContactCardProps = BoxProps & {
  contact: Contact;
  isActive?: boolean;
};

export default function ContactCard(props: ContactCardProps) {
  const c = props.contact;
  const [isHovered, setHovered] = useState(false);
  const fg = isHovered ? 'cyan' : undefined;

  return (
    <box
      paddingX={1}
      onMouseOver={() => setHovered(true)}
      onMouseOut={() => setHovered(false)}
      {...props}
    >
      <SingleLineText attributes={TextAttributes.BOLD} fg={fg}>{c.displayName}</SingleLineText>
      <box flexDirection="row" justifyContent="space-between">
        <text attributes={TextAttributes.DIM}>{c.lastMsg ? c.lastMsg.content : 'No messages yet'}</text>
        {c.isPinned && <text>📌</text>}
      </box>
    </box>
  );
}
