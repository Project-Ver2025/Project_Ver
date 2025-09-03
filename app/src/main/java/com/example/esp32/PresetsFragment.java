package com.example.esp32;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.function.Consumer;

public class PresetsFragment extends Fragment {
    private final String[] buttonLabels = new String[] {"One", "Two", "Three", "Four"};
    private final Consumer<String> sendCommand;
    private static final String PREFS_NAME = "PresetPrefs";

    public PresetsFragment(Consumer<String> sendCommand) {
        this.sendCommand = sendCommand;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_presets, container, false);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int[] ids = {R.id.button1, R.id.button2, R.id.button3, R.id.button4};
        for (int i = 0; i < 4; i++) {
            int index = i;
            Button b = view.findViewById(ids[i]);

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


            b.setOnLongClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("Edit Preset");

                final EditText input = new EditText(requireContext());
                input.setText(buttonLabels[index]);
                builder.setView(input);

                builder.setPositiveButton("OK", (dialog, which) -> {
                    String newLabel = input.getText().toString();
                    buttonLabels[index] = newLabel;
                    b.setText(newLabel);

                    // Save updated label
                    prefs.edit().putString("preset_" + index, newLabel).apply();
                });

                builder.setNegativeButton("Cancel", null);
                builder.show();
                return true;
            });
        }

        return view;
    }
}
