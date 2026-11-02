import { Spinner } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';
import { useSubscription } from '@trpc/tanstack-react-query';

import { applyUpdate } from '@/lib/cache';
import { core } from '@/lib/rpc';
import LoginView from '@/views/LoginView';
import MainView from '@/views/MainView';

export default function App() {
  useSubscription(core.updates.subscriptionOptions(undefined, { onData: applyUpdate }));
  const login = useQuery(core.login.state.queryOptions());
  const conversations = useQuery(core.conversations.list.queryOptions());

  if (!login.data || !conversations.data) {
    return (
      <div className="app-drag flex h-full items-center justify-center">
        <Spinner />
      </div>
    );
  }

  const state = login.data;
  // While a saved session logs in, what the store already has is worth showing.
  const showMain =
    state.kind === 'online' ||
    state.kind === 'offline' ||
    (state.kind === 'logging-in' && conversations.data.length > 0);
  return showMain ? <MainView state={state} conversations={conversations.data} /> : <LoginView state={state} />;
}
