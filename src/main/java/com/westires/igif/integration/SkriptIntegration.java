// Opsiyonel Skript desteği. Skript yoksa hiçbir şey patlamaz.
package com.westires.igif.integration;

import com.westires.igif.api.iGIFAPI;
import com.westires.igif.util.ConsoleLogger;

public final class SkriptIntegration {

    private final iGIFAPI api;
    private final ConsoleLogger log;
    private boolean enabled = false;

    public SkriptIntegration(iGIFAPI api, ConsoleLogger log) {
        this.api = api;
        this.log = log;
    }

    public void injectApi() {
        
    }

    public void register() {
        if (!isSkriptPresent()) {
            log.debug("Skript not found — skipping Skript integration.");
            return;
        }
        try {
            
            
            Class<?> effectsClass = Class.forName("com.westires.igif.integration.SkriptEffects");
            effectsClass.getMethod("register", iGIFAPI.class).invoke(null, api);
            enabled = true;
            log.info("Skript integration: ENABLED");
        } catch (Exception e) {
            log.warn("Skript integration failed: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }

    private boolean isSkriptPresent() {
        try {
            Class.forName("ch.njol.skript.Skript");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
