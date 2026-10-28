import { useChatStore } from '../store/chatStore';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import TypingIndicator from './TypingIndicator';

interface ChatWindowProps {
  sendMessage: (channelId: string, content: string) => void;
  sendTyping: (channelId: string) => void;
}

export default function ChatWindow({ sendMessage, sendTyping }: ChatWindowProps) {
  const activeChannelId = useChatStore((s) => s.activeChannelId);
  const channels = useChatStore((s) => s.channels);

  const activeChannel = channels.find((c) => c.id === activeChannelId);

  if (!activeChannelId || !activeChannel) {
    return (
      <div className="chat-window">
        <div className="no-channel">
          <div className="logo-icon">💬</div>
          <h2>Enterprise Chat</h2>
          <p>Select a channel from the sidebar to start messaging your team</p>
        </div>
      </div>
    );
  }

  return (
    <div className="chat-window">
      {/* Header */}
      <div className="chat-header">
        <span style={{ fontSize: 'var(--font-lg)', opacity: 0.5 }}>#</span>
        <span className="channel-title">{activeChannel.name}</span>
        {activeChannel.description && (
          <span className="channel-description">{activeChannel.description}</span>
        )}
        <span className="member-count">
          👥 {activeChannel.memberCount} members
        </span>
      </div>

      {/* Messages */}
      <MessageList channelId={activeChannelId} />

      {/* Typing Indicator */}
      <TypingIndicator channelId={activeChannelId} />

      {/* Input */}
      <MessageInput
        channelId={activeChannelId}
        onSendMessage={sendMessage}
        onTyping={sendTyping}
      />
    </div>
  );
}
