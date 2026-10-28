-- ============================================
-- Enterprise Chat Platform — Database Schema
-- PostgreSQL 16
-- ============================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url  VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- ============================================
-- Channels Table
-- ============================================
CREATE TABLE IF NOT EXISTS channels (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type        VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channels_type ON channels (type);

-- ============================================
-- Channel Members (Join Table)
-- ============================================
CREATE TABLE IF NOT EXISTS channel_members (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    channel_id  UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(channel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_members_channel ON channel_members (channel_id);
CREATE INDEX IF NOT EXISTS idx_channel_members_user ON channel_members (user_id);

-- ============================================
-- Messages Table (MIGRATED TO CASSANDRA)
-- ============================================
/*
CREATE TABLE IF NOT EXISTS messages (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    channel_id  UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id   UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Critical index: fetch latest messages per channel
CREATE INDEX IF NOT EXISTS idx_messages_channel_time
    ON messages (channel_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages (sender_id);
*/

-- ============================================
-- Seed: Default #general channel
-- ============================================
INSERT INTO channels (id, name, description, type)
VALUES ('00000000-0000-0000-0000-000000000001', 'general', 'General discussion channel', 'PUBLIC')
ON CONFLICT DO NOTHING;
