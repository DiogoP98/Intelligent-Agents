package group13;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;
import org.chocosolver.solver.variables.events.RealEventType;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.util.ESat;

public class MyPropagator extends Propagator<RealVar> {
    /**
     * The constant the sum cannot be smaller than
     */
    final int b;

    /**
     * Constructor of the specific sum propagator : x1 + x2 + ... + xn <= b
     *
     * @param x array of integer variables
     * @param b a constant
     */
    public MyPropagator(RealVar[] x, int b) {
        super(x, PropagatorPriority.LINEAR, false);
        this.b = b;
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        return RealEventType.combine(RealEventType.INSTANTIATE, RealEventType.INCLOW);
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        int sumLB = 0;
        for (RealVar var : vars) {
            sumLB += var.getLB();
        }
        int F = b - sumLB;
        if (F < 0) {
            fails();
        }
        for (RealVar var : vars) {
            double lb = var.getLB();
            double ub = var.getUB();
            if (ub - lb > F) {
                var.updateUpperBound(F + lb, this);
            }
        }
    }

    @Override
    public ESat isEntailed() {
        int sumUB = 0, sumLB = 0;
        for (RealVar var : vars) {
            sumLB += var.getLB();
            sumUB += var.getUB();
        }
        if (sumUB >= b) {
            return ESat.TRUE;
        }
        if (sumLB < b) {
            return ESat.FALSE;
        }
        return ESat.UNDEFINED;
    }
}
