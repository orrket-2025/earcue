package com.orrket.earcue;

import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;
import java.util.Set;

/** Android TextToSpeech exposed to the web app: precise rate control, offline voices, progress ranges. */
@CapacitorPlugin(name = "EarTts")
public class EarTtsPlugin extends Plugin {
    private TextToSpeech tts;
    private boolean ready = false;

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                tts.setLanguage(Locale.KOREAN);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { emit("start", id, null); }
                    @Override public void onDone(String id) { emit("done", id, null); }
                    @Override public void onError(String id) { emit("error", id, "error"); }
                    @Override public void onError(String id, int code) { emit("error", id, "error-" + code); }
                    @Override public void onStop(String id, boolean interrupted) { emit("stop", id, null); }
                    @Override public void onRangeStart(String id, int start, int end, int frame) {
                        JSObject o = new JSObject(); o.put("id", id); o.put("start", start); o.put("end", end);
                        notifyListeners("range", o);
                    }
                });
            }
            JSObject o = new JSObject(); o.put("ready", ready);
            notifyListeners("ready", o);
        });
    }

    private void emit(String event, String id, String detail) {
        JSObject o = new JSObject(); o.put("id", id); if (detail != null) o.put("detail", detail);
        notifyListeners(event, o);
    }

    @PluginMethod
    public void isReady(PluginCall call) { JSObject o = new JSObject(); o.put("ready", ready); call.resolve(o); }

    @PluginMethod
    public void voices(PluginCall call) {
        JSArray arr = new JSArray();
        if (ready) {
            Set<Voice> set = null;
            try { set = tts.getVoices(); } catch (Exception ignored) {}
            if (set != null) for (Voice v : set) {
                JSObject o = new JSObject();
                o.put("name", v.getName());
                o.put("lang", v.getLocale().toLanguageTag());
                o.put("network", v.isNetworkConnectionRequired());
                o.put("quality", v.getQuality());
                arr.put(o);
            }
        }
        JSObject out = new JSObject(); out.put("voices", arr); out.put("ready", ready);
        call.resolve(out);
    }

    @PluginMethod
    public void speak(PluginCall call) {
        if (!ready) { call.reject("tts-not-ready"); return; }
        String text = call.getString("text", "");
        String id = call.getString("id", "u");
        float rate = call.getFloat("rate", 1.0f);
        float pitch = call.getFloat("pitch", 1.0f);
        String voiceName = call.getString("voice", null);
        if (voiceName != null && !voiceName.isEmpty()) {
            try { for (Voice v : tts.getVoices()) if (v.getName().equals(voiceName)) { tts.setVoice(v); break; } } catch (Exception ignored) {}
        } else {
            tts.setLanguage(Locale.KOREAN);
        }
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
        int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id);
        if (r != TextToSpeech.SUCCESS) { call.reject("tts-speak-failed"); return; }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) { if (tts != null) tts.stop(); call.resolve(); }

    @Override
    protected void handleOnDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } }
}
