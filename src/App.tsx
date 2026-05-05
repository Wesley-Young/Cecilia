import { createMilkyClient, type MilkyClient, type MilkyEventSource } from '@saltify/milky-tea';
import { useState } from 'react';

import { devClient, MilkyContext, MilkyEventContext } from './shared/protocol';
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

  return (
    <box flexGrow={1}>
      {milky ? (
        <MilkyContext.Provider value={milky}>
          <MilkyEventContext.Provider value={eventSource}>
            <MainView />
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
