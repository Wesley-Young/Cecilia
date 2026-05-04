/** biome-ignore-all lint/suspicious/noConfusingVoidType: copied from @saltify/milky-tea */
/** biome-ignore-all lint/suspicious/noExplicitAny: copied from @saltify/milky-tea */
import type { MilkyClient, MilkyEventSource } from '@saltify/milky-tea';
import type { Event } from '@saltify/milky-types';
import { createContext, useContext } from 'react';

export const MilkyContext = createContext<MilkyClient | null>(null);

export function useMilky() {
  const milky = useContext(MilkyContext);
  if (!milky) {
    throw new Error('useMilky must be used within a MilkyProvider');
  }
  return milky;
}

export const MilkyEventContext = createContext<MilkyEventSource | null>(null);

export function useMilkyEvent() {
  const eventSource = useContext(MilkyEventContext);
  if (!eventSource) {
    throw new Error('useMilkyEvent must be used within a MilkyEventProvider');
  }
  return eventSource;
}

interface MilkyEventConstraint {}
type MilkyEvent<K extends Event['event_type'] = Event['event_type']> = Extract<
  Event,
  {
    event_type: K;
  }
> &
  MilkyEventConstraint;
type MilkyEventSourceEventMap = {
  error: any;
  push: MilkyEvent;
  open: void;
} & { [P in Event['event_type']]: MilkyEvent<P> };
type MilkyEventSourceEventKey = keyof MilkyEventSourceEventMap;
export function defineMilkyListener<K extends MilkyEventSourceEventKey, R>(
  _type: K,
  listener: (ev: MilkyEventSourceEventMap[K]) => R,
): (ev: MilkyEventSourceEventMap[K]) => R {
  return listener;
}
