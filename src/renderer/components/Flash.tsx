/** Flashes the row behind a message jumped to from a reply. The parent is `relative isolate`; `className` places it. */
export default function Flash({ className }: { className: string }) {
  return (
    <span
      aria-hidden
      className={`pointer-events-none absolute inset-x-0 -z-10 animate-flash bg-accent/15 ${className}`}
    />
  );
}
