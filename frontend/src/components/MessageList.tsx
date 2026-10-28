import { useEffect, useRef } from 'react';
import { useChatStore, ChatMessage } from '../store/chatStore';
import UserAvatar from './UserAvatar';

interface MessageListProps {
  channelId: string;
}

function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(timestamp: number): string {
  const date = new Date(timestamp);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  if (date.toDateString() === today.toDateString()) return 'Today';
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return date.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
}

export default function MessageList({ channelId }: MessageListProps) {
  const messages = useChatStore((s) => s.messages[channelId] || []);
  const listEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    listEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  if (messages.length === 0) {
    return (
      <div className="empty-chat">
        <div className="empty-icon">💬</div>
        <p>No messages yet. Start the conversation!</p>
      </div>
    );
  }

  // Group messages by date
  let lastDate = '';

  return (
    <div className="message-list">
      {messages.map((msg: ChatMessage) => {
        const dateStr = formatDate(msg.timestamp);
        const showDate = dateStr !== lastDate;
        lastDate = dateStr;

        return (
          <div key={msg.id}>
            {showDate && (
              <div style={{
                textAlign: 'center',
                padding: '12px 0 8px',
                fontSize: 'var(--font-xs)',
                color: 'var(--text-muted)',
                fontWeight: 500,
              }}>
                — {dateStr} —
              </div>
            )}
            <div className="message-group">
              <div className="message-avatar">
                <UserAvatar username={msg.senderName} />
              </div>
              <div className="message-content">
                <div className="message-header">
                  <span className="message-sender">{msg.senderName}</span>
                  <span className="message-time">{formatTime(msg.timestamp)}</span>
                </div>
                <div className="message-text">{msg.content}</div>
              </div>
            </div>
          </div>
        );
      })}
      <div ref={listEndRef} />
    </div>
  );
}
