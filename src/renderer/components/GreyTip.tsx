import type { ReactNode } from 'react';

import Flash from '@/components/Flash';

interface Props {
  children: ReactNode;
  /** Just jumped to, such as from a reply: it flashes once. */
  highlighted?: boolean;
  className?: string;
}

/** A centred grey line in the conversation that is not a message itself, such as a recall notice. */
export default function GreyTip({ children, highlighted = false, className = '' }: Props) {
  return (
    <p className={`relative isolate py-2.5 text-center text-muted text-xs ${className}`}>
      {/* 2px past the text on each side. */}
      {highlighted && <Flash className="inset-y-2" />}
      {children}
    </p>
  );
}
