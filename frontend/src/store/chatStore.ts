import { create } from 'zustand';

export interface ChatMessage {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: number;
}

export interface Channel {
  id: string;
  name: string;
  description: string;
  type: string;
  memberCount: number;
}

interface TypingUser {
  userId: string;
  username: string;
  expiry: number;
}

interface ChatState {
  // Channels
  channels: Channel[];
  activeChannelId: string | null;

  // Messages per channel (channelId -> messages[])
  messages: Record<string, ChatMessage[]>;

  // Typing indicators per channel (channelId -> TypingUser[])
  typingUsers: Record<string, TypingUser[]>;

  // Actions
  setChannels: (channels: Channel[]) => void;
  addChannel: (channel: Channel) => void;
  setActiveChannel: (channelId: string) => void;

  setMessages: (channelId: string, messages: ChatMessage[]) => void;
  addMessage: (message: ChatMessage) => void;

  setTypingUser: (channelId: string, userId: string, username: string) => void;
  clearExpiredTyping: () => void;
}

// Typing indicator expires after 3 seconds
const TYPING_EXPIRY_MS = 3000;

export const useChatStore = create<ChatState>((set, get) => ({
  channels: [],
  activeChannelId: null,
  messages: {},
  typingUsers: {},

  setChannels: (channels) => set({ channels }),

  addChannel: (channel) => set((state) => ({
    channels: [...state.channels, channel],
  })),

  setActiveChannel: (channelId) => set({ activeChannelId: channelId }),

  setMessages: (channelId, messages) => set((state) => ({
    messages: { ...state.messages, [channelId]: messages },
  })),

  addMessage: (message) => set((state) => {
    const channelMessages = state.messages[message.channelId] || [];
    // Deduplicate by message ID
    if (channelMessages.some((m) => m.id === message.id)) {
      return state;
    }
    return {
      messages: {
        ...state.messages,
        [message.channelId]: [...channelMessages, message],
      },
    };
  }),

  setTypingUser: (channelId, userId, username) => set((state) => {
    const existing = state.typingUsers[channelId] || [];
    const filtered = existing.filter((t) => t.userId !== userId);
    return {
      typingUsers: {
        ...state.typingUsers,
        [channelId]: [
          ...filtered,
          { userId, username, expiry: Date.now() + TYPING_EXPIRY_MS },
        ],
      },
    };
  }),

  clearExpiredTyping: () => set((state) => {
    const now = Date.now();
    const updated: Record<string, TypingUser[]> = {};
    let changed = false;

    for (const [channelId, users] of Object.entries(state.typingUsers)) {
      const active = users.filter((t) => t.expiry > now);
      if (active.length !== users.length) changed = true;
      if (active.length > 0) {
        updated[channelId] = active;
      }
    }

    return changed ? { typingUsers: updated } : state;
  }),
}));
