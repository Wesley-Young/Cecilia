import { useCallback, useEffect } from 'react';
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

export default function MainView() {
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [contacts, rawSetContacts] = useImmer<Contact[]>([]);

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
          <b>Cecilia</b>
        </text>
      </box>
      <box flexDirection="row" flexGrow={1}>
        <box title="Contacts" border width={30}>
          <scrollbox>
            <box gap={1}>
              {contacts.map((c) => (
                <ContactCard key={`${c.scene}-${c.uin}`} contact={c} />
              ))}
            </box>
          </scrollbox>
        </box>
        <box flexGrow={1}>
          <box title="Messages" flexGrow={1} border>
            <scrollbox></scrollbox>
          </box>
          <box title="Input" height={8} border>
            <input placeholder="Type a message..." flexGrow={1} />
          </box>
        </box>
      </box>
    </box>
  );
}
