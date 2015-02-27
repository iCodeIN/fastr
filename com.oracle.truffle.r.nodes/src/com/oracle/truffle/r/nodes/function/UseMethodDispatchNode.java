/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.function;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.variables.*;
import com.oracle.truffle.r.nodes.function.ArgumentMatcher.*;
import com.oracle.truffle.r.nodes.function.ArgumentMatcher.MatchPermutation;
import com.oracle.truffle.r.nodes.function.DispatchedCallNode.NoGenericMethodException;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * {@code UseMethod} is typically called like this:
 *
 * <pre>
 * f <- function(x, ...) UseMethod("f")
 * </pre>
 *
 * Locating the correct call depends on the class of {@code x}, and the search starts in the
 * enclosing (parent) environment of {@code f}, which, for packages, which is where most of these
 * definitions occur, will be the package {@code namepace} enviromnent.
 */
public abstract class UseMethodDispatchNode {

    public static DispatchNode createCached(String genericName, RStringVector type, ArgumentsSignature suppliedSignature) {
        return new UseMethodDispatchCachedNode(genericName, type, suppliedSignature);
    }

    public static DispatchNode createGeneric(String genericName, ArgumentsSignature suppliedSignature) {
        return new UseMethodDispatchGenericNode(genericName, suppliedSignature);
    }
}

final class UseMethodDispatchCachedNode extends S3DispatchCachedNode {

    @NodeInfo(cost = NodeCost.NONE)
    private static final class CheckReadsNode extends Node {
        @Children private final ReadVariableNode[] unsuccessfulReadsCallerFrame;
        @Children private final ReadVariableNode[] unsuccessfulReadsDefFrame;
        // if readsDefFrame != null, then this read will go to the def frame
        @Child private ReadVariableNode successfulRead;

        private final ArgumentsSignature signature;
        @CompilationFinal private final ArgumentsSignature[] varArgSignature;

        public final RFunction function;
        public final RStringVector clazz;
        public final String functionName;
        @CompilationFinal public long[] preparePermutation;
        public MatchPermutation permutation;

        public CheckReadsNode(ReadVariableNode[] unsuccessfulReadsCallerFrame, ReadVariableNode[] unsuccessfulReadsDefFrame, ReadVariableNode successfulRead, RFunction function, RStringVector clazz,
                        String functionName, ArgumentsSignature signature, ArgumentsSignature[] varArgSignature, long[] preparePermutation, MatchPermutation permutation) {
            this.unsuccessfulReadsCallerFrame = unsuccessfulReadsCallerFrame;
            this.unsuccessfulReadsDefFrame = unsuccessfulReadsDefFrame;
            this.successfulRead = successfulRead;
            this.function = function;
            this.clazz = clazz;
            this.functionName = functionName;
            this.signature = signature;
            this.varArgSignature = varArgSignature;
            this.preparePermutation = preparePermutation;
            this.permutation = permutation;
        }

        public boolean executeReads(Frame callerFrame, MaterializedFrame defFrame, ArgumentsSignature actualSignature, Object[] actualArguments) {
            if (actualSignature != signature) {
                return false;
            }
            if (!checkLastArgSignature(actualArguments)) {
                return false;
            }
            if (!executeReads(unsuccessfulReadsCallerFrame, callerFrame)) {
                return false;
            }
            Object actualFunction;
            if (unsuccessfulReadsDefFrame != null) {
                if (!executeReads(unsuccessfulReadsDefFrame, defFrame)) {
                    return false;
                }
                actualFunction = successfulRead.execute(null, defFrame);
            } else {
                actualFunction = successfulRead.execute(null, callerFrame);
            }
            return actualFunction == function;
        }

        @ExplodeLoop
        private static boolean executeReads(ReadVariableNode[] reads, Frame callerFrame) {
            for (ReadVariableNode read : reads) {
                if (read.execute(null, callerFrame) != null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return false;
                }
            }
            return true;
        }

        @ExplodeLoop
        private boolean checkLastArgSignature(Object[] arguments) {
            for (int i = 0; i < arguments.length; i++) {
                Object arg = arguments[i];
                if (arg instanceof RArgsValuesAndNames) {
                    if (varArgSignature == null || varArgSignature[i] != ((RArgsValuesAndNames) arg).getSignature()) {
                        return false;
                    }
                } else {
                    if (varArgSignature != null && varArgSignature[i] != null) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Child private CheckReadsNode cached;
    @Child private DirectCallNode call;

    public UseMethodDispatchCachedNode(String genericName, RStringVector type, ArgumentsSignature suppliedSignature) {
        super(genericName, type, suppliedSignature);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = extractArguments(frame);
        ArgumentsSignature signature = RArguments.getSignature(frame);
        MaterializedFrame genericDefFrame = RArguments.getEnclosingFrame(frame);
        MaterializedFrame callerFrame = getCallerFrame(frame);

        if (cached == null || !cached.executeReads(callerFrame, genericDefFrame, signature, arguments)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            specialize(frame, callerFrame, signature, arguments, true);
        }

        Object[] preparedArguments = prepareSuppliedArgument(cached.preparePermutation, arguments);

        EvaluatedArguments reorderedArgs = ArgumentMatcher.matchArgumentsEvaluated(cached.permutation, cached.function, preparedArguments);

        if (cached.function.isBuiltin()) {
            ArgumentMatcher.evaluatePromises(frame, promiseHelper, reorderedArgs);
        }

        Object[] argObject = prepareArguments(callerFrame, genericDefFrame, reorderedArgs, cached.function, cached.clazz, cached.functionName);

        return call.call(frame, argObject);
    }

    @ExplodeLoop
    private static Object[] prepareSuppliedArgument(long[] preparePermutation, Object[] arguments) {
        Object[] result = new Object[preparePermutation.length];
        for (int i = 0; i < result.length; i++) {
            long source = preparePermutation[i];
            if (source >= 0) {
                result[i] = arguments[(int) source];
            } else {
                source = -source;
                result[i] = ((RArgsValuesAndNames) arguments[(int) (source >> 32)]).getValues()[(int) source];
            }
        }
        return result;
    }

    private void specialize(Frame callerFrame, MaterializedFrame genericDefFrame, ArgumentsSignature signature, Object[] arguments, boolean throwsRError) {
        CompilerAsserts.neverPartOfCompilation();
        // look for a match in the caller frame hierarchy
        TargetLookupResult result = findTargetFunctionLookup(callerFrame, type, genericName);
        ReadVariableNode[] unsuccessfulReadsCaller = result.unsuccessfulReads;
        ReadVariableNode[] unsuccessfulReadsDef = null;
        if (result.successfulRead == null) {
            if (genericDefFrame != null) {
                // look for a match in the generic def frame hierarchy
                result = findTargetFunctionLookup(genericDefFrame, type, genericName);
                unsuccessfulReadsDef = result.unsuccessfulReads;
            }
            if (result.successfulRead == null) {
                if (throwsRError) {
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNKNOWN_FUNCTION_USE_METHOD, genericName, type);
                } else {
                    throw new NoGenericMethodException();
                }
            }
        }

        int argCount = arguments.length;
        int argListSize = argCount;

        ArgumentsSignature[] varArgSignatures = null;
        for (int i = 0; i < arguments.length; i++) {
            Object arg = arguments[i];
            if (arg instanceof RArgsValuesAndNames) {
                if (varArgSignatures == null) {
                    varArgSignatures = new ArgumentsSignature[arguments.length];
                }
                varArgSignatures[i] = ((RArgsValuesAndNames) arg).getSignature();
                argListSize += ((RArgsValuesAndNames) arg).length() - 1;
            }
        }
        long[] preparePermutation;
        ArgumentsSignature resultSignature;
        if (varArgSignatures != null) {
            preparePermutation = new long[argListSize];
            String[] argNames = new String[argListSize];
            int index = 0;
            for (int fi = 0; fi < argCount; ++fi) {
                Object arg = arguments[fi];
                if (arg instanceof RArgsValuesAndNames) {
                    RArgsValuesAndNames varArgs = (RArgsValuesAndNames) arg;
                    ArgumentsSignature varArgSignature = varArgs.getSignature();
                    for (int i = 0; i < varArgs.length(); i++) {
                        argNames[index] = varArgSignature.getName(i);
                        preparePermutation[index++] = -((((long) fi) << 32) + i);
                    }
                } else {
                    argNames[index] = signature.getName(fi);
                    preparePermutation[index++] = fi;
                }
            }
            resultSignature = ArgumentsSignature.get(argNames);
        } else {
            preparePermutation = new long[argCount];
            for (int i = 0; i < argCount; i++) {
                preparePermutation[i] = i;
            }
            resultSignature = signature;
        }

        assert signature != null;
        MatchPermutation permutation = ArgumentMatcher.matchArguments(result.targetFunction, resultSignature, getEncapsulatingSourceSection(), false);

        CheckReadsNode newCheckedReads = new CheckReadsNode(unsuccessfulReadsCaller, unsuccessfulReadsDef, result.successfulRead, result.targetFunction, result.clazz, result.targetFunctionName,
                        signature, varArgSignatures, preparePermutation, permutation);
        DirectCallNode newCall = Truffle.getRuntime().createDirectCallNode(result.targetFunction.getTarget());
        if (call == null) {
            cached = insert(newCheckedReads);
            call = insert(newCall);
        } else {
            RError.performanceWarning("re-specializing UseMethodDispatchCachedNode");
            cached.replace(newCheckedReads);
            call.replace(newCall);
        }
    }

    @Override
    public Object executeGeneric(VirtualFrame frame, RStringVector aType) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public Object executeInternal(VirtualFrame frame, Object[] arguments) throws NoGenericMethodException {
        ArgumentsSignature signature = suppliedSignature;
        if (cached == null || !cached.executeReads(frame, null, signature, arguments)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            specialize(frame, null, signature, arguments, false);
        }
        EvaluatedArguments reorderedArgs = reorderArguments(arguments, cached.function, suppliedSignature, getEncapsulatingSourceSection());
        if (cached.function.isBuiltin()) {
            ArgumentMatcher.evaluatePromises(frame, promiseHelper, reorderedArgs);
        }
        Object[] argObject = prepareArguments(frame, null, reorderedArgs, cached.function, cached.clazz, cached.functionName);
        return call.call(frame, argObject);
    }

    @Override
    public Object executeInternalGeneric(VirtualFrame frame, RStringVector aType, Object[] args) throws NoGenericMethodException {
        throw RInternalError.shouldNotReachHere();
    }

    private static final class TargetLookupResult {
        private final ReadVariableNode[] unsuccessfulReads;
        private final ReadVariableNode successfulRead;
        private final RFunction targetFunction;
        private final String targetFunctionName;
        private final RStringVector clazz;

        public TargetLookupResult(ReadVariableNode[] unsuccessfulReads, ReadVariableNode successfulRead, RFunction targetFunction, String targetFunctionName, RStringVector clazz) {
            this.unsuccessfulReads = unsuccessfulReads;
            this.successfulRead = successfulRead;
            this.targetFunction = targetFunction;
            this.targetFunctionName = targetFunctionName;
            this.clazz = clazz;
        }
    }

    private static TargetLookupResult findTargetFunctionLookup(Frame callerFrame, RStringVector type, String genericName) {
        CompilerAsserts.neverPartOfCompilation();
        RFunction targetFunction = null;
        String targetFunctionName = null;
        RStringVector clazz = null;
        ArrayList<ReadVariableNode> unsuccessfulReads = new ArrayList<>();

        for (int i = 0; i <= type.getLength(); ++i) {
            String clazzName = i == type.getLength() ? RRuntime.DEFAULT : type.getDataAt(i);
            String functionName = genericName + RRuntime.RDOT + clazzName;
            ReadVariableNode rvn = ReadVariableNode.createFunctionLookup(functionName, false);
            Object func = rvn.execute(null, callerFrame);
            if (func != null) {
                assert func instanceof RFunction;
                targetFunctionName = functionName;
                targetFunction = (RFunction) func;

                if (i == 0) {
                    clazz = type.copyResized(type.getLength(), false);
                } else if (i == type.getLength()) {
                    clazz = null;
                } else {
                    clazz = RDataFactory.createStringVector(Arrays.copyOfRange(type.getDataWithoutCopying(), i, type.getLength()), true);
                    clazz.setAttr(RRuntime.PREVIOUS_ATTR_KEY, type.copyResized(type.getLength(), false));
                }
                return new TargetLookupResult(unsuccessfulReads.toArray(new ReadVariableNode[unsuccessfulReads.size()]), rvn, targetFunction, targetFunctionName, clazz);
            } else {
                unsuccessfulReads.add(rvn);
            }
        }
        return new TargetLookupResult(unsuccessfulReads.toArray(new ReadVariableNode[unsuccessfulReads.size()]), null, null, null, null);
    }
}

final class UseMethodDispatchGenericNode extends S3DispatchGenericNode {

    public UseMethodDispatchGenericNode(String genericName, ArgumentsSignature suppliedSignature) {
        super(genericName, suppliedSignature);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public Object executeGeneric(VirtualFrame frame, RStringVector type) {
        Frame callerFrame = getCallerFrame(frame);
        findTargetFunction(RArguments.getEnclosingFrame(frame), type, true);
        return executeHelper(frame, callerFrame, extractArguments(frame), RArguments.getSignature(frame), getSourceSection());
    }

    @Override
    public Object executeInternal(VirtualFrame frame, Object[] args) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public Object executeInternalGeneric(VirtualFrame frame, RStringVector type, Object[] args) {
        // TBD getEnclosing?
        findTargetFunction(frame, type, false);
        return executeHelper(frame, frame, args, suppliedSignature, getEncapsulatingSourceSection());
    }

    private Object executeHelper(VirtualFrame frame, Frame callerFrame, Object[] args, ArgumentsSignature paramSignature, SourceSection errorSourceSection) {
        EvaluatedArguments reorderedArgs = reorderArguments(args, targetFunction, paramSignature, errorSourceSection);
        if (targetFunction.isBuiltin()) {
            ArgumentMatcher.evaluatePromises(frame, promiseHelper, reorderedArgs);
        }
        Object[] argObject = prepareArguments(callerFrame, RArguments.getEnclosingFrame(frame), reorderedArgs, targetFunction, klass, targetFunctionName);
        return indirectCallNode.call(frame, targetFunction.getTarget(), argObject);
    }

    private void findTargetFunction(Frame callerFrame, RStringVector type, boolean throwsRError) {
        findTargetFunctionLookup(callerFrame, type);
        if (targetFunction == null) {
            errorProfile.enter();
            if (throwsRError) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNKNOWN_FUNCTION_USE_METHOD, genericName, RRuntime.toString(type));
            } else {
                throw new NoGenericMethodException();
            }
        }
    }

    @TruffleBoundary
    private void findTargetFunctionLookup(Frame callerFrame, RStringVector type) {
        for (int i = 0; i < type.getLength(); ++i) {
            findFunction(genericName, type.getDataAt(i), callerFrame);
            if (targetFunction != null) {
                RStringVector classVec = null;
                if (i > 0) {
                    isFirst = false;
                    classVec = RDataFactory.createStringVector(Arrays.copyOfRange(type.getDataWithoutCopying(), i, type.getLength()), true);
                    classVec.setAttr(RRuntime.PREVIOUS_ATTR_KEY, type.copyResized(type.getLength(), false));
                } else {
                    isFirst = true;
                    classVec = type.copyResized(type.getLength(), false);
                }
                klass = classVec;
                break;
            }
        }
        if (targetFunction != null) {
            return;
        }
        findFunction(genericName, RRuntime.DEFAULT, callerFrame);
    }
}
