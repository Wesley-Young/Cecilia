import { Button, Description, FieldError, Input, Label, ListBox, Select, TextField } from '@heroui/react';
import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';

import type { SettingsView } from '#core/model/settings.ts';
import { core, errorMessage, fieldErrors, queryClient } from '@/lib/rpc';

interface Props {
  settings: SettingsView;
  /** Called after the settings are saved and the account has reconnected with them. */
  onSaved?: () => void;
  onCancel?: () => void;
}

/** The QQ number, sign service and protocol version the account logs in with. */
export default function SettingsForm({ settings, onSaved, onCancel }: Props) {
  const [uin, setUin] = useState(settings.uin === null ? '' : String(settings.uin));
  const [signUrl, setSignUrl] = useState(settings.signUrl);
  const [signToken, setSignToken] = useState('');
  const [appInfo, setAppInfo] = useState(settings.appInfo);
  const update = useMutation(
    core.settings.update.mutationOptions({
      onSuccess: async () => {
        // Another QQ number clears the conversations and history; everything is read again.
        await queryClient.invalidateQueries();
        onSaved?.();
      },
    }),
  );
  const errors = fieldErrors(update.error);
  const switching = settings.uin !== null && uin.trim() !== '' && Number(uin) !== settings.uin;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    // An empty field sends NaN, which the server reports as missing rather than as a wrong number.
    update.mutate({ uin: uin === '' ? Number.NaN : Number(uin), signUrl, signToken, appInfo });
  };

  return (
    <form onSubmit={submit} className="flex w-full flex-col gap-4" noValidate>
      <TextField
        value={uin}
        onChange={(value) => setUin(value.replace(/\D/g, ''))}
        isInvalid={errors.uin !== undefined}
        autoFocus
      >
        <Label>QQ 号</Label>
        <Input inputMode="numeric" />
        {switching && <Description className="text-danger">更换 QQ 号会清空本机的聊天记录和登录状态</Description>}
        <FieldError>{errors.uin}</FieldError>
      </TextField>
      <TextField value={signUrl} onChange={setSignUrl} isInvalid={errors.signUrl !== undefined}>
        <Label>签名服务地址</Label>
        <Input placeholder="https://" spellCheck={false} />
        <FieldError>{errors.signUrl}</FieldError>
      </TextField>
      <TextField type="password" value={signToken} onChange={setSignToken}>
        <Label>签名服务 Token</Label>
        <Input placeholder={settings.hasSignToken ? '已设置，留空则不修改' : ''} />
      </TextField>
      <Select
        selectedKey={appInfo}
        onSelectionChange={(key) => key !== null && setAppInfo(String(key))}
        isInvalid={errors.appInfo !== undefined}
      >
        <Label>协议版本</Label>
        <Select.Trigger>
          <Select.Value />
          <Select.Indicator />
        </Select.Trigger>
        <Select.Popover>
          <ListBox>
            {settings.appInfoOptions.map((option) => (
              <ListBox.Item key={option.id} id={option.id} textValue={option.label}>
                {option.label}
              </ListBox.Item>
            ))}
          </ListBox>
        </Select.Popover>
        <FieldError>{errors.appInfo}</FieldError>
      </Select>
      {update.error && Object.keys(errors).length === 0 && (
        <p className="text-sm text-danger">{errorMessage(update.error)}</p>
      )}
      <div className="flex justify-end gap-2 pt-2">
        {onCancel && (
          <Button variant="ghost" onPress={onCancel}>
            取消
          </Button>
        )}
        <Button type="submit" variant={switching ? 'danger' : 'primary'} isPending={update.isPending}>
          {switching ? '清空记录并保存' : '保存'}
        </Button>
      </div>
    </form>
  );
}
