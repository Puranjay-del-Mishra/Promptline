package com.promptline.backend.mcp;

public enum PlanStatus {
    PROPOSED,    // generated, waiting for green tick
    SUPERSEDED,  // replaced by newer plan
    CONFIRMED,   // green tick pressed
    RUNNING,     // executing phase 0/1/2
    COMPLETED,   // execution finished successfully
    FAILED       // execution failed
}

