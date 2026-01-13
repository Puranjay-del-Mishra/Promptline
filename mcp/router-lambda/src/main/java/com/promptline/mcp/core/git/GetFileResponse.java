package com.promptline.mcp.model.git;

public record GetFileResponse(String ref, String path, boolean found, String content) {}
