import type { FriendEntity, GroupEntity, GroupMemberEntity } from '@saltify/milky-types';
import { createContext, useContext } from 'react';
import type { Updater } from 'use-immer';

import type { Contact } from './model';

export interface RuntimeCache {
  selfInfo: {
    uin: number;
    nickname: string;
  };
  friends: Record<number, FriendEntity>;
  groups: Record<number, GroupEntity>;
  groupMembers: Record<number, Record<number, GroupMemberEntity>>;
  pinned: {
    friends: number[];
    groups: number[];
  };
  lastMsg: {
    friends: Record<number, Contact['lastMsg']>;
    groups: Record<number, Contact['lastMsg']>;
  };
}

export const RuntimeCacheContext = createContext<RuntimeCache | null>(null);
export const RuntimeCacheUpdaterContext = createContext<Updater<RuntimeCache> | null>(null);

export function useRuntimeCache(): RuntimeCache {
  const cache = useContext(RuntimeCacheContext);
  if (!cache) {
    throw new Error('useRuntimeCache must be used within a RuntimeCacheProvider');
  }
  return cache;
}

export function useRuntimeCacheUpdater(): Updater<RuntimeCache> {
  const updater = useContext(RuntimeCacheUpdaterContext);
  if (!updater) {
    throw new Error('useRuntimeCacheUpdater must be used within a RuntimeCacheProvider');
  }
  return updater;
}

export function createEmptyRuntimeCache(): RuntimeCache {
  return {
    selfInfo: {
      uin: 0,
      nickname: '',
    },
    friends: {},
    groups: {},
    groupMembers: {},
    pinned: {
      friends: [],
      groups: [],
    },
    lastMsg: {
      friends: {},
      groups: {},
    },
  };
}
