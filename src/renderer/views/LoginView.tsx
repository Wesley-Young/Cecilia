import { Button, Spinner } from '@heroui/react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';

import type { LoginState, QrCodeState } from '#core/model/login.ts';
import SettingsForm from '@/components/SettingsForm';
import { core, errorMessage, shell } from '@/lib/rpc';

const qrCaptions = {
  waiting: '使用手机 QQ 扫码登录',
  scanned: '已扫码，请在手机上确认',
  expired: '二维码已过期',
  canceled: '已在手机上取消登录',
} satisfies Record<QrCodeState, string>;

export default function LoginView({ state }: { state: LoginState }) {
  const info = useQuery(shell.system.info.queryOptions());
  const settings = useQuery(core.settings.get.queryOptions());
  const start = useMutation(core.login.start.mutationOptions());
  const reset = useMutation(core.login.reset.mutationOptions());
  const [editing, setEditing] = useState(false);
  const login = () => start.mutate();
  const error = start.error ?? reset.error;
  // The mock logs in by itself and ignores the settings.
  const configurable = info.data !== undefined && !info.data.mock;
  const showSettings = configurable && (state.kind === 'unconfigured' || editing);

  let body: ReactNode;
  if (showSettings) {
    body = settings.data ? (
      <SettingsForm
        settings={settings.data}
        onSaved={() => setEditing(false)}
        onCancel={state.kind === 'unconfigured' ? undefined : () => setEditing(false)}
      />
    ) : (
      <Spinner />
    );
  } else
    switch (state.kind) {
      case 'idle':
        body = (
          <Button variant="primary" fullWidth onPress={login} isPending={start.isPending}>
            扫码登录
          </Button>
        );
        break;
      case 'logging-in':
        body = (
          <div className="flex flex-col items-center gap-3 text-muted text-sm">
            <Spinner />
            正在登录…
          </div>
        );
        break;
      case 'qrcode': {
        const ended = state.state === 'expired' || state.state === 'canceled';
        body = (
          <div className="flex flex-col items-center gap-4">
            <div className="relative rounded-2xl bg-white p-3 shadow-sm">
              <img src={state.image} alt="登录二维码" className={`size-48 ${ended ? 'opacity-20' : ''}`} />
            </div>
            <p className="text-sm text-muted">{qrCaptions[state.state]}</p>
            {ended && (
              <Button variant="primary" onPress={login}>
                刷新二维码
              </Button>
            )}
          </div>
        );
        break;
      }
      case 'failed':
        body = (
          <div className="flex flex-col items-center gap-4">
            <p className="text-center text-sm text-danger">{state.message}</p>
            <div className="flex gap-2">
              <Button variant="primary" onPress={login} isDisabled={reset.isPending}>
                重新登录
              </Button>
              {state.withSession && (
                <Button variant="ghost" onPress={() => reset.mutate()} isPending={reset.isPending}>
                  扫码登录
                </Button>
              )}
            </div>
          </div>
        );
        break;
      case 'unconfigured':
        body = <p className="text-center text-sm text-muted">{state.message}</p>;
        break;
      case 'online':
      case 'offline':
        body = null;
        break;
    }

  return (
    <div className="app-drag flex h-full flex-col items-center justify-center bg-sidebar px-6">
      <div className="app-no-drag flex w-80 flex-col items-center gap-8">
        <div className="flex flex-col items-center gap-2">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-accent font-semibold text-2xl text-accent-foreground">
            C
          </div>
          <h1 className="font-semibold text-xl">{showSettings ? '账号设置' : 'Cecilia'}</h1>
        </div>
        {body}
        {error && <p className="text-center text-sm text-danger">{errorMessage(error)}</p>}
        {configurable && !showSettings && (state.kind === 'idle' || state.kind === 'failed') && (
          <Button variant="ghost" size="sm" onPress={() => setEditing(true)}>
            账号设置
          </Button>
        )}
      </div>
      {info.data && (
        <p className="absolute bottom-4 text-xs text-muted">
          {info.data.mock ? '模拟账号 · ' : ''}v{info.data.version}
        </p>
      )}
    </div>
  );
}
