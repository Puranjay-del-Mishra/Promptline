package com.promptline.backend.llm;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

import com.promptline.backend.mcp.ToolCallStatus;

@Entity
@Table(name = "tool_calls")
public class ToolCall {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private String tool;

    @Column(name = "args_json", columnDefinition = "jsonb", nullable = false)
    private String argsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolCallStatus status = ToolCallStatus.PENDING;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---- getters/setters ----

    public UUID getId() { return id; }

    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }

    public String getArgsJson() { return argsJson; }
    public void setArgsJson(String argsJson) { this.argsJson = argsJson; }

    public ToolCallStatus getStatus() { return status; }
    public void setStatus(ToolCallStatus status) { this.status = status; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
