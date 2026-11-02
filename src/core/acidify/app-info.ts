import { AppInfo } from '@acidify/core';

import type { AppInfoOption } from '#core/model/settings.ts';

type BundledAppInfo = Record<string, AppInfo | undefined>;

const bundled = AppInfo.Bundled as unknown as BundledAppInfo;

/** The AppInfo presented when none is chosen: what `AppInfo.Bundled.Linux` points to. */
export const defaultAppInfo = (() => {
  const linux = AppInfo.Bundled.Linux;
  return Object.keys(bundled).find((id) => id !== 'Linux' && bundled[id] === linux) ?? 'Linux_39038';
})();

/**
 * Every AppInfo bundled with this version of @acidify/core, read at runtime so an upgrade brings its new versions.
 * The `Linux` and `Mac` aliases are left out: they repeat one of the versioned entries.
 */
export function bundledAppInfoOptions(): AppInfoOption[] {
  return Object.keys(bundled)
    .filter((id) => /_\d+$/.test(id))
    .map((id) => {
      const info = bundled[id] as AppInfo;
      return { id, label: `${info.os} ${info.currentVersion}` };
    });
}

export function bundledAppInfo(id: string): AppInfo | undefined {
  return /_\d+$/.test(id) ? bundled[id] : undefined;
}
