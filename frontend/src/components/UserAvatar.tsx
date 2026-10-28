interface UserAvatarProps {
  username: string;
  size?: 'sm' | 'md' | 'lg';
  showPresence?: boolean;
  online?: boolean;
}

function getAvatarColor(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return Math.abs(hash) % 8;
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

export default function UserAvatar({ username, size = 'md', showPresence = false, online = false }: UserAvatarProps) {
  const colorIndex = getAvatarColor(username);
  const initials = getInitials(username);
  const sizeClass = size === 'md' ? '' : size;

  return (
    <div className={`avatar avatar-color-${colorIndex} ${sizeClass}`}>
      {initials}
      {showPresence && (
        <span className={`presence-dot ${online ? 'online' : 'offline'}`} />
      )}
    </div>
  );
}
