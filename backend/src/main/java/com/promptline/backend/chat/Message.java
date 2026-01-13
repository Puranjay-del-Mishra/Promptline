package com.promptline.backend.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "messages",
    indexes = {
        @Index(name = "idx_messages_chat_created", columnList = "chat_id, created_at")
    }
)
public class Message {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(nullable = false, length = 20)
    private String role; // user | assistant | system

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {}

    public Message(Chat chat, String role, String content) {
        this.id = UUID.randomUUID();
        this.chat = chat;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getChatId() { return chat.getId(); }
    public Chat getChat() { return chat; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
