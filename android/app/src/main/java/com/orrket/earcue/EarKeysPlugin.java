package com.orrket.earcue;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Lets the web app decide whether hardware volume keys are captured as remote-control signals. */
@CapacitorPlugin(name = "EarKeys")
public class EarKeysPlugin extends Plugin {
    static volatile boolean captureVolume = false;

    @PluginMethod
    public void capture(PluginCall call) {
        captureVolume = Boolean.TRUE.equals(call.getBoolean("volume", false));
        call.resolve();
    }
}
