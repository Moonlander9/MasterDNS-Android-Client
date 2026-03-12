# UDP Support Escalation Spec (Android Follow-up)

This document defines the Android-side handoff contract for true end-to-end UDP support if Phase 1 (internal mapped DNS) does not fully resolve connectivity.

## Trigger
Proceed only if, after Phase 1 rollout, TCP browsing still fails on at least one validated network/profile while tunnel status is `CONNECTED`.

## Scope Boundary
- In scope (this Android repo): app/tunnel integration requirements and compatibility constraints.
- Out of scope (must be implemented in shared Python/backend repos): protocol packet additions, server transport changes, and UDP relay engine behavior.

## Required Protocol Additions
1. Add explicit UDP stream semantics
- Introduce packet types for UDP open/data/ack/close separate from TCP `STREAM_*` and `SOCKS5_SYN*`.
- Preserve existing header structure versioning so old clients continue to parse known packet types.

2. Datagram framing contract
- Every UDP payload frame must carry source stream/session identifiers, destination tuple metadata, and payload length.
- Fragmentation/reassembly rules must be deterministic and bounded.

3. Backward compatibility
- Unknown UDP packet types must be ignored safely by older peers.
- Session negotiation must advertise UDP capability before either side sends UDP frames.

## NAT and Session Mapping
1. Mapping key
- Maintain per-session mapping keyed by `(session_id, udp_stream_id, remote_addr, remote_port)`.

2. Lifetime and cleanup
- Idle UDP mappings must expire on timeout.
- Mapping teardown must occur on session close, tunnel restart, and explicit UDP close control packet.

3. Resource limits
- Enforce per-session and global UDP mapping caps to avoid memory growth.

## Reliability and Retransmit Policy
1. Control reliability
- UDP control packets (open/close/errors) require acknowledgement and retry with bounded backoff.

2. Data reliability mode
- UDP data should default to best-effort delivery (no forced ARQ-style strict ordering).
- Optional reliability mode, if added later, must be negotiated and isolated from default behavior.

3. Failure handling
- Repeated UDP control failure must not tear down healthy TCP streams in the same session.

## SOCKS5 Handshake Compatibility
1. Commands
- Support SOCKS5 UDP commands needed by local tunnel integrations (`0x03` and `0x05` where applicable).

2. Fallback behavior
- If UDP negotiation fails, return a clear SOCKS command failure and emit a diagnostic code; do not silently blackhole traffic.

3. Observability
- Emit structured event codes for UDP setup success/failure, mapping expiration, and unsupported-command fallback.

## Android Acceptance Criteria (when backend UDP is ready)
1. DNS and general TCP browsing still work under full tunnel mode.
2. UDP-capable apps can pass traffic without forcing tunnel offline behavior.
3. Tunnel reconnect/network switch preserves or cleanly rebuilds UDP mappings without app-wide outage.
4. Diagnostics distinguish DNS failures, UDP negotiation failures, and remote-path packet loss.
