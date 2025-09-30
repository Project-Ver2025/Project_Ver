package com.example.esp32;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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

    private final Runnable reconnectCallback;

    private SeekBar fontSizeSeekBar;
    private TextView fontSizeLabel;
    private Button textColorButton;
    private Button btnColor1, btnColor2, btnColor3, btnColor4;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        // Connection Status
        TextView statusView = v.findViewById(R.id.connectionStatus);
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            statusView.setText(activity.getLastStatusText());
            statusView.setTextColor(activity.getLastStatusColor());
        }

        // Speech Rate
        seekBar = v.findViewById(R.id.speedSeekBar);
        TextView label = v.findViewById(R.id.speedLabel);
        reconnectBtn = v.findViewById(R.id.reconnectButton);

        float savedRate = prefs.getFloat("speech_rate", speechRate);
        speechRate = savedRate;
        textToSpeech.setSpeechRate(speechRate);

        seekBar.setProgress((int) ((speechRate - 0.5f) * 20 / 1.5f));
        label.setText(String.format("Speech Speed: %.1fx", speechRate));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newRate = 0.5f + (progress / 20.0f) * 1.5f;
                label.setText(String.format("Speech Speed: %.1fx", newRate));
                textToSpeech.setSpeechRate(newRate);
                prefs.edit().putFloat("speech_rate", newRate).apply();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        reconnectBtn.setOnClickListener(vv -> reconnectCallback.run());

        // FONT SIZE CONTROL
        fontSizeSeekBar = v.findViewById(R.id.fontSizeSeekBar);
        fontSizeLabel = v.findViewById(R.id.fontSizeLabel);

        int savedFontSize = prefs.getInt("font_size", 16);

        int minFontSize = 12; // or whatever minimum you want
        fontSizeSeekBar.setMax(36 - minFontSize);
        fontSizeSeekBar.setProgress(savedFontSize - minFontSize);
        fontSizeLabel.setText("Font Size: " + savedFontSize + "sp");

        fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = minFontSize + progress;
                fontSizeLabel.setText("Font Size: " + newSize + "sp");
                prefs.edit().putInt("font_size", newSize).apply();
                applyFontSizeToAll(v, newSize);

                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).applyGlobalFontSize(newSize);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        applyFontSizeToAll(v, prefs.getInt("font_size", 22));
        applyTextColorToAll(v, prefs.getInt("text_color", Color.BLACK));

        // TEXT COLOR PICKER
        textColorButton = v.findViewById(R.id.textColorButton);
        textColorButton.setOnClickListener(vv -> {
            showColorPicker(color -> {
                prefs.edit().putInt("text_color", color).apply();
                applyTextColorToAll(v, color);
            });
        });

        int savedTextColor = prefs.getInt("text_color", Color.BLACK);
        applyTextColorToAll(v, savedTextColor);

        // BUTTON COLOR PICKERS
        btnColor1 = v.findViewById(R.id.buttonColor1);
        btnColor2 = v.findViewById(R.id.buttonColor2);
        btnColor3 = v.findViewById(R.id.buttonColor3);
        btnColor4 = v.findViewById(R.id.buttonColor4);

        setupButtonColorPicker(btnColor1, 1, prefs);
        setupButtonColorPicker(btnColor2, 2, prefs);
        setupButtonColorPicker(btnColor3, 3, prefs);
        setupButtonColorPicker(btnColor4, 4, prefs);



        Button resetDefaults = v.findViewById(R.id.resetDefaultsButton);
        resetDefaults.setOnClickListener(vv -> {
            SharedPreferences.Editor editor = prefs.edit();

            // Reset all stored preferences
            editor.clear().apply();

            // Reset TTS speed
            textToSpeech.setSpeechRate(1.0f);
            seekBar.setProgress((int) ((1.0f - 0.5f) * 20 / 1.5f)); // match seekbar calculation
            // Reset font size
            applyFontSizeToAll(v, 24);
            fontSizeSeekBar.setProgress(36-24);
            fontSizeLabel.setText("Font Size: 24sp");

            // Reset text color
            applyTextColorToAll(v, Color.BLACK);

            // Reset button colors to gray
            int defaultBtnColor = Color.LTGRAY;
            btnColor1.setBackgroundColor(defaultBtnColor);
            btnColor2.setBackgroundColor(defaultBtnColor);
            btnColor3.setBackgroundColor(defaultBtnColor);
            btnColor4.setBackgroundColor(defaultBtnColor);

            // Apply instantly across fragments via MainActivity
            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                main.applyGlobalFontSize(22);
                main.applyGlobalTextColor(Color.BLACK);
                for (int i = 1; i <= 4; i++) {
                    main.applyGlobalButtonColor(i, defaultBtnColor);
                }
            }
        });


        return v;


    }

    // --- Helpers ---
    private void applyFontSizeToAll(View root, int size) {
        if (root instanceof TextView) {
            ((TextView) root).setTextSize(size);
        } else if (root instanceof Button) {
            ((Button) root).setTextSize(size);
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFontSizeToAll(group.getChildAt(i), size);
            }
        }
    }

    private void applyTextColorToAll(View root, int color) {
        if (root instanceof TextView) {
            ((TextView) root).setTextColor(color);
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextColorToAll(group.getChildAt(i), color);
            }
        }
    }

    private void setupButtonColorPicker(Button btn, int buttonIndex, SharedPreferences prefs) {
        String key = "button_color_" + buttonIndex;
        int savedColor = prefs.getInt(key, Color.LTGRAY);
        btn.setBackgroundColor(savedColor);

        btn.setOnClickListener(v -> {
            showColorPicker(color -> {
                prefs.edit().putInt(key, color).apply();
                btn.setBackgroundColor(color);

                // Apply instantly across fragments
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).applyGlobalButtonColor(buttonIndex, color);
                }
            });
        });
    }

    private void showColorPicker(ColorCallback callback) {
        final String[] colors = {
                "Red", "Green", "Blue", "Black", "Gray", "Cyan", "Magenta",
                "Yellow", "Orange", "Purple", "Brown", "Pink", "Light Gray", "Dark Gray"
        };

        final int[] values = {
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.GRAY, Color.CYAN, Color.MAGENTA,
                Color.YELLOW, 0xFFFFA500, // Orange
                0xFF800080, // Purple
                0xFFA52A2A, // Brown
                0xFFFFC0CB, // Pink
                0xFFD3D3D3, // Light Gray
                0xFFA9A9A9  // Dark Gray
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, colors) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);

                // Increase text size
                textView.setTextSize(40);

                // Add extra spacing (left, top, right, bottom)
                textView.setPadding(40, 24, 40, 24);

                // Set text color to match the color it represents
                textView.setTextColor(values[position]);

                return view;
            }
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose a Color")
                .setAdapter(adapter, (dialog, which) -> callback.onColorSelected(values[which]))
                .show();
    }


    interface ColorCallback {
        void onColorSelected(int color);
    }
}
