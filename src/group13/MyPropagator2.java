package group13;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;
import org.chocosolver.solver.variables.events.RealEventType;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.util.ESat;

public class MyPropagator2 extends Propagator<RealVar> {
    /**
     * The constant the sum cannot be smaller than
     */
    final double b;
    final double [] weights;

    /**
     * Constructor of the specific sum propagator : x1*w1 + x2*w2 + ... + xn*wn = b
     *
     * @param x array of integer variables
     * @param b a constant
     */
    public MyPropagator2(RealVar[] x, double [] weights, double b) {
        super(x, PropagatorPriority.LINEAR, false);
        this.b = b;
        this.weights = new double[weights.length];

        for(int i = 0; i < weights.length; i++) {
            this.weights[i] = weights[i];
        }
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        return RealEventType.BOUND.getMask();
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        double sumUB = 0, sumLB = 0;
        int indx = 0;
        for (RealVar var : vars) {
            sumUB += var.getUB() * this.weights[indx];
            indx++;
        }
        double F = b - sumUB;
        if (F != 0) {
            fails();
        }

        indx = 0;
        for (RealVar var : vars) {
            sumLB += var.getLB() * this.weights[indx];
            indx++;
        }

        F = b - sumLB;
        if (F != 0) {
            fails();
        }

        for (RealVar var : vars) {
            double lb = var.getLB();
            double ub = var.getUB();
            if (ub - lb != F) {
                var.updateLowerBound(ub, this);
            }
        }
    }

    @Override
    public ESat isEntailed() {
        double sumUB = 0, sumLB = 0;
        int indx = 0;
        for (RealVar var : vars) {
            sumLB += var.getLB() * this.weights[indx];
            sumUB += var.getUB() * this.weights[indx];
            indx++;
        }
        if (sumLB == b) {
            return ESat.TRUE;
        }
        if (sumUB != b) {
            return ESat.FALSE;
        }
        return ESat.UNDEFINED;
    }
}