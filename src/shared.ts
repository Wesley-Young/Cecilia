import type { MilkyClient } from '@saltify/milky-tea';
import { createContext, useContext } from 'react';

const MilkyContext = createContext<MilkyClient | null>(null);

export function useMilky() {
  const milky = useContext(MilkyContext);
  if (!milky) {
    throw new Error('useMilky must be used within a MilkyProvider');
  }
  return milky;
}
