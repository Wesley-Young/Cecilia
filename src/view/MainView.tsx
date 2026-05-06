import type { ScrollBoxRenderable } from '@opentui/core';
import { useKeyboard, useTerminalDimensions } from '@opentui/react';
import { useCallback, useRef, useState } from 'react';

import ContactCard from '../component/ContactCard';
import { useRuntimeCache } from '../shared/cache';
import { contactComparator, friendToBaseContact, groupToBaseContact } from '../shared/transform';
import MessageView from './MessageView';

export default function MainView() {
  const { height } = useTerminalDimensions();
  const { selfInfo, friends, groups, pinned, lastMsg } = useRuntimeCache();

  const pinnedFriendSet = new Set(pinned.friends);
  const pinnedGroupSet = new Set(pinned.groups);
  const contacts = [
    ...Object.values(groups).map((g) => ({
      ...groupToBaseContact(g),
      lastMsg: lastMsg.groups[g.group_id],
      isPinned: pinnedGroupSet.has(g.group_id),
    })),
    ...Object.values(friends).map((f) => ({
      ...friendToBaseContact(f),
      lastMsg: lastMsg.friends[f.user_id],
      isPinned: pinnedFriendSet.has(f.user_id),
    })),
  ].sort(contactComparator);

  const [selectedContact, setSelectedContact] = useState<{ scene: 'friend' | 'group'; uin: number } | null>(null);
  const [activeContact, setActiveContact] = useState<{ scene: 'friend' | 'group'; uin: number } | null>(null);
  const [focused, setFocused] = useState<'contacts' | 'messages' | 'input'>('contacts');
  const contactScrollRef = useRef<ScrollBoxRenderable>(null);

  const switchActiveContact = useCallback((scene: 'friend' | 'group', uin: number) => {
    setActiveContact({ scene, uin });
    setTimeout(() => {
      setFocused('input');
    });
  }, []);

  useKeyboard((e) => {
    if (e.name === 'tab') {
      setFocused((f) => {
        if (f === 'contacts' && activeContact) return 'messages';
        if (f === 'messages') return 'input';
        return 'contacts';
      });
      e.preventDefault();
    }
  });

  useKeyboard((e) => {
    if (focused === 'contacts' && (e.name === 'up' || e.name === 'down' || e.sequence === '\r')) {
      if (e.name === 'up') {
        const currentIndex = contacts.findIndex(
          (c) => c.scene === selectedContact?.scene && c.uin === selectedContact?.uin,
        );
        const prevIndex = currentIndex - 1;
        if (prevIndex < 0 || prevIndex >= contacts.length) {
          return;
        }
        // biome-ignore lint/style/noNonNullAssertion: already checked bounds
        const prevContact = contacts[prevIndex]!;
        setSelectedContact({ scene: prevContact.scene, uin: prevContact.uin });
        contactScrollRef.current?.scrollChildIntoView(`contact-${prevContact.scene}-${prevContact.uin}`);
        e.preventDefault();
      } else if (e.name === 'down') {
        const currentIndex = contacts.findIndex(
          (c) => c.scene === selectedContact?.scene && c.uin === selectedContact?.uin,
        );
        const nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= contacts.length) {
          return;
        }
        // biome-ignore lint/style/noNonNullAssertion: already checked bounds
        const nextContact = contacts[nextIndex]!;
        setSelectedContact({ scene: nextContact.scene, uin: nextContact.uin });
        contactScrollRef.current?.scrollChildIntoView(`contact-${nextContact.scene}-${nextContact.uin}`);
        e.preventDefault();
      } else if (e.sequence === '\r') {
        if (selectedContact) {
          switchActiveContact(selectedContact.scene, selectedContact.uin);
        }
        e.preventDefault();
      }
    }
  });

  return (
    <box flexGrow={1}>
      <box backgroundColor="white" height={1} paddingX={1} alignItems="center">
        <text fg="black">
          <b>{selfInfo.nickname}</b> ({selfInfo.uin})
        </text>
      </box>
      <box flexDirection="row" height={height - 1}>
        <box
          title="Contacts"
          border
          width={30}
          borderColor={focused === 'contacts' ? 'cyan' : undefined}
          onMouseDown={() => setFocused('contacts')}
        >
          <scrollbox ref={contactScrollRef}>
            <box gap={1}>
              {contacts.map((c) => {
                const key = `contact-${c.scene}-${c.uin}`;
                return (
                  <ContactCard
                    id={key}
                    key={key}
                    contact={c}
                    onMouseDown={() => switchActiveContact(c.scene, c.uin)}
                    onMouseOver={() => setSelectedContact({ scene: c.scene, uin: c.uin })}
                    onMouseOut={() => setSelectedContact(null)}
                    active={activeContact?.scene === c.scene && activeContact?.uin === c.uin}
                    selected={selectedContact?.scene === c.scene && selectedContact?.uin === c.uin}
                  />
                );
              })}
            </box>
          </scrollbox>
        </box>
        <box flexGrow={1}>
          <MessageView active={activeContact ?? undefined} focused={focused} setFocused={setFocused} />
        </box>
      </box>
    </box>
  );
}
