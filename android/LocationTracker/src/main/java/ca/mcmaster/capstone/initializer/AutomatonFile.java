package ca.mcmaster.capstone.initializer;

import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

@Value
public class AutomatonFile {
    @NonNull @Getter Set<String> state_names = new HashSet<>();
    @NonNull @Getter Set<Transition> transitions = new HashSet<>();

    @Value
    public static class Transition {
        @NonNull String source;
        @NonNull String destination;
        @NonNull String predicate;
    }
}
