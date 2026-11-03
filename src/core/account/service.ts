import { serviceToken } from '@fraqjs/kernel';

import { bundledAppInfoOptions } from '#core/acidify/app-info.ts';
import type { ChatStore } from '#core/chat-store/service.ts';
import type { ChatClient } from '#core/client/service.ts';
import type { ConversationService } from '#core/conversations/service.ts';
import { AppError } from '#core/errors.ts';
import type { AccountSettings, SettingsView } from '#core/model/settings.ts';
import type { SettingsStore } from '#core/settings/service.ts';

export interface SettingsInput {
  uin: number;
  signUrl: string;
  /** Omitted to keep the token already saved. */
  signToken?: string | undefined;
  appInfo: string;
}

/** Changes to the account as a whole: its settings, and what has to go when it becomes another account. */
export class AccountService {
  static readonly token = serviceToken<AccountService>('cecilia/AccountService');
  private readonly settings: SettingsStore;
  private readonly client: ChatClient;
  private readonly store: ChatStore;
  private readonly conversations: ConversationService;

  constructor(settings: SettingsStore, client: ChatClient, store: ChatStore, conversations: ConversationService) {
    this.settings = settings;
    this.client = client;
    this.store = store;
    this.conversations = conversations;
  }

  async view(): Promise<SettingsView> {
    return toView(await this.settings.load());
  }

  /**
   * Saves the settings and reconnects with them. A different QQ number is a different account: its session and
   * local messages are dropped first, so two accounts' data never mix.
   */
  async update(input: SettingsInput): Promise<SettingsView> {
    if (!bundledAppInfoOptions().some((option) => option.id === input.appInfo)) {
      throw new AppError('bad_request', '所选的协议版本不存在');
    }
    const previous = await this.settings.load();
    const next: AccountSettings = {
      uin: input.uin,
      signUrl: input.signUrl,
      signToken: input.signToken ?? previous.signToken,
      appInfo: input.appInfo,
    };
    if (previous.uin !== null && previous.uin !== next.uin) {
      await this.client.logout();
      await this.store.clear();
      await this.conversations.clear();
    }
    await this.settings.save(next);
    await this.client.applySettings(next);
    return toView(next);
  }

  /**
   * Forgets the saved session and rebuilds the connection with a fresh one, for when logging in with it keeps
   * failing. The next login shows a QR code; local messages stay.
   */
  async resetSession() {
    await this.client.logout();
    await this.client.applySettings(await this.settings.load());
  }
}

function toView(settings: AccountSettings): SettingsView {
  return {
    uin: settings.uin,
    signUrl: settings.signUrl,
    hasSignToken: settings.signToken !== '',
    appInfo: settings.appInfo,
    appInfoOptions: bundledAppInfoOptions(),
  };
}
