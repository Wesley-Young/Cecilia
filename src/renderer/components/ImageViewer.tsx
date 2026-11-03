import { Button, Modal } from '@heroui/react';
import { X } from 'lucide-react';
import {
  createContext,
  type PointerEvent,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';

interface Shown {
  src: string;
  alt: string;
}

/** Scale is relative to the picture fitted in the window. */
const minScale = 0.25;
const maxScale = 20;
/** How much one pixel of wheel movement zooms; a pinch reports far smaller deltas than a wheel. */
const wheelSpeed = 0.0015;
const pinchSpeed = 0.01;

const ViewerContext = createContext<((shown: Shown) => void) | undefined>(undefined);

/** Opens a picture in the viewer; undefined outside a provider, where pictures are not clickable. */
export const useImageViewer = () => useContext(ViewerContext);

/**
 * Hosts the viewer above a list whose rows come and go: a picture that scrolls out of the list while it is open
 * does not close it.
 */
export function ImageViewerProvider({ children }: { children: ReactNode }) {
  const [shown, setShown] = useState<Shown>();
  return (
    <ViewerContext value={setShown}>
      {children}
      <Modal.Backdrop
        isOpen={shown !== undefined}
        onOpenChange={(open) => !open && setShown(undefined)}
        className="app-no-drag bg-viewer"
      >
        <Modal.Container size="full">
          <Modal.Dialog
            aria-label="图片预览"
            className="size-full max-w-none rounded-none bg-transparent p-0 shadow-none"
          >
            {shown && <Stage {...shown} onClose={() => setShown(undefined)} />}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </ViewerContext>
  );
}

interface View {
  scale: number;
  /** Offset of the picture's center from the window's center. */
  x: number;
  y: number;
}

const fitted: View = { scale: 1, x: 0, y: 0 };

/** The picture, fitted in the window; drag to move it, wheel or pinch to zoom around the pointer. */
function Stage({ src, alt, onClose }: Shown & { onClose: () => void }) {
  const stage = useRef<HTMLDivElement>(null);
  const [view, setView] = useState(fitted);
  const [drag, setDrag] = useState<{ pointerX: number; pointerY: number; x: number; y: number }>();

  /** Scales by `factor`, keeping the point under the pointer in place. */
  const zoom = useCallback((factor: number, clientX: number, clientY: number) => {
    const rect = stage.current?.getBoundingClientRect();
    if (!rect) return;
    const px = clientX - rect.left - rect.width / 2;
    const py = clientY - rect.top - rect.height / 2;
    setView((current) => {
      const scale = Math.min(Math.max(current.scale * factor, minScale), maxScale);
      const ratio = scale / current.scale;
      return { scale, x: px - (px - current.x) * ratio, y: py - (py - current.y) * ratio };
    });
  }, []);

  // React's wheel listener is passive, so it cannot keep the page (or Chromium's own pinch zoom) from reacting.
  useEffect(() => {
    const element = stage.current;
    if (!element) return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      // Trackpad pinches arrive as wheel events with ctrlKey set; line-based deltas come from some mice.
      const delta = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? event.deltaY * 16 : event.deltaY;
      zoom(Math.exp(-delta * (event.ctrlKey ? pinchSpeed : wheelSpeed)), event.clientX, event.clientY);
    };
    element.addEventListener('wheel', onWheel, { passive: false });
    return () => element.removeEventListener('wheel', onWheel);
  }, [zoom]);

  const onPointerDown = (event: PointerEvent<HTMLImageElement>) => {
    if (event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    setDrag({ pointerX: event.clientX, pointerY: event.clientY, x: view.x, y: view.y });
  };
  const onPointerMove = (event: PointerEvent<HTMLImageElement>) => {
    if (!drag) return;
    setView((current) => ({
      ...current,
      x: drag.x + event.clientX - drag.pointerX,
      y: drag.y + event.clientY - drag.pointerY,
    }));
  };
  const onPointerUp = () => setDrag(undefined);

  return (
    // Clicking the dimmed area around the picture closes the viewer; Esc does too.
    <div
      ref={stage}
      className="relative flex size-full items-center justify-center overflow-hidden"
      onClick={(event) => event.target === event.currentTarget && onClose()}
    >
      <img
        src={src}
        alt={alt}
        draggable={false}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        // Double-click returns to the fitted view, or zooms in from it.
        onDoubleClick={(event) => (view === fitted ? zoom(2, event.clientX, event.clientY) : setView(fitted))}
        className={`max-h-[90%] max-w-[90%] touch-none select-none ${drag ? 'cursor-grabbing' : 'cursor-grab'}`}
        style={{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.scale})` }}
      />
      <Button
        variant="ghost"
        size="sm"
        isIconOnly
        aria-label="关闭"
        onPress={onClose}
        className="absolute top-3 right-3 text-viewer-foreground"
      >
        <X className="size-5" />
      </Button>
    </div>
  );
}
