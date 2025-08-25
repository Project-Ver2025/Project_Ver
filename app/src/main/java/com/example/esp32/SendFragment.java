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
    private Button sendButton, saveButton;
    private ListView savedListView;
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
        saveButton = view.findViewById(R.id.saveTextButton);
        savedListView = view.findViewById(R.id.savedMessagesList);

        savedMessages = new ArrayList<>();
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, savedMessages);
        savedListView.setAdapter(adapter);

        sendButton.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                sendCommand.accept(text);
                editText.setText("");
            } else {
                Toast.makeText(requireContext(), "Please enter text", Toast.LENGTH_SHORT).show();
            }
        });

        saveButton.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                if (!savedMessages.contains(text)) {
                    savedMessages.add(text);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Already saved", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Nothing to save", Toast.LENGTH_SHORT).show();
            }
        });

        savedListView.setOnItemClickListener((parent, view1, position, id) -> {
            String message = savedMessages.get(position);
            sendCommand.accept(message);
        });

        savedListView.setOnItemLongClickListener((parent, view12, position, id) -> {
            String itemToRemove = savedMessages.get(position);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Message")
                    .setMessage("Delete this saved message?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        savedMessages.remove(position);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        return view;
    }
}
