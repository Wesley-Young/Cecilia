import { createContext, useContext } from 'react';

/** What the people shown in a chat can lead to, provided by the chat pane. */
export interface ChatActions {
  /** Puts a mention of a group member in the composer, at the caret. `name` is without the '@'. */
  mention: (uin: number, name: string) => void;
  /** Opens the chat with a friend. */
  openChat: (uin: number) => void;
}

export const ChatActionsContext = createContext<ChatActions | undefined>(undefined);

/** Undefined outside a chat pane, where the cards and menus leave these actions out. */
export const useChatActions = () => useContext(ChatActionsContext);
