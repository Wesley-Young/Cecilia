import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { useLexicalNodeSelection } from '@lexical/react/useLexicalNodeSelection';
import {
  $createTextNode,
  $getNodeByKey,
  $getSelection,
  $isNodeSelection,
  CLICK_COMMAND,
  COMMAND_PRIORITY_LOW,
  DecoratorNode,
  KEY_BACKSPACE_COMMAND,
  KEY_DELETE_COMMAND,
  type LexicalNode,
  type NodeKey,
} from 'lexical';
import { type ReactNode, useEffect, useRef } from 'react';

import type { ImageFormat } from '#core/model/segment.ts';

/** What the composer shows in place of an image, and what one reads as when copied out as text. */
const imageSummary = '[图片]';

/** A picture put into the composer, kept as the file it came from until it is sent. */
export interface PastedImage {
  file: Blob;
  /** An object URL of `file`, revoked by the composer. */
  url: string;
  format: ImageFormat;
  width: number;
  height: number;
}

function PastedImageView({ nodeKey, image }: { nodeKey: NodeKey; image: PastedImage }) {
  const [editor] = useLexicalComposerContext();
  const [selected, setSelected, clearSelection] = useLexicalNodeSelection(nodeKey);
  const element = useRef<HTMLImageElement>(null);

  // A click selects the picture, and Backspace or Delete then removes it.
  useEffect(() => {
    const remove = (event: KeyboardEvent | null) => {
      const selection = $getSelection();
      if (!$isNodeSelection(selection) || !selection.has(nodeKey)) return false;
      event?.preventDefault();
      $getNodeByKey(nodeKey)?.remove();
      return true;
    };
    const unregister = [
      editor.registerCommand(
        CLICK_COMMAND,
        (event) => {
          if (event.target !== element.current) return false;
          if (!event.shiftKey) clearSelection();
          setSelected(true);
          return true;
        },
        COMMAND_PRIORITY_LOW,
      ),
      editor.registerCommand(KEY_BACKSPACE_COMMAND, remove, COMMAND_PRIORITY_LOW),
      editor.registerCommand(KEY_DELETE_COMMAND, remove, COMMAND_PRIORITY_LOW),
    ];
    return () => {
      for (const done of unregister) done();
    };
  }, [editor, nodeKey, setSelected, clearSelection]);

  return (
    <img
      ref={element}
      src={image.url}
      alt={imageSummary}
      draggable={false}
      className={`inline-block max-h-24 max-w-40 rounded-md align-bottom ${selected ? 'ring-2 ring-focus' : ''}`}
    />
  );
}

/** An image in the composer's text, sent as an image segment where it stands. */
export class ImageNode extends DecoratorNode<ReactNode> {
  __image: PastedImage;

  static getType(): string {
    return 'pasted-image';
  }

  static clone(node: ImageNode): ImageNode {
    return new ImageNode(node.__image, node.__key);
  }

  // The file stays with the node, so an image copied out of the composer comes back as its summary.
  static importJSON(): LexicalNode {
    return $createTextNode(imageSummary);
  }

  constructor(image: PastedImage, key?: NodeKey) {
    super(key);
    this.__image = image;
  }

  getImage(): PastedImage {
    return this.getLatest().__image;
  }

  createDOM(): HTMLElement {
    return document.createElement('span');
  }

  updateDOM(): boolean {
    return false;
  }

  isInline(): boolean {
    return true;
  }

  getTextContent(): string {
    return imageSummary;
  }

  decorate(): ReactNode {
    return <PastedImageView nodeKey={this.__key} image={this.__image} />;
  }
}

export const $createImageNode = (image: PastedImage) => new ImageNode(image);

export const $isImageNode = (node: LexicalNode | null | undefined): node is ImageNode => node instanceof ImageNode;
