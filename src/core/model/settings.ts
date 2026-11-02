/** How the account logs in; kept in the account's database and edited in the interface. */
export interface AccountSettings {
  /** The account to log in as. The sign service needs it before the QR code is scanned. */
  uin: number | null;
  /** A Lagrange V2 sign service. */
  signUrl: string;
  signToken: string;
  /** Which bundled PC AppInfo to present as, such as `Linux_39038`. */
  appInfo: string;
}

/** A bundled AppInfo the account can present as. */
export interface AppInfoOption {
  id: string;
  /** OS and client version, such as `Linux 3.2.19-39038`. */
  label: string;
}

/** What the interface sees of the settings: the token is never sent back, only whether one is set. */
export interface SettingsView {
  uin: number | null;
  signUrl: string;
  hasSignToken: boolean;
  appInfo: string;
  appInfoOptions: AppInfoOption[];
}

/** Settings the account cannot log in without. */
export function isComplete(settings: AccountSettings): boolean {
  return settings.uin !== null && settings.signUrl !== '';
}
