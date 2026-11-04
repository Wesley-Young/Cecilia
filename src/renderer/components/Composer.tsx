import { Button } from '@heroui/react';
import { HistoryExtension } from '@lexical/history';
import { PlainTextExtension } from '@lexical/plain-text';
import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { ContentEditable } from '@lexical/react/LexicalContentEditable';
import { LexicalExtensionComposer } from '@lexical/react/LexicalExtensionComposer';
import { useMutation } from '@tanstack/react-query';
import {
  $createParagraphNode,
  $getRoot,
  $getSelection,
  $isElementNode,
  $isRangeSelection,
  CLEAR_HISTORY_COMMAND,
  COMMAND_PRIORITY_HIGH,
  defineExtension,
  KEY_BACKSPACE_COMMAND,
  KEY_ENTER_COMMAND,
  type LexicalEditor,
  PASTE_COMMAND,
} from 'lexical';
import { Reply, X } from 'lucide-react';
import { useEffect, useEffectEvent, useRef, useState } from 'react';

import type { ChatMessage } from '#core/model/message.ts';
import type { Peer } from '#core/model/peer.ts';
import { type ImageFormat, imageFormats, maxImageSize, summarize } from '#core/model/segment.ts';
import { $createImageNode, $isImageNode, ImageNode, type PastedImage } from '@/components/ImageNode';
import { type CoreInputs, core, errorMessage } from '@/lib/rpc';

type Content = CoreInputs['messages']['send']['content'];

/** The message being replied to, above the text. */
function ReplyBar({ message, onCancel }: { message: ChatMessage; onCancel: () => void }) {
  return (
    <div className="mx-3 mt-2 flex shrink-0 items-center gap-2 rounded-lg bg-default py-1 pr-1 pl-3 text-xs">
      <Reply className="size-3.5 shrink-0 text-muted" aria-label="回复" />
      <p className="min-w-0 flex-1 truncate">
        <span className="font-medium">{message.sender.name || message.sender.uin}</span>
        <span className="text-muted">：{summarize(message.segments) || '[消息]'}</span>
      </p>
      <Button variant="ghost" size="sm" isIconOnly aria-label="取消回复" onPress={onCancel} className="size-6 shrink-0">
        <X className="size-3.5" />
      </Button>
    </div>
  );
}

const formatOf = (type: string): ImageFormat | undefined => imageFormats.find((format) => type === `image/${format}`);

/** Reads a pasted file as an image, or says why it cannot be sent. */
async function toPastedImage(file: File): Promise<PastedImage | string> {
  const format = formatOf(file.type);
  if (!format) return '不支持这种图片格式';
  if (file.size > maxImageSize) return '图片不能超过 30 MB';
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    return '无法读取这张图片';
  }
  const { width, height } = bitmap;
  bitmap.close();
  return { file, url: URL.createObjectURL(file), format, width, height };
}

/**
 * The editor's text and images in order, with the outer whitespace trimmed. The images are read later: reading a
 * file is asynchronous, and the editor is cleared as soon as the message is on its way.
 */
function readContent(editor: LexicalEditor) {
  const parts: ({ type: 'text'; text: string } | { type: 'image'; image: PastedImage })[] = [];
  const append = (text: string) => {
    const last = parts.at(-1);
    if (last?.type === 'text') last.text += text;
    else parts.push({ type: 'text', text });
  };
  editor.getEditorState().read(() => {
    for (const [index, block] of $getRoot().getChildren().entries()) {
      if (index > 0) append('\n');
      if (!$isElementNode(block)) continue;
      for (const node of block.getChildren()) {
        if ($isImageNode(node)) parts.push({ type: 'image', image: node.getImage() });
        else append(node.getTextContent());
      }
    }
  });
  const first = parts[0];
  if (first?.type === 'text') first.text = first.text.trimStart();
  const last = parts.at(-1);
  if (last?.type === 'text') last.text = last.text.trimEnd();
  return parts.filter((part) => part.type === 'image' || part.text);
}

/** Whether the caret is before everything, where Backspace removes the reply instead. */
function $caretAtStart(): boolean {
  const selection = $getSelection();
  if (!$isRangeSelection(selection) || !selection.isCollapsed() || selection.anchor.offset !== 0) return false;
  const node = selection.anchor.getNode();
  const first = $getRoot().getFirstChild();
  return node === first || ($isElementNode(first) && node === first.getFirstDescendant());
}

interface Props {
  peer: Peer;
  disabled: boolean;
  replyTo: ChatMessage | undefined;
  onCancelReply: () => void;
}

function Editor({ peer, disabled, replyTo, onCancelReply }: Props) {
  const [editor] = useLexicalComposerContext();
  const send = useMutation(core.messages.send.mutationOptions());
  const [hasContent, setHasContent] = useState(false);
  const [pasteError, setPasteError] = useState<string>();
  const urls = useRef(new Set<string>());
  const canSend = !disabled && hasContent;

  useEffect(() => editor.setEditable(!disabled), [editor, disabled]);
  // Images count as content: they read as their summary.
  useEffect(
    () =>
      editor.registerUpdateListener(({ editorState }) =>
        setHasContent(editorState.read(() => $getRoot().getTextContent().trim().length > 0)),
      ),
    [editor],
  );
  // Undo can bring a sent image back, so the files stay readable as long as the editor is here.
  useEffect(() => {
    const created = urls.current;
    return () => {
      for (const url of created) URL.revokeObjectURL(url);
    };
  }, []);
  // Choosing a message to reply to goes straight on to typing the reply.
  useEffect(() => {
    if (replyTo) editor.focus();
  }, [editor, replyTo]);

  const submit = async () => {
    if (!canSend) return;
    const parts = readContent(editor);
    const reply = replyTo?.seq;
    // Undo does not bring a sent message back.
    editor.update(() => $getRoot().clear().append($createParagraphNode()), {
      onUpdate: () => editor.dispatchCommand(CLEAR_HISTORY_COMMAND, undefined),
    });
    onCancelReply();
    setPasteError(undefined);
    const content: Content = await Promise.all(
      parts.map(async (part) => {
        if (part.type === 'text') return part;
        const { image } = part;
        const data = new Uint8Array(await image.file.arrayBuffer());
        return { type: 'image' as const, data, format: image.format, width: image.width, height: image.height };
      }),
    );
    send.mutate({ peer, content, reply });
  };

  const paste = async (files: File[]) => {
    const results = await Promise.all(files.map(toPastedImage));
    const images = results.filter((result) => typeof result !== 'string');
    setPasteError(results.find((result) => typeof result === 'string'));
    if (images.length === 0) return;
    for (const image of images) urls.current.add(image.url);
    editor.update(() => {
      // The selection is gone when the editor lost focus while the files were read.
      const selection = $getSelection() ?? $getRoot().selectEnd();
      selection.insertNodes(images.map($createImageNode));
    });
  };

  const onEnter = useEffectEvent((event: KeyboardEvent | null) => {
    // Shift+Enter starts a new line; Enter while an IME is composing picks a candidate instead.
    if (!event || event.shiftKey || event.isComposing) return false;
    event.preventDefault();
    void submit();
    return true;
  });
  const onBackspace = useEffectEvent((event: KeyboardEvent) => {
    // The reply sits before the text, so Backspace at the very start deletes it like a character.
    if (!replyTo || !$caretAtStart()) return false;
    event.preventDefault();
    onCancelReply();
    return true;
  });
  const onPaste = useEffectEvent((event: ClipboardEvent | InputEvent | KeyboardEvent) => {
    if (!(event instanceof ClipboardEvent) || !event.clipboardData) return false;
    // Office apps put a picture of what was copied next to the text; the text is what was meant.
    if (event.clipboardData.getData('text/plain')) return false;
    const files = [...event.clipboardData.files].filter((file) => file.type.startsWith('image/'));
    if (files.length === 0) return false;
    event.preventDefault();
    void paste(files);
    return true;
  });
  useEffect(() => {
    const unregister = [
      editor.registerCommand(KEY_ENTER_COMMAND, (event) => onEnter(event), COMMAND_PRIORITY_HIGH),
      editor.registerCommand(KEY_BACKSPACE_COMMAND, (event) => onBackspace(event), COMMAND_PRIORITY_HIGH),
      editor.registerCommand(PASTE_COMMAND, (event) => onPaste(event), COMMAND_PRIORITY_HIGH),
    ];
    return () => {
      for (const done of unregister) done();
    };
  }, [editor]);

  const error = pasteError ?? (send.error ? errorMessage(send.error) : undefined);
  const placeholder = disabled ? '离线中，暂时无法发送' : '输入消息，Enter 发送，Shift + Enter 换行';
  // The whole block is the field: no border or send button, Enter is the way to send.
  // It takes a share of the window's height, within bounds for very short and very tall windows.
  return (
    <div className="flex h-[clamp(8rem,25vh,16rem)] shrink-0 flex-col border-separator border-t bg-surface text-surface-foreground">
      {error && <p className="px-5 pt-2 text-xs text-danger">{error}</p>}
      {/* The reply comes first in the message, so it scrolls with the text instead of staying on top. */}
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
        {replyTo && <ReplyBar message={replyTo} onCancel={onCancelReply} />}
        {/* The text fills the rest, so a click anywhere below the reply lands in it. */}
        <div className="relative flex flex-1 flex-col">
          <ContentEditable
            aria-label="消息"
            aria-placeholder={placeholder}
            placeholder={
              <p className="pointer-events-none absolute top-3 left-5 text-[14px] text-muted leading-relaxed">
                {placeholder}
              </p>
            }
            className={`selectable flex-1 whitespace-pre-wrap wrap-anywhere px-5 py-3 text-[14px] leading-relaxed outline-none ${
              disabled ? 'cursor-not-allowed' : ''
            }`}
          />
        </div>
      </div>
    </div>
  );
}

/** Plain text, which takes images among it, and undo. */
const composerExtension = defineExtension({
  name: '@cecilia/composer',
  namespace: 'composer',
  nodes: [ImageNode],
  dependencies: [PlainTextExtension, HistoryExtension],
  onError: (error) => {
    throw error;
  },
});

/** Text with images among it: Enter sends, Shift+Enter starts a new line, Backspace at the start drops the reply. */
export default function Composer(props: Props) {
  // The editor is placed by `Editor`, below the reply, rather than first in the composer.
  return (
    <LexicalExtensionComposer extension={composerExtension} contentEditable={null}>
      <Editor {...props} />
    </LexicalExtensionComposer>
  );
}
