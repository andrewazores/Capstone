package ca.mcmaster.capstone.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.NonNull;

public class CollectionUtils {
    public static <T> Collection<T> filter(@NonNull final Collection<? extends T> collection, @NonNull final Predicate<T> predicate) {
        final List<T> results = new ArrayList<>();
        for (final T t : collection) {
            if (predicate.apply(t)) {
                results.add(t);
            }
        }
        return results;
    }
}
