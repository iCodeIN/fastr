package com.oracle.truffle.r.ffi.impl.nodes;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;

@GenerateUncached
public abstract class IntegerNoNANode extends FFIUpCallNode.Arg1 {
    public static IntegerNoNANode create() {
        return IntegerNoNANodeGen.create();
    }

    @Specialization(limit = "getGenericDataLibraryCacheSize()")
    public Object doContainer(RAbstractContainer container,
                              @CachedLibrary("container.getData()") VectorDataLibrary dataLibrary) {
        if (dataLibrary.isComplete(container.getData())) {
            return RRuntime.LOGICAL_TRUE;
        } else {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @Specialization(replaces = "doContainer")
    public Object doOther(@SuppressWarnings("unused") RBaseObject rObject) {
        return RRuntime.LOGICAL_FALSE;
    }
}
