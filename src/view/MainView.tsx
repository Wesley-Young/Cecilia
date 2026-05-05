import { useKeyboard } from '@opentui/react';
import { useCallback, useEffect, useState } from 'react';
import { type Updater, useImmer } from 'use-immer';

import ContactCard from '../component/ContactCard';
import type { Contact } from '../shared/model';
import { defineMilkyListener, useMilky, useMilkyEvent } from '../shared/protocol';
import {
  contactComparator,
  friendToBaseContact,
  groupToBaseContact,
  incomingSegmentsToText,
} from '../shared/transform';
import MessageView from './MessageView';

export default function MainView() {
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [contacts, rawSetContacts] = useImmer<Contact[]>([]);
  const [activeContact, setActiveContact] = useImmer<{ scene: 'friend' | 'group'; uin: number } | null>(null);
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
    const listener = defineMilkyListener('message_receive', ({ data }) => {
      if (data.message_scene === 'temp') {
        return;
      }
      upsertContact(data.message_scene, data.peer_id, {
        lastMsg: {
          time: data.time,
          content: incomingSegmentsToText(data.segments),
        },
      });
    });

    eventSource.on('message_receive', listener);
    return () => {
      eventSource.off('message_receive', listener);
    };
  }, [eventSource, upsertContact]);

  return (
    <box flexGrow={1}>
      <box backgroundColor="white">
        <text fg="black">
          {' '}
          <b>Cecilia</b> - <b>Tab</b> or click to switch focus
        </text>
      </box>
      <box flexDirection="row" flexGrow={1} paddingTop={1}>
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
                    setActiveContact({ scene: c.scene, uin: c.uin });
                    setTimeout(() => {
                      setFocused('messages');
                    });
                  }}
                  active={activeContact?.scene === c.scene && activeContact?.uin === c.uin}
                />
              ))}
            </box>
          </scrollbox>
        </box>
        <box flexGrow={1}>
          <box
            title="Messages"
            flexGrow={1}
            border
            borderColor={focused === 'messages' ? 'cyan' : undefined}
            onMouseDown={() => setFocused('messages')}
          >
            <MessageView active={activeContact ?? undefined} focused={focused === 'messages'} />
          </box>
          <box
            title="Input"
            height={8}
            border
            borderColor={focused === 'input' ? 'cyan' : undefined}
            onMouseDown={() => setFocused('input')}
          >
            <input placeholder="Type a message..." flexGrow={1} focused={focused === 'input'} />
          </box>
        </box>
      </box>
    </box>
  );
}
