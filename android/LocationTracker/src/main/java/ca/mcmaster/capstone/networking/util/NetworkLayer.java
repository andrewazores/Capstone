package ca.mcmaster.capstone.networking.util;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public interface NetworkLayer {

    void sendTokenToPeer(HashableNsdServiceInfo destination, Token token);

    void receiveTokenInternal(Token token);

    Token receiveToken() throws InterruptedException;

    void receiveEventInternal(Event event);

    void receiveEventExternal(Event event);

    Event receiveEvent() throws InterruptedException;
}
