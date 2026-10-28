import { useState } from 'react';
import { useAuthStore } from '../store/authStore';
import UserAvatar from './UserAvatar';
import ChannelList from './ChannelList';
import CreateChannelModal from './CreateChannelModal';

interface SidebarProps {
  connectionStatus: string;
}

export default function Sidebar({ connectionStatus }: SidebarProps) {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <aside className="sidebar">
      {/* Header */}
      <div className="sidebar-header">
        <h2>💬 Chat</h2>
        <div className={`connection-status ${connectionStatus}`}>
          <span className="status-dot" />
          {connectionStatus === 'connected' ? 'Online' :
           connectionStatus === 'connecting' ? 'Connecting' : 'Offline'}
        </div>
      </div>

      {/* Channel List */}
      <ChannelList onCreateChannel={() => setShowCreateModal(true)} />

      {/* Footer: User Info */}
      <div className="sidebar-footer">
        <div className="user-avatar-wrapper">
          <UserAvatar
            username={user?.displayName || user?.username || 'U'}
            showPresence
            online
          />
        </div>
        <div className="user-details">
          <div className="display-name">{user?.displayName || user?.username}</div>
          <div className="username">@{user?.username}</div>
        </div>
        <button
          className="btn-logout"
          onClick={logout}
          id="logout-btn"
          title="Sign out"
        >
          ↗ Out
        </button>
      </div>

      {/* Create Channel Modal */}
      {showCreateModal && (
        <CreateChannelModal onClose={() => setShowCreateModal(false)} />
      )}
    </aside>
  );
}
