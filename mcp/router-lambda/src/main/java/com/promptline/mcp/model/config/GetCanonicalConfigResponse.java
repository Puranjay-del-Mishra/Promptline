package com.promptline.mcp.model.config;

public record GetCanonicalConfigResponse(String ref, String uiPath, String policyPath, String uiJson, String policyJson) {}
