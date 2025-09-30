package com.example.esp32;

import android.content.Context;
import android.content.SharedPreferences;
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

            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                if (main.isServiceBound() && main.getBluetoothService() != null) {
                    if (main.getBluetoothService().checkAndWarnConnection()) {
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
                    }
                }
            }

        });

        cancelButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                if (main.isServiceBound() && main.getBluetoothService() != null) {
                    if (main.getBluetoothService().checkAndWarnConnection()) {
                        commandSender.accept("cancel");
                        if (cancelPlayer != null) {
                            cancelPlayer.start();
                        }
                    }
                }
            }

        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Access MainActivity methods
        if (getActivity() instanceof MainActivity) {
            MainActivity main = (MainActivity) getActivity();
            SharedPreferences prefs = main.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

            int fontSize = prefs.getInt("font_size", 26);
            int textColor = prefs.getInt("text_color", android.graphics.Color.BLACK);

            // Apply font size + text color to all TextViews/Buttons in this fragment
            main.applyGlobalFontSize(fontSize);
            main.applyGlobalTextColor(textColor);

            // Apply preset button colors
            for (int i = 1; i <= 2; i++) {
                int btnColor = prefs.getInt("button_color_" + i, android.graphics.Color.LTGRAY);
                main.applyGlobalButtonColor(i, btnColor);
            }
        }
    }

}
