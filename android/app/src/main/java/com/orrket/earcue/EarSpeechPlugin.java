package com.orrket.earcue;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;

/** Android SpeechRecognizer exposed to the web app. Prefers on-device recognition (no internet, no start beep). */
@CapacitorPlugin(name = "EarSpeech", permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "mic") })
public class EarSpeechPlugin extends Plugin {
    private SpeechRecognizer rec;
    private boolean active = false;
    private String lang = "ko-KR";

    @PluginMethod
    public void available(PluginCall call) {
        JSObject o = new JSObject();
        o.put("available", SpeechRecognizer.isRecognitionAvailable(getContext()));
        o.put("onDevice", Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()));
        call.resolve(o);
    }

    @PluginMethod
    public void start(PluginCall call) {
        lang = call.getString("lang", "ko-KR");
        if (getPermissionState("mic") != PermissionState.GRANTED) { requestPermissionForAlias("mic", call, "micGranted"); return; }
        begin(call);
    }

    @PermissionCallback
    private void micGranted(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) begin(call); else call.reject("mic-denied");
    }

    private void begin(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                if (rec == null) {
                    if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()))
                        rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext());
                    else rec = SpeechRecognizer.createSpeechRecognizer(getContext());
                    rec.setRecognitionListener(listener);
                }
                active = true;
                listen();
                call.resolve();
            } catch (Exception e) { call.reject("speech-start-failed: " + e.getMessage()); }
        });
    }

    private void listen() {
        if (!active || rec == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        if (Build.VERSION.SDK_INT >= 23) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try { rec.startListening(i); } catch (Exception e) { emitEnd("start-error"); }
    }

    private void emitText(Bundle b, boolean fin) {
        ArrayList<String> list = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return;
        JSObject o = new JSObject(); o.put("text", list.get(0)); o.put("final", fin);
        notifyListeners("result", o);
    }

    private void emitEnd(String why) { JSObject o = new JSObject(); o.put("reason", why); notifyListeners("end", o); }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle p) { notifyListeners("listening", new JSObject()); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float v) {}
        @Override public void onBufferReceived(byte[] b) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onError(int code) {
            // 7 = no match, 6 = speech timeout: normal between sentences; keep listening
            emitEnd("error-" + code);
            if (active && code != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && code != SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                getActivity().runOnUiThread(() -> { try { Thread.sleep(150); } catch (Exception ignored) {} listen(); });
        }
        @Override public void onResults(Bundle b) { emitText(b, true); emitEnd("results"); if (active) getActivity().runOnUiThread(EarSpeechPlugin.this::listen); }
        @Override public void onPartialResults(Bundle b) { emitText(b, false); }
        @Override public void onEvent(int t, Bundle b) {}
    };

    @PluginMethod
    public void stop(PluginCall call) {
        active = false;
        getActivity().runOnUiThread(() -> { if (rec != null) { try { rec.cancel(); rec.destroy(); } catch (Exception ignored) {} rec = null; } });
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() { active = false; if (rec != null) { try { rec.destroy(); } catch (Exception ignored) {} rec = null; } }
}
