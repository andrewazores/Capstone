package ca.mcmaster.capstone.networking.structures;

import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import lombok.Value;

@Value public class Message {
    NetworkPeerIdentifier sender;
    VectorClock vectorClock;
    String message;
}
