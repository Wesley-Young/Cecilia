import type { TextProps } from '@opentui/react';

export type LimitedLineTextProps = TextProps & {
  maxLines?: number;
};

export default function LimitedLineText(props: LimitedLineTextProps) {
  return (
    <box maxHeight={props.maxLines ?? 1}>
      <text {...props} truncate />
    </box>
  );
}
