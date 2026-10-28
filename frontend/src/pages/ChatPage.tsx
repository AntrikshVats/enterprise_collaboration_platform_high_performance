import { useEffect } from 'react';
import { useChatStore } from '../store/chatStore';
import { useWebSocket } from '../hooks/useWebSocket';
import httpClient from '../api/httpClient';
import Sidebar from '../components/Sidebar';
import ChatWindow from '../components/ChatWindow';

export default function ChatPage() {
  const { sendMessage, sendTyping, subscribe, unsubscribe, status } = useWebSocket();
  const setChannels = useChatStore((s) => s.setChannels);
  const activeChannelId = useChatStore((s) => s.activeChannelId);

  // Fetch channels on mount
  useEffect(() => {
    const fetchChannels = async () => {
      try {
        const res = await httpClient.get('/channels');
        setChannels(res.data);
      } catch (err) {
        console.error('Failed to fetch channels:', err);
      }
    };
    fetchChannels();
  }, [setChannels]);

  // Subscribe to active channel via WebSocket
  useEffect(() => {
    if (activeChannelId) {
      subscribe(activeChannelId);
      return () => {
        unsubscribe(activeChannelId);
      };
    }
  }, [activeChannelId, subscribe, unsubscribe]);

  return (
    <div className="chat-layout">
      <Sidebar connectionStatus={status} />
      <ChatWindow
        sendMessage={sendMessage}
        sendTyping={sendTyping}
      />
    </div>
  );
}
