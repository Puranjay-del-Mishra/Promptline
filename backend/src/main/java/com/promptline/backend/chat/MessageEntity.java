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
public class MessageEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatEntity chat;

    @Column(nullable = false, length = 20)
    private String role; // user | assistant | system

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageEntity() {}

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ChatEntity getChat() { return chat; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }

    public UUID getChatId() { return chat != null ? chat.getId() : null; }

    public void setChat(ChatEntity chat) { this.chat = chat; }
    public void setRole(String role) { this.role = role; }
    public void setContent(String content) { this.content = content; }
}
