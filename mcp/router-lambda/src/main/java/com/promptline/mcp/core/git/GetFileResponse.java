package com.promptline.mcp.core.git;

public record GetFileResponse(String ref, String path, boolean found, String content) {}
