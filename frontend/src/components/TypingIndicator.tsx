import { useChatStore } from '../store/chatStore';
import { useAuthStore } from '../store/authStore';

interface TypingIndicatorProps {
  channelId: string;
}

export default function TypingIndicator({ channelId }: TypingIndicatorProps) {
  const typingUsers = useChatStore((s) => s.typingUsers[channelId] || []);
  const currentUserId = useAuthStore((s) => s.user?.userId);

  // Filter out current user from typing indicators
  const others = typingUsers.filter((t) => t.userId !== currentUserId);

  if (others.length === 0) {
    return <div className="typing-indicator" />;
  }

  const names = others.map((t) => t.username);
  let text = '';

  if (names.length === 1) {
    text = `${names[0]} is typing`;
  } else if (names.length === 2) {
    text = `${names[0]} and ${names[1]} are typing`;
  } else {
    text = `${names[0]} and ${names.length - 1} others are typing`;
  }

  return (
    <div className="typing-indicator">
      <div className="typing-dots">
        <span />
        <span />
        <span />
      </div>
      {text}
    </div>
  );
}
