CREATE TABLE IF NOT EXISTS conversations (
    conv_id TEXT PRIMARY KEY,
    type TEXT NOT NULL CHECK (type IN ('DM', 'GROUP', 'PUBLIC')),
    name TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_message_preview TEXT DEFAULT NULL,
    last_activity BIGINT DEFAULT NULL
);
CREATE TABLE IF NOT EXISTS conversation_members (
    conv_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'member')),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conv_id, user_id),
    FOREIGN KEY (conv_id) REFERENCES conversations(conv_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_members_user_conv ON conversation_members (user_id, conv_id);
CREATE TABLE IF NOT EXISTS messages (
    sequence_id INTEGER PRIMARY KEY,
    message_id TEXT NOT NULL UNIQUE,
    conv_id TEXT NOT NULL,
    sender_username TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'text' CHECK (kind IN ('text', 'reply', 'forward', 'system')),
    reply_to_message_id TEXT DEFAULT NULL,
    forward_from_message_id TEXT DEFAULT NULL,
    forward_from_conv_id TEXT DEFAULT NULL,
    FOREIGN KEY (conv_id) REFERENCES conversations(conv_id) ON DELETE CASCADE,
    FOREIGN KEY (sender_username) REFERENCES users(username)
);
CREATE INDEX IF NOT EXISTS idx_messages_conv_sequence ON messages (conv_id, sequence_id DESC);