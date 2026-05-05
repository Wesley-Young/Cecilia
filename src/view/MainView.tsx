import { useCallback, useEffect, useState } from 'react';
import { useImmer } from 'use-immer';

import ContactCard from '../component/ContactCard';
import type { Contact } from '../model';
import { defineMilkyListener, useMilky, useMilkyEvent } from '../shared';

export default function MainView() {
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [contacts, rawSetContacts] = useImmer<Contact[]>([]);
  const [messages, setMessages] = useState<string[]>([]);

  const setContacts = useCallback(
    (contacts: Contact[]) => {
      const sortedContacts = contacts.toSorted((a, b) => {
        // compare pinned status
        if (a.isPinned && !b.isPinned) return -1;
        if (!a.isPinned && b.isPinned) return 1;

        // compare message time
        const aTime = a.lastMsg?.time ?? 0;
        const bTime = b.lastMsg?.time ?? 0;
        if (aTime !== bTime) {
          return bTime - aTime;
        }

        // compare scene, group first
        if (a.scene !== b.scene) {
          return a.scene === 'group' ? -1 : 1;
        }

        // compare uin
        return b.uin - a.uin;
      });
      rawSetContacts(sortedContacts);
    },
    [rawSetContacts],
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
            scene: 'group',
            uin: group.group_id,
            displayName: info.group_name,
            isPinned: true,
          });
        }
      }

      for (const friend of pinnedFriends) {
        const info = friendMap.get(friend.user_id);
        if (info) {
          preloadedContacts.push({
            scene: 'friend',
            uin: friend.user_id,
            displayName: info.nickname,
            isPinned: true,
          });
        }
      }

      setContacts(preloadedContacts);
    })();
  }, [milky, setContacts]);

  useEffect(() => {
    const listener = defineMilkyListener('message_receive', ({ data }) => {
      setMessages((msgs) => [...msgs, `${data.message_scene}, ${data.peer_id}, ${data.message_seq}`]);
    });

    eventSource.on('message_receive', listener);
    return () => {
      eventSource.off('message_receive', listener);
    };
  }, [eventSource]);

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
