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

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;


public class SendFragment extends Fragment {

    private EditText editText;
    private Button sendButton;
    private ArrayList<String> savedMessages;
    private ArrayAdapter<String> adapter;
    private final Consumer<String> sendCommand;

    public SendFragment(Consumer<String> sendCommand) {
        this.sendCommand = sendCommand;
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_send, container, false);

        editText = view.findViewById(R.id.inputText);
        sendButton = view.findViewById(R.id.sendTextButton);

        savedMessages = new ArrayList<>();
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, savedMessages);

        sendButton.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                sendCommand.accept(text);
                editText.setText("");

                if (getActivity() instanceof MainActivity) {
                    MainActivity main = (MainActivity) getActivity();
                    if (main.isServiceBound() && main.getBluetoothService() != null) {
                        if (main.getBluetoothService().checkAndWarnConnection()) {
                            main.getBluetoothService().startLoadingSoundFromCommand();
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Please enter text", Toast.LENGTH_SHORT).show();
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
            int btnColor = prefs.getInt("button_color_1", android.graphics.Color.LTGRAY);
            main.applyGlobalButtonColor(1, btnColor);
        }
    }

}
