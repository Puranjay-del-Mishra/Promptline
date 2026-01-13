package com.promptline.backend.runtime;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeConfigController {

    private final RuntimeConfigStore store;

    public RuntimeConfigController(RuntimeConfigStore store) {
        this.store = store;
    }

    @GetMapping(value = "/ui-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public String uiConfig() {
        return store.getUiConfig().json();
    }

    @GetMapping(value = "/policy", produces = MediaType.APPLICATION_JSON_VALUE)
    public String policy() {
        return store.getPolicy().json();
    }
}
