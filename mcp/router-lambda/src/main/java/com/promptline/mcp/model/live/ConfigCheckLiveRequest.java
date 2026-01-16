package com.promptline.mcp.model.live;

import java.util.List;

public record ConfigCheckLiveRequest(
        String env,                 // "live" (informational for now)
        List<ConfigChange> changes  // required
) {}
