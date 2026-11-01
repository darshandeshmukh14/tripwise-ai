package com.tripwise.entity;

/**
 * Must match the {@code ck_chat_messages_sender_role} check constraint.
 */
public enum SenderRole {
    USER,
    ASSISTANT,
    SYSTEM
}
