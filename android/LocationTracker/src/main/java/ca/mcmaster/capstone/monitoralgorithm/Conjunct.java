package ca.mcmaster.capstone.monitoralgorithm;

import ca.mcmaster.capstone.monitoralgorithm.tree.BooleanExpressionTree;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    @NonNull @Getter private final HashableNsdServiceInfo ownerProcess;
    @NonNull private final BooleanExpressionTree expression;

    public Conjunct(@NonNull final HashableNsdServiceInfo ownerProcess, @NonNull final String expression) {
        this.ownerProcess = ownerProcess;
        this.expression = new BooleanExpressionTree(expression);
    }

    public Evaluation evaluate(@NonNull final ProcessState state) {
        return expression.evaluate(state);
    }
}
