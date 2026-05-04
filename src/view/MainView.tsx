import type { GetLoginInfoOutput } from '@saltify/milky-types';
import { useEffect, useState } from 'react';

import { defineMilkyListener, useMilky, useMilkyEvent } from '../shared';

export default function MainView() {
  const milky = useMilky();
  const eventSource = useMilkyEvent();
  const [loginInfo, setLoginInfo] = useState<GetLoginInfoOutput | null>(null);
  const [messages, setMessages] = useState<string[]>([]);

  useEffect(() => {
    milky.system.getLoginInfo().then(setLoginInfo);
  }, [milky]);

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
    <box title={`Cecilia - ${loginInfo?.nickname || 'Loading...'}`} border flexGrow={1}>
      <scrollbox gap={1}>
        {messages.map((msg) => (
          <text key={msg}>{msg}</text>
        ))}
      </scrollbox>
    </box>
  );
}
