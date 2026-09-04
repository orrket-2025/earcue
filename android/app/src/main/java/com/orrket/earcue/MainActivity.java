package com.orrket.earcue;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(EarTtsPlugin.class);
        registerPlugin(EarSpeechPlugin.class);
        registerPlugin(EarKeysPlugin.class);
        super.onCreate(savedInstanceState);
        // a prompter must not dim or lock while it is on the lectern
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String key = keyName(keyCode);
        if (key != null && EarKeysPlugin.captureVolume) {
            if (event.getRepeatCount() == 0) sendKey(key);
            return true; // swallow so the volume UI does not appear
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyName(keyCode) != null && EarKeysPlugin.captureVolume) return true;
        return super.onKeyUp(keyCode, event);
    }

    private static String keyName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP: return "VolumeUp";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "VolumeDown";
            case KeyEvent.KEYCODE_MEDIA_NEXT: return "Media:next";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "Media:prev";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE: case KeyEvent.KEYCODE_HEADSETHOOK: return "Media:play";
            default: return null;
        }
    }

    private void sendKey(String key) {
        String js = "window.__earNativeKey && window.__earNativeKey(" + org.json.JSONObject.quote(key) + ")";
        getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null));
    }
}
