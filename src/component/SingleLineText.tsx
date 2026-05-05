import type { TextProps } from '@opentui/react';

export type SingleLineTextProps = TextProps;

export default function SingleLineText(props: SingleLineTextProps) {
  return (
    <box height={1}>
      <text {...props} truncate />
    </box>
  );
}
