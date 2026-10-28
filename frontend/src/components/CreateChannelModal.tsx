import { useState } from 'react';
import { useChatStore, Channel } from '../store/chatStore';
import httpClient from '../api/httpClient';

interface CreateChannelModalProps {
  onClose: () => void;
}

export default function CreateChannelModal({ onClose }: CreateChannelModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState('PUBLIC');
  const [loading, setLoading] = useState(false);
  const addChannel = useChatStore((s) => s.addChannel);

  const handleCreate = async () => {
    if (!name.trim()) return;
    setLoading(true);

    try {
      const res = await httpClient.post('/channels', {
        name: name.toLowerCase().replace(/\s+/g, '-'),
        description,
        type,
      });
      addChannel(res.data as Channel);
      onClose();
    } catch (err) {
      console.error('Failed to create channel:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <h2>Create Channel</h2>

        <div className="form-group" style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: 6 }}>
            Channel Name
          </label>
          <input
            id="channel-name-input"
            type="text"
            placeholder="e.g. project-alpha"
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'var(--bg-tertiary)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--text-primary)',
              outline: 'none',
            }}
            autoFocus
          />
        </div>

        <div className="form-group" style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: 6 }}>
            Description
          </label>
          <input
            id="channel-desc-input"
            type="text"
            placeholder="What's this channel about?"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'var(--bg-tertiary)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--text-primary)',
              outline: 'none',
            }}
          />
        </div>

        <div className="form-group" style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: 6 }}>
            Type
          </label>
          <select
            id="channel-type-select"
            value={type}
            onChange={(e) => setType(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'var(--bg-tertiary)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--text-primary)',
              outline: 'none',
            }}
          >
            <option value="PUBLIC">Public</option>
            <option value="GROUP">Group</option>
          </select>
        </div>

        <div className="modal-actions">
          <button className="btn-secondary" onClick={onClose}>Cancel</button>
          <button
            id="channel-create-btn"
            className="btn-primary"
            style={{ width: 'auto' }}
            onClick={handleCreate}
            disabled={loading || !name.trim()}
          >
            {loading ? 'Creating...' : 'Create Channel'}
          </button>
        </div>
      </div>
    </div>
  );
}
