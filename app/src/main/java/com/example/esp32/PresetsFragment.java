package com.example.esp32;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.function.Consumer;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.Locale;

public class PresetsFragment extends Fragment {
    private final String[] buttonLabels = new String[] {"One", "Two", "Three", "Four"};
    private final Consumer<String> sendCommand;
    private static final String PREFS_NAME = "PresetPrefs";
    private static final int SPEECH_REQUEST_CODE = 100;
    private TextToSpeech tts;

    private int editingIndex = -1; // Track which button is being edited

    public PresetsFragment(Consumer<String> sendCommand) {
        this.sendCommand = sendCommand;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_presets, container, false);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        int[] ids = {R.id.button1, R.id.button2, R.id.button3, R.id.button4};
        int[] editIds = {R.id.editButton1, R.id.editButton2, R.id.editButton3, R.id.editButton4};


        for (int i = 0; i < 4; i++) {
            int index = i;
            Button b = view.findViewById(ids[i]);
            ImageButton edit = view.findViewById(editIds[i]);


            // Load saved label or use default
            String saved = prefs.getString("preset_" + index, buttonLabels[index]);
            buttonLabels[index] = saved;
            b.setText(saved);

            b.setOnClickListener(v -> {
                String msg = buttonLabels[index];
                sendCommand.accept(index + " " + msg); // Send via BLE

                // Trigger loading sound through MainActivity
                if (getActivity() instanceof MainActivity) {
                    MainActivity main = (MainActivity) getActivity();
                    if (main.isServiceBound()) {
                        main.getBluetoothService().startLoadingSoundFromCommand();
                    }
                }
            });


//            b.setOnLongClickListener(v -> {
//                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
//                builder.setTitle("Edit Preset");
//
//                final EditText input = new EditText(requireContext());
//                input.setText(buttonLabels[index]);
//                builder.setView(input);
//
//                builder.setPositiveButton("OK", (dialog, which) -> {
//                    String newLabel = input.getText().toString();
//                    buttonLabels[index] = newLabel;
//                    b.setText(newLabel);
//
//                    // Save updated label
//                    prefs.edit().putString("preset_" + index, newLabel).apply();
//                });
//
//                builder.setNegativeButton("Cancel", null);
//                builder.show();
//                return true;
//            });


//            b.setOnLongClickListener(v -> {
//                editingIndex = index;
//
//                if (getActivity() instanceof MainActivity) {
//                    MainActivity main = (MainActivity) getActivity();
//                    if (main.isServiceBound()) {
//                        main.getBluetoothService().playBeep(); // <- implement in service
//                    }
//                }
//
//                startSpeechToText();
//                return true;
//            });

            edit.setOnClickListener(v -> {
                editingIndex = index;

                if (getActivity() instanceof MainActivity) {
                    MainActivity main = (MainActivity) getActivity();
                    if (main.isServiceBound()) {
                        main.getBluetoothService().playBeep();
                    }
                }

                startSpeechToText();
            });
        }

        return view;
    }

    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your preset");
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && editingIndex >= 0) {
                String newLabel = results.get(0); // take best match
                buttonLabels[editingIndex] = newLabel;

                // Update button text
                int[] ids = {R.id.button1, R.id.button2, R.id.button3, R.id.button4};
                Button b = getView().findViewById(ids[editingIndex]);
                b.setText(newLabel);

                // Save in SharedPreferences
                SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString("preset_" + editingIndex, newLabel).apply();

                if (tts != null) {
                    tts.speak("Saved preset " + newLabel, TextToSpeech.QUEUE_FLUSH, null, "presetSaved");
                }

                editingIndex = -1;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
