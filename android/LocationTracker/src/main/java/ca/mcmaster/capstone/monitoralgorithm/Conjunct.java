package ca.mcmaster.capstone.monitoralgorithm;

import ca.mcmaster.capstone.monitoralgorithm.tree.BooleanExpressionTree;
import ca.mcmaster.capstone.monitoralgorithm.tree.parser.BooleanExpressionParser;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    @NonNull @Getter private final NetworkPeerIdentifier ownerProcess;
    @NonNull private final BooleanExpressionTree expression;

    public Conjunct(@NonNull final NetworkPeerIdentifier ownerProcess, @NonNull final String expression) {
        this.ownerProcess = ownerProcess;
        this.expression = BooleanExpressionParser.INSTANCE.parse(expression);
    }

    public Evaluation evaluate(@NonNull final ProcessState state) {
        if (!ownerProcess.equals(state.getId())) {
            return Evaluation.NONE;
        }
        return expression.evaluate(state);
    }
}
