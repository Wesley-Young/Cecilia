import { createMilkyClient, type MilkyClient } from "@saltify/milky-tea";

function createDefaultMilkyClient(): MilkyClient | null {
  if (!Bun.env.BASE_URL) {
    return null;
  }
  return createMilkyClient({
    baseURL: Bun.env.BASE_URL,
  });
}

export const devClient = createDefaultMilkyClient();
