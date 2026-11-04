import { $createTextNode, type EditorConfig, type LexicalNode, type NodeKey, TextNode } from 'lexical';

/**
 * An @mention in the composer's text, sent as a mention segment. It reads as its text ("@name") and is deleted as a
 * whole; typing next to it starts a new text node.
 */
export class MentionNode extends TextNode {
  /** Null for @全体成员. */
  __uin: number | null;

  static getType(): string {
    return 'mention';
  }

  static clone(node: MentionNode): MentionNode {
    return new MentionNode(node.__uin, node.__text, node.__key);
  }

  // Copied out of the composer, a mention comes back as plain text: the member it names is not part of the copy.
  static importJSON(serialized: Record<string, unknown>): LexicalNode {
    return $createTextNode(typeof serialized.text === 'string' ? serialized.text : '');
  }

  constructor(uin: number | null, text: string, key?: NodeKey) {
    super(text, key);
    this.__uin = uin;
  }

  getUin(): number | null {
    return this.getLatest().__uin;
  }

  /** The name without the '@' it shows with, as a mention is sent. */
  getName(): string {
    return this.getTextContent().replace(/^@/, '');
  }

  createDOM(config: EditorConfig): HTMLElement {
    const element = super.createDOM(config);
    element.classList.add('text-accent');
    return element;
  }

  isTextEntity(): true {
    return true;
  }

  canInsertTextBefore(): boolean {
    return false;
  }

  canInsertTextAfter(): boolean {
    return false;
  }
}

/** A mention of a member, or of everyone when `uin` is null; `name` starts with '@'. */
export const $createMentionNode = (uin: number | null, name: string) => new MentionNode(uin, name).setMode('token');

export const $isMentionNode = (node: LexicalNode | null | undefined): node is MentionNode =>
  node instanceof MentionNode;
