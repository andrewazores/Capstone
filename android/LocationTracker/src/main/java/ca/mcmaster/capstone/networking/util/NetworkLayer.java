package ca.mcmaster.capstone.networking.util;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public interface NetworkLayer {

    /**
     * Broadcasts a Token to a specific peer
     * @param destination
     * @param token
     */
    void sendTokenToPeer(HashableNsdServiceInfo destination, Token token);

    /**
     * Called by the Server when a Token is received over the network
     * @param token
     */
    void receiveTokenInternal(Token token);

    /**
     * Called by the local monitoring process when it wishes to poll for tokens.
     * This is a blocking call - if no tokens are available, the calling thread
     * will wait until there is one.
     * @return the first token in the queue
     * @throws InterruptedException
     */
    Token receiveToken() throws InterruptedException;

    /**
     * Called by the local process when an event occurs which is to be broadcast to peers
     * @param event
     */
    void receiveEventInternal(Event event);

    /**
     * Called by the Server when an Event is received over the network
     * @param event
     */
    void receiveEventExternal(Event event);

    /**
     * Called by the local monitoring process when it wishes to poll for events.
     * This is a blocking call - if no events are available, the calling thread
     * will wait until there is one.
     * @return the first event in the queue
     * @throws InterruptedException
     */
    Event receiveEvent() throws InterruptedException;
}
