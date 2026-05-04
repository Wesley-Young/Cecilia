import type { BoxProps } from '@opentui/react';
import { useState } from 'react';

export type InteractableBoxProps = BoxProps & {
  hoveredColor?: string;
  activeColor?: string;
  isActive?: boolean;
};

export default function InteractableBox(props: InteractableBoxProps) {
  const [isHovered, setHovered] = useState(false);

  const hoveredColor = props.hoveredColor || 'cyan';
  const activeColor = props.activeColor || 'brightGreen';

  return (
    <box
      {...props}
      borderColor={props.isActive ? activeColor : isHovered ? hoveredColor : props.borderColor}
      onMouseOver={function (e) {
        setHovered(true);
        props.onMouseOver?.call(this, e);
      }}
      onMouseOut={function (e) {
        setHovered(false);
        props.onMouseOut?.call(this, e);
      }}
    />
  );
}
