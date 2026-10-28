import { create } from 'zustand';

interface PresenceState {
  onlineUsers: Set<string>;
  setOnline: (userId: string) => void;
  setOffline: (userId: string) => void;
  setOnlineUsers: (userIds: string[]) => void;
  isOnline: (userId: string) => boolean;
}

export const usePresenceStore = create<PresenceState>((set, get) => ({
  onlineUsers: new Set<string>(),

  setOnline: (userId) => set((state) => {
    const next = new Set(state.onlineUsers);
    next.add(userId);
    return { onlineUsers: next };
  }),

  setOffline: (userId) => set((state) => {
    const next = new Set(state.onlineUsers);
    next.delete(userId);
    return { onlineUsers: next };
  }),

  setOnlineUsers: (userIds) => set({
    onlineUsers: new Set(userIds),
  }),

  isOnline: (userId) => get().onlineUsers.has(userId),
}));
