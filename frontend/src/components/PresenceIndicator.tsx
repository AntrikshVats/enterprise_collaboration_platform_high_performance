interface PresenceIndicatorProps {
  online: boolean;
}

export default function PresenceIndicator({ online }: PresenceIndicatorProps) {
  return (
    <span className={`presence-dot ${online ? 'online' : 'offline'}`} />
  );
}
