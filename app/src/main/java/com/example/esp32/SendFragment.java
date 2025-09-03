package com.example.esp32;

import android.app.AlertDialog;
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

                // Trigger loading sound through MainActivity
                if (getActivity() instanceof MainActivity) {
                    MainActivity main = (MainActivity) getActivity();
                    if (main.isServiceBound()) {
                        main.getBluetoothService().startLoadingSoundFromCommand();
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Please enter text", Toast.LENGTH_SHORT).show();
            }
        });


        return view;
    }
}
