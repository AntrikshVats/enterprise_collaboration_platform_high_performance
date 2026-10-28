import { useState, useCallback, FormEvent } from 'react';

interface MessageInputProps {
  channelId: string;
  onSendMessage: (channelId: string, content: string) => void;
  onTyping: (channelId: string) => void;
}

export default function MessageInput({ channelId, onSendMessage, onTyping }: MessageInputProps) {
  const [text, setText] = useState('');

  const handleSubmit = useCallback((e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;

    onSendMessage(channelId, content);
    setText('');
  }, [text, channelId, onSendMessage]);

  const handleChange = useCallback((value: string) => {
    setText(value);
    if (value.length > 0) {
      onTyping(channelId);
    }
  }, [channelId, onTyping]);

  return (
    <div className="message-input-container">
      <form onSubmit={handleSubmit}>
        <div className="message-input-wrapper">
          <input
            id="message-input"
            type="text"
            placeholder="Type a message..."
            value={text}
            onChange={(e) => handleChange(e.target.value)}
            autoComplete="off"
          />
          <button
            id="send-message-btn"
            type="submit"
            className="btn-send"
            disabled={!text.trim()}
            title="Send message"
          >
            ➤
          </button>
        </div>
      </form>
    </div>
  );
}
