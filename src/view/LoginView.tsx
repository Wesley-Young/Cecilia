import { TextAttributes } from '@opentui/core';
import { useKeyboard } from '@opentui/react';
import { type ReactNode, useCallback, useState } from 'react';

import InteractableBox from '../component/InteractableBox';

export type LoginViewProps = {
  onSubmit: (
    endpoint: string,
    accessToken: string,
  ) => Promise<{ result: 'success' } | { result: 'error'; message: string }>;
};

export default function LoginView(props: LoginViewProps) {
  const [endpoint, setEndpoint] = useState('');
  const [accessToken, setAccessToken] = useState('');
  const [focused, setFocused] = useState<'endpoint' | 'accessToken'>('endpoint');
  const [message, setMessage] = useState<ReactNode | null>(null);
  const [canSubmit, setCanSubmit] = useState(true);

  useKeyboard((e) => {
    if (e.name === 'tab') {
      setFocused((f) => (f === 'endpoint' ? 'accessToken' : 'endpoint'));
      e.preventDefault();
    }
  });

  const handleSubmit = useCallback(async () => {
    if (!canSubmit) return;
    setCanSubmit(false);
    const result = await props.onSubmit(endpoint, accessToken);
    if (result.result === 'error') {
      setMessage(
        <text attributes={TextAttributes.BOLD} fg="red">
          {result.message}
        </text>,
      );
      setTimeout(() => {
        setMessage(null);
        setCanSubmit(true);
      }, 2000);
    }
  }, [canSubmit, endpoint, accessToken, props]);

  return (
    <box alignItems="center" justifyContent="center" flexGrow={1}>
      <box justifyContent="center" alignItems="stretch">
        <ascii-font font="block" text="Cecilia" />
        <text alignSelf="flex-end" attributes={TextAttributes.DIM}>
          Terminal-based Milky IM
        </text>
        <InteractableBox
          title="Endpoint"
          border
          height={3}
          isActive={focused === 'endpoint'}
          onMouseDown={() => setFocused('endpoint')}
        >
          <input
            placeholder="e.g. http(s)://..."
            onInput={setEndpoint}
            onSubmit={handleSubmit}
            focused={focused === 'endpoint'}
          />
        </InteractableBox>
        <InteractableBox
          title="Access Token"
          border
          height={3}
          isActive={focused === 'accessToken'}
          onMouseDown={() => setFocused('accessToken')}
        >
          <input
            placeholder="Leave empty if not required"
            onInput={setAccessToken}
            onSubmit={handleSubmit}
            focused={focused === 'accessToken'}
          />
        </InteractableBox>
        <box alignSelf="flex-end">
          {message ?? (
            <text attributes={TextAttributes.DIM}>
              <b>Tab</b> or mouse to switch, <b>Enter</b> to submit
            </text>
          )}
        </box>
      </box>
    </box>
  );
}
