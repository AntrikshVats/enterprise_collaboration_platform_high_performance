import { useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';
import { useChatStore } from '../store/chatStore';
import { usePresenceStore } from '../store/presenceStore';

type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';

interface UseWebSocketReturn {
  sendMessage: (channelId: string, content: string) => void;
  sendTyping: (channelId: string) => void;
  subscribe: (channelId: string) => void;
  unsubscribe: (channelId: string) => void;
  status: ConnectionStatus;
}

/**
 * Single persistent WebSocket connection hook.
 *
 * Features:
 * - Auto-reconnect with exponential backoff (1s → 2s → 4s → ... → 30s max)
 * - JWT auth via query parameter
 * - Heartbeat every 30s to keep presence alive
 * - Message routing to Zustand stores
 * - Typing indicator throttle (1 event per 2s per channel)
 */
export function useWebSocket(): UseWebSocketReturn {
  const wsRef = useRef<WebSocket | null>(null);
  const statusRef = useRef<ConnectionStatus>('disconnected');
  const reconnectAttemptRef = useRef(0);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const heartbeatIntervalRef = useRef<ReturnType<typeof setInterval>>();
  const typingThrottleRef = useRef<Record<string, number>>({});
  const mountedRef = useRef(true);

  const token = useAuthStore((s) => s.token);
  const addMessage = useChatStore((s) => s.addMessage);
  const setTypingUser = useChatStore((s) => s.setTypingUser);
  const setOnline = usePresenceStore((s) => s.setOnline);
  const setOffline = usePresenceStore((s) => s.setOffline);

  const connect = useCallback(() => {
    if (!token || !mountedRef.current) return;
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    statusRef.current = 'connecting';

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const ws = new WebSocket(`${protocol}//${host}/ws?token=${token}`);

    ws.onopen = () => {
      statusRef.current = 'connected';
      reconnectAttemptRef.current = 0;

      // Start heartbeat
      heartbeatIntervalRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'HEARTBEAT' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        switch (data.type) {
          case 'MESSAGE':
            addMessage({
              id: data.id,
              channelId: data.channelId,
              senderId: data.senderId,
              senderName: data.senderName,
              content: data.content,
              timestamp: data.timestamp,
            });
            break;

          case 'TYPING':
            setTypingUser(data.channelId, data.userId, data.username);
            break;

          case 'PRESENCE':
            if (data.status === 'ONLINE') {
              setOnline(data.userId);
            } else {
              setOffline(data.userId);
            }
            break;

          case 'ACK':
            // Message delivered confirmation — could update UI if needed
            break;

          case 'ERROR':
            console.error('[WS] Server error:', data.code, data.message);
            break;

          default:
            break;
        }
      } catch (e) {
        console.error('[WS] Failed to parse message:', e);
      }
    };

    ws.onclose = (event) => {
      statusRef.current = 'disconnected';
      wsRef.current = null;

      if (heartbeatIntervalRef.current) {
        clearInterval(heartbeatIntervalRef.current);
      }

      // Auto-reconnect with exponential backoff
      if (mountedRef.current && token) {
        const delay = Math.min(
          1000 * Math.pow(2, reconnectAttemptRef.current),
          30000
        );
        reconnectAttemptRef.current++;

        reconnectTimeoutRef.current = setTimeout(() => {
          connect();
        }, delay);
      }
    };

    ws.onerror = (error) => {
      console.error('[WS] WebSocket error:', error);
    };

    wsRef.current = ws;
  }, [token, addMessage, setTypingUser, setOnline, setOffline]);

  // Connect on mount / token change
  useEffect(() => {
    mountedRef.current = true;
    connect();

    return () => {
      mountedRef.current = false;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (heartbeatIntervalRef.current) {
        clearInterval(heartbeatIntervalRef.current);
      }
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connect]);

  // Clear expired typing indicators every second
  useEffect(() => {
    const clearExpired = useChatStore.getState().clearExpiredTyping;
    const interval = setInterval(clearExpired, 1000);
    return () => clearInterval(interval);
  }, []);

  const send = useCallback((data: object) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data));
    }
  }, []);

  const sendMessage = useCallback((channelId: string, content: string) => {
    send({ type: 'MESSAGE', channelId, content });
  }, [send]);

  const sendTyping = useCallback((channelId: string) => {
    const now = Date.now();
    const lastSent = typingThrottleRef.current[channelId] || 0;
    // Throttle: max 1 typing event per 2 seconds per channel
    if (now - lastSent > 2000) {
      typingThrottleRef.current[channelId] = now;
      send({ type: 'TYPING', channelId });
    }
  }, [send]);

  const subscribe = useCallback((channelId: string) => {
    send({ type: 'SUBSCRIBE', channelId });
  }, [send]);

  const unsubscribe = useCallback((channelId: string) => {
    send({ type: 'UNSUBSCRIBE', channelId });
  }, [send]);

  return {
    sendMessage,
    sendTyping,
    subscribe,
    unsubscribe,
    status: statusRef.current,
  };
}
