package ca.mcmaster.capstone.networking.util;

import ca.mcmaster.capstone.networking.structures.Message;

public interface MessageReceiver {
    void receiveMessage(Message message);
}
