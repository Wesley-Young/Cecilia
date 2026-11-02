import { Button, Spinner } from '@heroui/react';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';

import type { LoginState, QrCodeState } from '#core/model/login.ts';
import { core, errorMessage, shell } from '@/lib/rpc';

const qrCaptions = {
  waiting: '使用手机 QQ 扫码登录',
  scanned: '已扫码，请在手机上确认',
  expired: '二维码已过期',
  canceled: '已在手机上取消登录',
} satisfies Record<QrCodeState, string>;

export default function LoginView({ state }: { state: LoginState }) {
  const info = useQuery(shell.system.info.queryOptions());
  const start = useMutation(core.login.start.mutationOptions());
  const login = () => start.mutate();

  let body: ReactNode;
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
          <Button variant="primary" onPress={login}>
            重新登录
          </Button>
        </div>
      );
      break;
    case 'unconfigured':
      body = (
        <p className="text-center text-sm text-muted">
          {state.message}。请设置 CECILIA_UIN、CECILIA_SIGN_URL 和 CECILIA_SIGN_TOKEN 后重启
        </p>
      );
      break;
    case 'online':
    case 'offline':
      body = null;
      break;
  }

  return (
    <div className="app-drag flex h-full flex-col items-center justify-center bg-sidebar px-6">
      <div className="app-no-drag flex w-72 flex-col items-center gap-8">
        <div className="flex flex-col items-center gap-2">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-accent font-semibold text-2xl text-accent-foreground">
            C
          </div>
          <h1 className="font-semibold text-xl">Cecilia</h1>
        </div>
        {body}
        {start.error && <p className="text-center text-sm text-danger">{errorMessage(start.error)}</p>}
      </div>
      {info.data && (
        <p className="absolute bottom-4 text-xs text-muted">
          {info.data.mock ? '模拟账号 · ' : ''}v{info.data.version}
        </p>
      )}
    </div>
  );
}
