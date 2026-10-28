import { useChatStore } from '../store/chatStore';
import httpClient from '../api/httpClient';

interface ChannelListProps {
  onCreateChannel: () => void;
}

export default function ChannelList({ onCreateChannel }: ChannelListProps) {
  const channels = useChatStore((s) => s.channels);
  const activeChannelId = useChatStore((s) => s.activeChannelId);
  const setActiveChannel = useChatStore((s) => s.setActiveChannel);
  const setMessages = useChatStore((s) => s.setMessages);

  const handleSelectChannel = async (channelId: string) => {
    setActiveChannel(channelId);

    // Fetch message history if not already loaded
    const existing = useChatStore.getState().messages[channelId];
    if (!existing) {
      try {
        const res = await httpClient.get(`/channels/${channelId}/messages?page=0&size=50`);
        setMessages(channelId, res.data);
      } catch (err) {
        console.error('Failed to fetch messages:', err);
      }
    }
  };

  return (
    <>
      <div className="sidebar-section">
        <div className="sidebar-section-title">
          <span>Channels</span>
          <button
            className="btn-icon"
            onClick={onCreateChannel}
            title="Create channel"
            id="create-channel-btn"
          >
            +
          </button>
        </div>
      </div>

      <div className="channel-list">
        {channels.map((channel) => (
          <div
            key={channel.id}
            className={`channel-item ${channel.id === activeChannelId ? 'active' : ''}`}
            onClick={() => handleSelectChannel(channel.id)}
            id={`channel-${channel.id}`}
          >
            <span className="channel-icon">#</span>
            <span className="channel-name">{channel.name}</span>
          </div>
        ))}

        {channels.length === 0 && (
          <div style={{
            padding: '12px',
            fontSize: 'var(--font-xs)',
            color: 'var(--text-muted)',
            textAlign: 'center'
          }}>
            No channels yet
          </div>
        )}
      </div>
    </>
  );
}
