import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { LexicalTypeaheadMenuPlugin, MenuOption, type TriggerFn } from '@lexical/react/LexicalTypeaheadMenuPlugin';
import { useQuery } from '@tanstack/react-query';
import { $createTextNode, $insertNodes, type TextNode } from 'lexical';
import { Users } from 'lucide-react';
import { type RefObject, useEffect, useMemo, useState } from 'react';

import { type GroupMember, memberName } from '#core/model/contact.ts';
import { $createMentionNode } from '@/components/MentionNode';
import PeerAvatar from '@/components/PeerAvatar';
import { sortMembers } from '@/lib/members';
import { core } from '@/lib/rpc';
import { SearchIndex } from '@/lib/search';

/** The menu lists at most this many members; typing more of the name narrows it down. */
const maxOptions = 50;
/** An @ starts a mention wherever it is typed, and the name after it runs to the next space. */
const mentionTrigger: TriggerFn = (text) => {
  const match = /@([^\s@]{0,32})$/.exec(text);
  if (!match) return null;
  return { leadOffset: match.index, matchingString: match[1] ?? '', replaceableString: match[0] };
};

type Target = { kind: 'everyone' } | { kind: 'member'; member: GroupMember };

class MentionOption extends MenuOption {
  readonly target: Target;

  constructor(target: Target) {
    super(target.kind === 'everyone' ? 'everyone' : String(target.member.uin));
    this.target = target;
  }
}

function OptionContent({ target }: { target: Target }) {
  if (target.kind === 'everyone') {
    return (
      <>
        <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-default">
          <Users className="size-3.5" />
        </span>
        <span className="min-w-0 flex-1 truncate">全体成员</span>
      </>
    );
  }
  const { member } = target;
  const name = memberName(member);
  return (
    <>
      <PeerAvatar peer={{ scene: 'friend', uin: member.uin }} name={name} size="xs" />
      <span className="min-w-0 flex-1 truncate">{name}</span>
      <span className="shrink-0 font-mono text-[11px] text-muted">{member.uin}</span>
    </>
  );
}

interface ListProps {
  options: MentionOption[];
  selectedIndex: number | null;
  onSelect: (option: MentionOption) => void;
  onHighlight: (index: number) => void;
}

/** The members to pick from, above the composer. */
function MentionList({ options, selectedIndex, onSelect, onHighlight }: ListProps) {
  // The plugin scrolls only lists inside its own anchor, which is at the caret; this one is not, so it scrolls itself
  // to keep the member picked with the arrow keys in view.
  useEffect(() => {
    if (selectedIndex !== null) options[selectedIndex]?.ref?.current?.scrollIntoView({ block: 'nearest' });
  }, [options, selectedIndex]);

  return (
    <div
      role="listbox"
      aria-label="提及成员"
      className="app-no-drag absolute bottom-full left-3 z-20 mb-2 max-h-72 w-64 overflow-y-auto rounded-xl bg-overlay p-1 text-overlay-foreground shadow-overlay"
    >
      {options.map((option, index) => (
        // The editor keeps focus and handles the keys; the pointer only highlights and picks.
        <div
          key={option.key}
          ref={(element) => option.setRefElement(element)}
          role="option"
          tabIndex={-1}
          aria-selected={selectedIndex === index}
          onMouseEnter={() => onHighlight(index)}
          // Keeps the caret in the editor.
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => onSelect(option)}
          className={`flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-[13px] ${
            selectedIndex === index ? 'bg-default' : ''
          }`}
        >
          <OptionContent target={option.target} />
        </div>
      ))}
    </div>
  );
}

interface Props {
  group: number;
  /** Set while the menu shows members, when Enter picks one instead of sending. */
  active: RefObject<boolean>;
}

/**
 * Typing @ in a group lists its members, found by the same search as the member panel; picking one puts a mention in
 * the text. Admins and the owner can also mention everyone.
 */
export default function MentionMenu({ group, active }: Props) {
  const [editor] = useLexicalComposerContext();
  const members = useQuery(core.contacts.members.queryOptions({ group }));
  const login = useQuery(core.login.state.queryOptions());
  const self = login.data?.kind === 'online' ? login.data.self.uin : undefined;
  const [query, setQuery] = useState<string | null>(null);

  const index = useMemo(() => {
    const list = members.data ?? [];
    const me = list.find((member) => member.uin === self);
    const targets: Target[] = [
      ...(me && me.role !== 'member' ? [{ kind: 'everyone' } as const] : []),
      // Listed like the member panel, without its headings; matches that are equally good keep this order. Mentioning
      // oneself notifies no one.
      ...sortMembers(list.filter((member) => member.uin !== self)).map(
        (member) => ({ kind: 'member', member }) as const,
      ),
    ];
    return new SearchIndex(targets, (target) =>
      target.kind === 'everyone'
        ? { names: ['全体成员'] }
        : { names: [target.member.card, target.member.nickname], number: target.member.uin },
    );
  }, [members.data, self]);
  const options = useMemo(
    () =>
      query === null
        ? []
        : index
            .search(query)
            .slice(0, maxOptions)
            .map((target) => new MentionOption(target)),
    [index, query],
  );

  useEffect(() => {
    active.current = options.length > 0;
    return () => {
      active.current = false;
    };
  }, [active, options]);

  const select = (option: MentionOption, typed: TextNode | null, close: () => void) => {
    editor.update(() => {
      const { target } = option;
      const mention =
        target.kind === 'everyone'
          ? $createMentionNode(null, '@全体成员')
          : $createMentionNode(target.member.uin, `@${memberName(target.member)}`);
      if (typed) typed.replace(mention);
      else $insertNodes([mention]);
      // A space after it, as QQ puts one, so what is typed next is not part of the mention.
      const space = $createTextNode(' ');
      mention.insertAfter(space);
      space.select();
    });
    close();
  };

  return (
    <LexicalTypeaheadMenuPlugin<MentionOption>
      triggerFn={mentionTrigger}
      onQueryChange={setQuery}
      onSelectOption={select}
      options={options}
      // Shown above the composer, which sits at the bottom of the window, rather than at the caret.
      menuRenderFn={(_anchor, { options: shown, selectedIndex, selectOptionAndCleanUp, setHighlightedIndex }) =>
        shown.length === 0 ? null : (
          <MentionList
            options={shown}
            selectedIndex={selectedIndex}
            onSelect={selectOptionAndCleanUp}
            onHighlight={setHighlightedIndex}
          />
        )
      }
    />
  );
}
