package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

/* A leaf node */
@ToString @Getter @EqualsAndHashCode(callSuper = true)
public class ValueNode extends LeafNode<Double, Double> {
    public ValueNode(final Double value) {
        super(value);
    }
    @Override public Double evaluate(@NonNull final ProcessState state) {
        //TODO: refactor Valuation to use Double
        return value;
    }
}
