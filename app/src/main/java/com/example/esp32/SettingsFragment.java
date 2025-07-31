package com.example.esp32;

import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    private TextToSpeech textToSpeech;
    private float speechRate;
    private SeekBar seekBar;
    private Button reconnectBtn;

    public SettingsFragment(TextToSpeech tts, float currentRate, Runnable reconnectCallback) {
        this.textToSpeech = tts;
        this.speechRate = currentRate;
        this.reconnectCallback = reconnectCallback;
    }

    public void setConnectionStatus(String status, int colour) {
        View view = getView();
        if (view != null) {
            TextView statusView = view.findViewById(R.id.connectionStatus);
            statusView.setText(status);
            statusView.setTextColor(colour);
        }
    }



    private final Runnable reconnectCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        seekBar = v.findViewById(R.id.speedSeekBar);
        TextView label = v.findViewById(R.id.speedLabel);
        reconnectBtn = v.findViewById(R.id.reconnectButton);

        seekBar.setProgress((int) ((speechRate - 0.5f) * 20 / 1.5f));
        label.setText(String.format("TTS Speed: %.1fx", speechRate));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newRate = 0.5f + (progress / 20.0f) * 1.5f;
                label.setText(String.format("TTS Speed: %.1fx", newRate));
                textToSpeech.setSpeechRate(newRate);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        reconnectBtn.setOnClickListener(vv -> reconnectCallback.run());

        return v;
    }
}
