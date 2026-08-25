# ST-03 — Chat Server TCP Listener

# Huu Tien

## Objective

Implement the TCP Server Listener responsible for accepting client
connections and integrating them with the existing server components.

## Scope

- Start `ServerSocket` on the configured port.
- Accept multiple TCP client connections.
- Create `ClientSession` for each connection.
- Handle client connection lifecycle.
- Dispatch `HELLO` to `LoginHandler`.
- Dispatch `CHAT` to `MessageRouter`.
- Handle client disconnect and connection errors.
- Support concurrent clients.

## Integration Fix

During code review, `LoginHandler` and `MessageRouter` were found to use
two independent session registries.

- `LoginHandler` → `OnlineUserRegistry`
- `MessageRouter` → `SessionRegistry`

Use `OnlineUserRegistry` as the single source of truth for authenticated
client sessions. Update `MessageRouter` accordingly.

`SessionRegistry` will not be removed until all project usages are
audited.

## Out of Scope

- GUI
- Database
- Reply / Forward
- Emoji / Avatar UI
- Reimplementation of authentication or message routing

## Acceptance Criteria

- Server accepts multiple TCP clients.
- Client sessions are created correctly.
- HELLO and CHAT are dispatched correctly.
- Online sessions use a single registry.
- Client disconnect does not crash the server.
- Existing and new tests pass.