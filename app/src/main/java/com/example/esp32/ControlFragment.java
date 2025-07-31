package com.example.esp32;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.function.Consumer;

public class ControlFragment extends Fragment {
    Button startStopButton;
    private Button cancelButton;
    boolean isRecording = false;
    private Consumer<String> commandSender;
    private MediaPlayer loadingPlayer;
    private MediaPlayer completePlayer;
    private MediaPlayer cancelPlayer;

    public ControlFragment(Consumer<String> commandSender) {
        this.commandSender = commandSender;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        loadingPlayer = MediaPlayer.create(getContext(), R.raw.loading);
        completePlayer = MediaPlayer.create(getContext(), R.raw.complete);
        cancelPlayer = MediaPlayer.create(getContext(), R.raw.cancel);

        startStopButton = view.findViewById(R.id.startStopButton);
        cancelButton = view.findViewById(R.id.cancelButton);

        startStopButton.setOnClickListener(v -> {
            if (!isRecording) {
                commandSender.accept("start");
                startStopButton.setText("Stop");
                isRecording = true;

                if (loadingPlayer != null) {
                    loadingPlayer.start();
                }
            } else {
                commandSender.accept("stop");
                startStopButton.setText("Start");
                isRecording = false;

                if (completePlayer != null) {
                    completePlayer.start();
                }

            }
        });

        cancelButton.setOnClickListener(v -> {
            commandSender.accept("cancel");

            if (cancelPlayer != null) {
                cancelPlayer.start();
            }
        });

        return view;
    }
}
