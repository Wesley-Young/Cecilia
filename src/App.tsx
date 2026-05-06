import { createMilkyClient, type MilkyClient, type MilkyEventSource } from '@saltify/milky-tea';
import { useEffect, useState } from 'react';
import { useImmer } from 'use-immer';

import { IncomingSegmentDisplay } from './component/MessageSegmentDisplay';
import { createEmptyRuntimeCache, RuntimeCacheContext, RuntimeCacheUpdaterContext } from './shared/cache';
import { defineMilkyListener, devClient, MilkyContext, MilkyEventContext } from './shared/protocol';
import LoginView from './view/LoginView';
import MainView from './view/MainView';

function validateUrl(url: string): boolean {
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

async function testApi(client: MilkyClient): Promise<boolean> {
  try {
    await client.system.getLoginInfo();
    return true;
  } catch {
    return false;
  }
}

export default function App() {
  const [milky, setMilky] = useState<MilkyClient | null>(devClient);
  const [eventSource, setEventSource] = useState<MilkyEventSource | null>(devClient?.event() ?? null);
  const [runtimeCache, setRuntimeCache] = useImmer(createEmptyRuntimeCache());

  useEffect(() => {
    milky?.system.getLoginInfo().then(({ uin, nickname }) => {
      setRuntimeCache((cache) => {
        cache.selfInfo.uin = uin;
        cache.selfInfo.nickname = nickname;
      });
    });

    milky?.system.getFriendList({ no_cache: true }).then(({ friends }) => {
      setRuntimeCache((cache) => {
        for (const friend of friends) {
          cache.friends[friend.user_id] = friend;
        }
      });
    });

    milky?.system.getGroupList({ no_cache: true }).then(({ groups }) => {
      setRuntimeCache((cache) => {
        for (const group of groups) {
          cache.groups[group.group_id] = group;
        }
      });
    });

    milky?.system.getPeerPins().then(({ friends, groups }) => {
      setRuntimeCache((cache) => {
        cache.pinned.friends = friends.map((f) => f.user_id);
        cache.pinned.groups = groups.map((g) => g.group_id);
      });
    });

    const pinListener = defineMilkyListener('peer_pin_change', ({ data }) => {
      switch (data.message_scene) {
        case 'friend':
          setRuntimeCache((cache) => {
            const pinnedList = cache.pinned.friends;
            if (data.is_pinned) {
              if (!pinnedList.includes(data.peer_id)) {
                pinnedList.push(data.peer_id);
              }
            } else {
              const index = pinnedList.indexOf(data.peer_id);
              if (index !== -1) {
                pinnedList.splice(index, 1);
              }
            }
          });
          return;
        case 'group':
          setRuntimeCache((cache) => {
            const pinnedList = cache.pinned.groups;
            if (data.is_pinned) {
              if (!pinnedList.includes(data.peer_id)) {
                pinnedList.push(data.peer_id);
              }
            } else {
              const index = pinnedList.indexOf(data.peer_id);
              if (index !== -1) {
                pinnedList.splice(index, 1);
              }
            }
          });
          return;
        default:
          return;
      }
    });

    const messageListener = defineMilkyListener('message_receive', ({ data }) => {
      if (data.message_scene === 'temp') {
        return;
      }
      setRuntimeCache((cache) => {
        if (data.message_scene === 'friend') {
          cache.lastMsg.friends[data.peer_id] = {
            time: data.time,
            content: <IncomingSegmentDisplay segments={data.segments} noFg />,
          };
        } else if (data.message_scene === 'group') {
          cache.lastMsg.groups[data.peer_id] = {
            time: data.time,
            content: <IncomingSegmentDisplay segments={data.segments} noFg />,
          };
        }
      });
    });

    eventSource?.on('peer_pin_change', pinListener);
    eventSource?.on('message_receive', messageListener);

    return () => {
      eventSource?.off('peer_pin_change', pinListener);
      eventSource?.off('message_receive', messageListener);
    };
  }, [milky, setRuntimeCache, eventSource]);

  return (
    <box flexGrow={1}>
      {milky ? (
        <MilkyContext.Provider value={milky}>
          <MilkyEventContext.Provider value={eventSource}>
            <RuntimeCacheContext.Provider value={runtimeCache}>
              <RuntimeCacheUpdaterContext.Provider value={setRuntimeCache}>
                <MainView />
              </RuntimeCacheUpdaterContext.Provider>
            </RuntimeCacheContext.Provider>
          </MilkyEventContext.Provider>
        </MilkyContext.Provider>
      ) : (
        <LoginView
          onSubmit={async (endpoint, accessToken) => {
            endpoint = endpoint || Bun.env.BASE_URL || '';
            if (!validateUrl(endpoint)) {
              return {
                result: 'error',
                message: 'Invalid URL. Please enter a valid endpoint.',
              };
            }

            const client = createMilkyClient({
              baseURL: endpoint,
              token: accessToken,
            });

            const isApiAvailable = await testApi(client);
            if (!isApiAvailable) {
              return {
                result: 'error',
                message: 'Failed to connect to the API service.',
              };
            }

            const eventSource = client.event();

            setMilky(client);
            setEventSource(eventSource);

            return { result: 'success' };
          }}
        />
      )}
    </box>
  );
}
