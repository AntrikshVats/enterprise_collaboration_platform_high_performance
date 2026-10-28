# OS-Level Tuning for 50,000+ WebSocket Connections

This guide covers the OS-level configuration required to support 50,000 concurrent WebSocket connections on both Windows and Linux systems.

## Windows (Development / Your Laptop)

### 1. Increase Maximum User Ports

By default, Windows limits the ephemeral port range to ~16,000 ports. For 50k connections, we need to expand this.

**Option A: Registry Edit (Requires Reboot)**

```powershell
# Run PowerShell as Administrator

# Increase max ephemeral ports to 65534
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters" `
    -Name "MaxUserPort" -Value 65534 -PropertyType DWord -Force

# Reduce TIME_WAIT delay for faster port recycling (30 seconds instead of 240)
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters" `
    -Name "TcpTimedWaitDelay" -Value 30 -PropertyType DWord -Force

# REBOOT REQUIRED
Restart-Computer
```

**Option B: Dynamic Port Range via netsh (No Reboot, but resets on restart)**

```powershell
# View current dynamic port range
netsh int ipv4 show dynamicport tcp

# Expand to start at 1025 with 64510 ports available
netsh int ipv4 set dynamicport tcp start=1025 num=64510
```

### 2. Increase Max Connections in the TCP Stack

```powershell
# Allow more half-open connections (SYN queue)
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters" `
    -Name "TcpMaxHalfOpen" -Value 10000 -PropertyType DWord -Force
```

### 3. Windows Defender / Firewall

Large numbers of WebSocket connections may trigger Windows Defender heuristics. Add exclusions for your Java process:

```powershell
Add-MpExclusion -Process "java.exe"
Add-MpExclusion -Path "C:\path\to\your\backend\target"
```

---

## Linux (Production)

### 1. File Descriptors

Each WebSocket connection consumes one file descriptor. The default limit (1024) is far too low.

```bash
# /etc/security/limits.conf
# Add these lines (or create /etc/security/limits.d/websocket.conf):
*  soft  nofile  200000
*  hard  nofile  200000
root  soft  nofile  200000
root  hard  nofile  200000
```

Verify after login:
```bash
ulimit -n
# Should show 200000
```

### 2. Kernel Parameters

```bash
# /etc/sysctl.conf — Append these:

# Max connections in the backlog queue
net.core.somaxconn = 65535

# Max SYN backlog for incoming connections
net.ipv4.tcp_max_syn_backlog = 65535

# Expand ephemeral port range
net.ipv4.ip_local_port_range = 1024 65535

# Allow rapid reuse of TIME_WAIT sockets
net.ipv4.tcp_tw_reuse = 1

# Reduce FIN timeout
net.ipv4.tcp_fin_timeout = 15

# Increase socket buffer sizes for high throughput
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# Increase max file handles system-wide
fs.file-max = 500000

# Apply changes:
# sudo sysctl -p
```

### 3. Systemd Service Limits

If running as a systemd service, set `LimitNOFILE` in the unit file:

```ini
[Service]
LimitNOFILE=200000
```

---

## Verification

After applying settings, verify with a load test tool:

```bash
# Using websocat for WebSocket load testing
# Install: cargo install websocat
for i in $(seq 1 100); do
  websocat ws://localhost:8080/ws?token=YOUR_TOKEN &
done

# Check open connections
ss -s
# or on Windows:
netstat -an | find /c "ESTABLISHED"
```
