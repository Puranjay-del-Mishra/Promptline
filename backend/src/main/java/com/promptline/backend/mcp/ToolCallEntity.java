package com.promptline.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_calls")
public class ToolCallEntity {

    @PreUpdate
    public void preUpdate() {
        System.out.println("ToolCallEntity UPDATE id=" + id + " status=" + status);
        Thread.dumpStack();
    }

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlanEntity plan;

    @Column(nullable = false)
    private String tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "args_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode argsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolCallStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private JsonNode resultJson;

    @Column
    private String error;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ---- getters/setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PlanEntity getPlan() { return plan; }
    public void setPlan(PlanEntity plan) { this.plan = plan; }

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }

    public JsonNode getArgsJson() { return argsJson; }
    public void setArgsJson(JsonNode argsJson) { this.argsJson = argsJson; }

    public ToolCallStatus getStatus() { return status; }
    public void setStatus(ToolCallStatus status) { this.status = status; }

    public JsonNode getResultJson() { return resultJson; }
    public void setResultJson(JsonNode resultJson) { this.resultJson = resultJson; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
