import { useKeyboard, useTerminalDimensions } from '@opentui/react';
import { useCallback, useEffect, useState } from 'react';
import { type Updater, useImmer } from 'use-immer';

import ContactCard from '../component/ContactCard';
import { IncomingSegmentDisplay } from '../component/MessageSegmentDisplay';
import type { Contact } from '../shared/model';
import { defineMilkyListener, useMilky, useMilkyEvent } from '../shared/protocol';
import { contactComparator, friendToBaseContact, groupToBaseContact } from '../shared/transform';
import MessageView from './MessageView';

export default function MainView() {
  const { height } = useTerminalDimensions();
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [contacts, rawSetContacts] = useImmer<Contact[]>([]);
  const [selectedContact, setSelectedContact] = useState<{ scene: 'friend' | 'group'; uin: number } | null>(null);
  const [activeContact, setActiveContact] = useState<{ scene: 'friend' | 'group'; uin: number } | null>(null);
  const [focused, setFocused] = useState<'contacts' | 'messages' | 'input'>('contacts');

  useKeyboard((e) => {
    if (e.name === 'tab') {
      setFocused((f) => {
        if (f === 'contacts') return 'messages';
        if (f === 'messages') return 'input';
        return 'contacts';
      });
      e.preventDefault();
    }
  });

  const setContacts = useCallback<Updater<Contact[]>>(
    (contactsOrFunc) => {
      if (Array.isArray(contactsOrFunc)) {
        const sortedContacts = contactsOrFunc.toSorted(contactComparator);
        rawSetContacts(sortedContacts);
      } else {
        // is a function
        rawSetContacts((draft) => {
          contactsOrFunc(draft);
          draft.sort(contactComparator);
        });
      }
    },
    [rawSetContacts],
  );

  const resolveBaseContact = useCallback(
    async (scene: 'friend' | 'group', uin: number): Promise<Contact | null> => {
      try {
        if (scene === 'friend') {
          const { friend } = await milky.system.getFriendInfo({ user_id: uin, no_cache: false });
          return friendToBaseContact(friend);
        } else {
          const { group } = await milky.system.getGroupInfo({ group_id: uin, no_cache: false });
          return groupToBaseContact(group);
        }
      } catch {
        return null;
      }
    },
    [milky],
  );

  const upsertContact = useCallback(
    (scene: 'friend' | 'group', uin: number, contact: Partial<Contact>) => {
      const contactIndex = contacts.findIndex((c) => c.scene === scene && c.uin === uin);
      if (contactIndex !== -1) {
        // update existing contact
        setContacts((contacts) => {
          // @ts-expect-error
          contacts[contactIndex] = { ...contacts[contactIndex], ...contact };
        });
      } else {
        resolveBaseContact(scene, uin).then((baseContact) => {
          if (baseContact) {
            setContacts((contacts) => {
              contacts.push({ ...baseContact, ...contact });
            });
          }
        });
      }
    },
    [setContacts, resolveBaseContact, contacts],
  );

  const switchActiveContact = useCallback((scene: 'friend' | 'group', uin: number) => {
    setActiveContact({ scene, uin });
    setTimeout(() => {
      setFocused('messages');
    });
  }, []);

  useEffect(() => {
    (async () => {
      const preloadedContacts: Contact[] = [];

      const { friends: pinnedFriends, groups: pinnedGroups } = await milky.system.getPeerPins();
      const friendMap = await milky.system.getFriendList({ no_cache: false }).then((res) => {
        return new Map(res.friends.map((f) => [f.user_id, f]));
      });
      const groupMap = await milky.system.getGroupList({ no_cache: false }).then((res) => {
        return new Map(res.groups.map((g) => [g.group_id, g]));
      });

      for (const group of pinnedGroups) {
        const info = groupMap.get(group.group_id);
        if (info) {
          preloadedContacts.push({
            ...groupToBaseContact(info),
            isPinned: true,
          });
        }
      }

      for (const friend of pinnedFriends) {
        const info = friendMap.get(friend.user_id);
        if (info) {
          preloadedContacts.push({
            ...friendToBaseContact(info),
            isPinned: true,
          });
        }
      }

      setContacts(preloadedContacts);
    })();
  }, [milky, setContacts]);

  useEffect(() => {
    const messageListener = defineMilkyListener('message_receive', ({ data }) => {
      if (data.message_scene === 'temp') {
        return;
      }
      upsertContact(data.message_scene, data.peer_id, {
        lastMsg: {
          time: data.time,
          content: <IncomingSegmentDisplay segments={data.segments} noFg />,
        },
      });
    });

    const pinListener = defineMilkyListener('peer_pin_change', ({ data }) => {
      if (data.message_scene === 'temp') {
        return;
      }
      if (data.is_pinned) {
        upsertContact(data.message_scene, data.peer_id, {
          isPinned: true,
        });
      } else {
        if (contacts.find((c) => c.scene === data.message_scene && c.uin === data.peer_id)) {
          upsertContact(data.message_scene, data.peer_id, {
            isPinned: false,
          });
        }
      }
    });

    eventSource.on('message_receive', messageListener);
    eventSource.on('peer_pin_change', pinListener);
    return () => {
      eventSource.off('message_receive', messageListener);
      eventSource.off('peer_pin_change', pinListener);
    };
  }, [eventSource, upsertContact, contacts]);

  useKeyboard((e) => {
    if (focused === 'contacts' && (e.name === 'up' || e.name === 'down' || e.sequence === '\r')) {
      if (e.name === 'up') {
        const currentIndex = contacts.findIndex((c) => c.scene === selectedContact?.scene && c.uin === selectedContact?.uin);
        const prevIndex = currentIndex - 1;
        if (prevIndex < 0 || prevIndex >= contacts.length) {
          return;
        }
        // biome-ignore lint/style/noNonNullAssertion: already checked bounds
        const prevContact = contacts[prevIndex]!;
        setSelectedContact({ scene: prevContact.scene, uin: prevContact.uin });
        e.preventDefault();
      } else if (e.name === 'down') {
        const currentIndex = contacts.findIndex((c) => c.scene === selectedContact?.scene && c.uin === selectedContact?.uin);
        const nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= contacts.length) {
          return;
        }
        // biome-ignore lint/style/noNonNullAssertion: already checked bounds
        const nextContact = contacts[nextIndex]!;
        setSelectedContact({ scene: nextContact.scene, uin: nextContact.uin });
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
      <box backgroundColor="white" height={1}>
        <text fg="black">
          {' '}
          <b>Cecilia</b> - <b>Tab</b> or click to switch focus
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
          <scrollbox>
            <box gap={1}>
              {contacts.map((c) => (
                <ContactCard
                  key={`${c.scene}-${c.uin}`}
                  contact={c}
                  onMouseDown={() => {
                    switchActiveContact(c.scene, c.uin);
                  }}
                  active={activeContact?.scene === c.scene && activeContact?.uin === c.uin}
                  selected={selectedContact?.scene === c.scene && selectedContact?.uin === c.uin}
                />
              ))}
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
