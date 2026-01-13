package com.promptline.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.promptline.backend.chat.ChatEntity;
import com.promptline.backend.chat.MessageEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatEntity chat;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private MessageEntity message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposal_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode proposalJson;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ---- getters/setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ChatEntity getChat() { return chat; }
    public void setChat(ChatEntity chat) { this.chat = chat; }

    public MessageEntity getMessage() { return message; }
    public void setMessage(MessageEntity message) { this.message = message; }

    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }

    public JsonNode getProposalJson() { return proposalJson; }
    public void setProposalJson(JsonNode proposalJson) { this.proposalJson = proposalJson; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
