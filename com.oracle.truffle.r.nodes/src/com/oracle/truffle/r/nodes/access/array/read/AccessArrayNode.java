/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.access.array.read;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.access.array.*;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCast.OperatorConverterNode;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCastFactory.OperatorConverterNodeFactory;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@NodeChildren({@NodeChild(value = "vector", type = RNode.class), @NodeChild(value = "recursionLevel", type = RNode.class),
                @NodeChild(value = "positions", type = PositionsArrayNode.class, executeWith = {"vector"}), @NodeChild(value = "dropDim", type = RNode.class)})
public abstract class AccessArrayNode extends RNode {

    private final boolean isSubset;

    private final NACheck elementNACheck = NACheck.create();
    private final NACheck posNACheck = NACheck.create();
    private final NACheck namesNACheck = NACheck.create();

    @Child private AccessArrayNode accessRecursive;
    @Child private CastToVectorNode castVector;
    @Child private ArrayPositionCast castPosition;
    @Child private OperatorConverterNode operatorConverter;
    @Child private GetMultiDimDataNode getMultiDimData;
    @Child private GetNamesNode getNamesNode;
    @Child private GetDimNamesNode getDimNamesNode;

    protected abstract RNode getVector();

    public abstract Object executeAccess(VirtualFrame frame, Object vector, int recLevel, Object operand, RAbstractLogicalVector dropDim);

    public abstract Object executeAccess(VirtualFrame frame, Object vector, int recLevel, int operand, RAbstractLogicalVector dropDim);

    public AccessArrayNode(boolean isSubset) {
        this.isSubset = isSubset;
    }

    public AccessArrayNode(AccessArrayNode other) {
        this.isSubset = other.isSubset;
    }

    private Object accessRecursive(VirtualFrame frame, Object vector, Object operand, int recLevel, RAbstractLogicalVector dropDim) {
        if (accessRecursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            accessRecursive = insert(AccessArrayNodeFactory.create(this.isSubset, null, null, null, null));
        }
        return executeAccess(frame, vector, recLevel, operand, dropDim);
    }

    private Object castVector(VirtualFrame frame, Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeFactory.create(null, false, false, false, true));
        }
        return castVector.executeObject(frame, value);
    }

    private Object castPosition(VirtualFrame frame, Object vector, Object operand) {
        if (castPosition == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castPosition = insert(ArrayPositionCastFactory.create(0, 1, false, false, null, null, null));
        }
        return castPosition.executeArg(frame, operand, vector, operand);
    }

    private void initOperatorConvert() {
        if (operatorConverter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            operatorConverter = insert(OperatorConverterNodeFactory.create(0, 1, false, false, null, null));
        }
    }

    private Object convertOperand(VirtualFrame frame, Object vector, int operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(frame, vector, operand);
    }

    private Object convertOperand(VirtualFrame frame, Object vector, String operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(frame, vector, operand);
    }

    private Object getMultiDimData(VirtualFrame frame, Object data, RAbstractVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                    int accDstDimensions, NACheck posCheck, NACheck elementCheck) {
        if (getMultiDimData == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMultiDimData = insert(GetMultiDimDataNodeFactory.create(posCheck, elementCheck, null, null, null, null, null, null, null, null));
        }
        return getMultiDimData.executeMultiDimDataGet(frame, data, vector, positions, currentDimLevel, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
    }

    private RStringVector getNames(VirtualFrame frame, RAbstractVector vector, Object[] positions, int currentDimLevel, NACheck namesCheck) {
        if (getNamesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getNamesNode = insert(GetNamesNodeFactory.create(namesCheck, null, null, null, null, null));
        }
        return (RStringVector) getNamesNode.executeNamesGet(frame, vector, positions, currentDimLevel, RRuntime.LOGICAL_TRUE, RNull.instance);
    }

    private RStringVector getDimNames(VirtualFrame frame, RList dstDimNames, RAbstractVector vector, Object[] positions, int currentSrcDimLevel, int currentDstDimLevel, NACheck namesCheck) {
        if (getDimNamesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getDimNamesNode = insert(GetDimNamesNodeFactory.create(namesCheck, null, null, null, null, null));
        }
        return (RStringVector) getDimNamesNode.executeDimNamesGet(frame, dstDimNames, vector, positions, currentSrcDimLevel, currentDstDimLevel);
    }

    public static RIntVector popHead(RIntVector p, NACheck posNACheck) {
        int[] data = new int[p.getLength() - 1];
        posNACheck.enable(p);
        for (int i = 0; i < data.length; i++) {
            data[i] = p.getDataAt(i + 1);
            posNACheck.check(data[i]);
        }
        return RDataFactory.createIntVector(data, posNACheck.neverSeenNA());
    }

    public static RStringVector popHead(RStringVector p, NACheck posNACheck) {
        String[] data = new String[p.getLength() - 1];
        posNACheck.enable(p);
        for (int i = 0; i < data.length; i++) {
            data[i] = p.getDataAt(i + 1);
            posNACheck.check(data[i]);
        }
        return RDataFactory.createStringVector(data, posNACheck.neverSeenNA());
    }

    protected static RStringVector getNamesVector(Object srcNamesObject, RIntVector p, int resLength, NACheck namesNACheck) {
        if (srcNamesObject == RNull.instance) {
            return null;
        }
        RStringVector srcNames = (RStringVector) srcNamesObject;
        String[] namesData = new String[resLength];
        namesNACheck.enable(!srcNames.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (namesNACheck.check(position)) {
                namesData[i] = RRuntime.STRING_NA;
            } else {
                namesData[i] = srcNames.getDataAt(position - 1);
                namesNACheck.check(namesData[i]);
            }
        }
        return RDataFactory.createStringVector(namesData, namesNACheck.neverSeenNA());
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"inRecursion", "isFirstPositionPositive"})
    protected RNull accessNullInRecursionPosPositive(RNull vector, int recLevel, RAbstractIntVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"inRecursion", "!isFirstPositionPositive"})
    protected RNull accessNullInRecursion(RNull vector, int recLevel, RAbstractIntVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull access(RNull vector, int recLevel, Object positions, RAbstractLogicalVector dropDim) {
        return RNull.instance;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"inRecursion", "isFirstPositionOne"})
    protected RNull accessFunctionInRecursionPosOne(RFunction vector, int recLevel, RAbstractIntVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_TYPE_LENGTH, "closure", 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"inRecursion", "isFirstPositionPositive", "!isFirstPositionOne"})
    protected RNull accessFunctionInRecursionPosPositive(RFunction vector, int recLevel, RAbstractIntVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"inRecursion", "!isFirstPositionPositive"})
    protected RNull accessFunctionInRecursion(RFunction vector, int recLevel, RAbstractIntVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "inRecursion")
    protected RNull accessFunctionInRecursionString(RFunction vector, int recLevel, RAbstractStringVector positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull accessFunction(RFunction vector, int recLevel, Object position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.OBJECT_NOT_SUBSETTABLE, "closure");
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull access(RAbstractContainer container, int recLevel, RNull positions, RAbstractLogicalVector dropDim) {
        // this is a special case (see ArrayPositionCast) - RNull can only appear to represent the
        // x[NA] case which has to return null and not a null vector
        return RNull.instance;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object access(RAbstractContainer container, int recLevel, RMissing positions, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "symbol");
        } else {
            return container;
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "wrongDimensions")
    protected Object access(RAbstractContainer container, int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INCORRECT_DIMENSIONS);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "!isSubset"})
    protected RIntVector accessNA(RAbstractContainer container, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
    }

    private RStringVector getName(RAbstractVector vector, int position) {
        RStringVector srcNames = (RStringVector) vector.getNames();
        namesNACheck.enable(srcNames);
        String name = srcNames.getDataAt(position - 1);
        namesNACheck.check(name);
        return RDataFactory.createStringVector(new String[]{name}, namesNACheck.neverSeenNA());
    }

    private static class DimsAndResultLength {
        public final int[] dimensions;
        public final int resLength;

        public DimsAndResultLength(int[] dimensions, int resLength) {
            this.dimensions = dimensions;
            this.resLength = resLength;
        }
    }

    private DimsAndResultLength getDimsAndResultLength(Object[] positions, byte dropDim) {
        int dimLength = 0;
        int resLength = 1;
        int multi = 0; // how many times # positions > 1 ?
        int zero = 0; // how many times a position is 0
        int single = 0; // how many times # positions == 1
        for (int i = 0; i < positions.length; i++) {
            RIntVector p = (RIntVector) positions[i];
            int pLength = p.getLength();
            if (pLength == 1 && p.getDataAt(0) != 0) {
                if (!isSubset || dropDim == RRuntime.LOGICAL_TRUE) {
                    // always drop dimensions with subscript
                    continue;
                } else {
                    single++;
                }
            } else if (pLength > 1) {
                multi++;
                resLength *= pLength;
            } else {
                resLength = 0;
                zero++;
            }
            dimLength++;
        }
        // create dimensions array
        int[] dimensions = null;

        if (dimLength > 0 && ((zero > 1 || multi > 1) || (zero > 0 && multi > 0) || single > 0)) {
            dimensions = new int[dimLength];
            int ind = 0;
            for (int i = 0; i < positions.length; i++) {
                RIntVector p = (RIntVector) positions[i];
                int pLength = p.getLength();
                if (pLength == 1 && p.getDataAt(0) != 0) {
                    if (!isSubset || dropDim == RRuntime.LOGICAL_TRUE) {
                        // always drop dimensions with subscript
                        continue;
                    } else {
                        dimensions[ind++] = pLength;
                    }
                } else if (pLength > 1) {
                    dimensions[ind++] = pLength;
                } else {
                    dimensions[ind++] = 0;
                }
            }
        }
        return new DimsAndResultLength(dimensions, resLength);
    }

    private int getSrcArrayBase(int pos, int accSrcDimensions) {
        if (elementNACheck.check(pos)) {
            return -1; // fill with NAs at the lower levels
        } else {
            return accSrcDimensions * (pos - 1);
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RList access(VirtualFrame frame, RList vector, int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        Object[] data;
        if (resLength == 0) {
            data = new Object[0];
        } else {
            data = new Object[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createList(data, dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createList(data, dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RList resultVector = RDataFactory.createList(data, dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isSubset")
    protected RList accessSubset(RList vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        Object[] data = new Object[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = RNull.instance;
            } else {
                data[i] = vector.getDataAt(position - 1);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createList(data, names);
    }

    // lists require special handling for one-dimensional "subscript", that is [[]], accesses due to
    // support for recursive access

    public static int getPositionInRecursion(RList vector, String position, int recLevel, SourceSection sourceSection) {
        if (vector.getNames() != RNull.instance) {
            RStringVector names = (RStringVector) vector.getNames();
            for (int i = 0; i < names.getLength(); i++) {
                if (position.equals(names.getDataAt(i))) {
                    return i + 1;
                }
            }
        }
        throw RError.error(sourceSection, RError.Message.NO_SUCH_INDEX, recLevel + 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!hasNames")
    protected RList accessStringNoNames(RList vector, int recLevel, RStringVector p, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.NO_SUCH_INDEX, 1);
    }

    @Specialization(guards = {"hasNames", "!isSubset", "twoPosition"})
    protected Object accessStringTwoPosRec(VirtualFrame frame, RList vector, int recLevel, RStringVector p, RAbstractLogicalVector dropDim) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, getEncapsulatingSourceSection());
        Object newVector = castVector(frame, vector.getDataAt(position - 1));
        Object newPosition = castPosition(frame, newVector, convertOperand(frame, newVector, p.getDataAt(1)));
        return accessRecursive(frame, newVector, newPosition, recLevel + 1, dropDim);
    }

    @Specialization(guards = {"hasNames", "!isSubset", "!twoPosition"})
    protected Object accessString(VirtualFrame frame, RList vector, int recLevel, RStringVector p, RAbstractLogicalVector dropDim) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, getEncapsulatingSourceSection());
        RStringVector newP = popHead(p, posNACheck);
        return accessRecursive(frame, vector.getDataAt(position - 1), newP, recLevel + 1, dropDim);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isSubset", "onePosition", "!inRecursion"})
    protected Object accessOnePos(RList vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int position = p.getDataAt(0);
        if (RRuntime.isNA(position)) {
            return RNull.instance;
        } else if (position <= 0) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else if (position > vector.getLength()) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
        }
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isSubset", "noPosition"})
    protected Object accessNoPos(RList vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    public static int getPositionFromNegative(RList vector, int position, SourceSection sourceSection) {
        if (vector.getLength() == 1 && position == -1) {
            // x<-c(1); x[-1] <==> x[0]
            throw RError.error(sourceSection, RError.Message.SELECT_LESS_1);
        } else if (vector.getLength() > 1 && position < -vector.getLength()) {
            // x<-c(1,2); x[-3] <==> x[1,2]
            throw RError.error(sourceSection, RError.Message.SELECT_MORE_1);
        } else if (vector.getLength() > 2 && position > -vector.getLength()) {
            // x<-c(1,2,3); x[-2] <==> x[1,3]
            throw RError.error(sourceSection, RError.Message.SELECT_MORE_1);
        }
        assert (vector.getLength() == 2);
        return position == -1 ? 2 : 1;
    }

    private int getPositionInRecursion(RList vector, int position, int recLevel) {
        if (RRuntime.isNA(position) || position > vector.getLength()) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.NO_SUCH_INDEX, recLevel + 1);
        } else if (position < 0) {
            return getPositionFromNegative(vector, position, getEncapsulatingSourceSection());
        } else if (position == 0) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        return position;
    }

    @Specialization(guards = {"!isSubset", "onePosition", "inRecursion"})
    protected Object accessSubscript(RList vector, int recLevel, RIntVector p, @SuppressWarnings("unused") RAbstractLogicalVector dropDim) {
        int position = p.getDataAt(0);
        position = getPositionInRecursion(vector, position, recLevel);
        return vector.getDataAt(position - 1);
    }

    @Specialization(guards = {"!isSubset", "twoPosition"})
    protected Object accessTwoPosRec(VirtualFrame frame, RList vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int position = p.getDataAt(0);
        position = getPositionInRecursion(vector, position, recLevel);
        Object newVector = castVector(frame, vector.getDataAt(position - 1));
        Object newPosition = castPosition(frame, newVector, convertOperand(frame, newVector, p.getDataAt(1)));
        return accessRecursive(frame, newVector, newPosition, recLevel + 1, dropDim);
    }

    @Specialization(guards = {"!isSubset", "multiPos"})
    protected Object access(VirtualFrame frame, RList vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int position = p.getDataAt(0);
        position = getPositionInRecursion(vector, position, recLevel);
        RIntVector newP = popHead(p, posNACheck);
        return accessRecursive(frame, vector.getDataAt(position - 1), newP, recLevel + 1, dropDim);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RList accessNA(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createList(new Object[]{RNull.instance});
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createList(new Object[]{RNull.instance}, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionNA", "isPositionNegative", "!outOfBoundsNegative"})
    protected RList accessNegativeInBounds(RAbstractContainer container, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionNA", "isPositionNegative", "outOfBoundsNegative", "oneElemVector"})
    protected RList accessNegativeOutOfBoundsOneElemVector(RAbstractContainer container, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionNA", "isPositionNegative", "outOfBoundsNegative", "!oneElemVector"})
    protected RList accessNegativeOutOfBounds(RAbstractContainer container, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RList accessNamesSubset(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        Object val = vector.getDataAt(position - 1);
        return RDataFactory.createList(new Object[]{val}, getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "!isSubset", "!isPositionNA", "!isPositionNegative", "!outOfBounds"})
    protected Object accessNames(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RList accessSubset(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return RDataFactory.createList(new Object[]{vector.getDataAt(position - 1)});
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!hasNames", "!isSubset", "!isPositionNA", "!isPositionNegative", "!outOfBounds"})
    protected Object access(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isSubset", "outOfBounds"})
    protected Object accessOutOfBounds(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_BOUNDS);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RList accessPosZero(RList vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createList();
        } else {
            return RDataFactory.createList(new Object[0], RDataFactory.createEmptyStringVector());
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isSubset", "inRecursion", "multiPos", "!isVectorList"})
    protected Object accessRecFailedRec(RAbstractContainer container, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.RECURSIVE_INDEXING_FAILED, recLevel + 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isSubset", "!inRecursion", "multiPos", "!isVectorList"})
    protected Object accessRecFailed(RAbstractContainer container, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RAbstractIntVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int[] data;
        if (resLength == 0) {
            data = new int[0];
        } else {
            data = new int[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!vector.isComplete() || !p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createIntVector(data, elementNACheck.neverSeenNA(), dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createIntVector(data, elementNACheck.neverSeenNA(), dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RIntVector resultVector = RDataFactory.createIntVector(data, elementNACheck.neverSeenNA(), dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RIntVector access(RAbstractIntVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        int[] data = new int[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = RRuntime.INT_NA;
            } else {
                data[i] = vector.getDataAt(position - 1);
                elementNACheck.check(data[i]);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createIntVector(data, elementNACheck.neverSeenNA(), names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RIntVector accessNA(RAbstractIntVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createIntVector(new int[]{RRuntime.INT_NA}, RDataFactory.INCOMPLETE_VECTOR);
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createIntVector(new int[]{RRuntime.INT_NA}, RDataFactory.INCOMPLETE_VECTOR, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RIntVector accessNames(RAbstractIntVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        int val = vector.getDataAt(position - 1);
        elementNACheck.check(val);
        return RDataFactory.createIntVector(new int[]{val}, elementNACheck.neverSeenNA(), getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected int access(RAbstractIntVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RIntVector accessPosZero(RAbstractIntVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyIntVector();
        } else {
            return RDataFactory.createIntVector(new int[0], RDataFactory.COMPLETE_VECTOR, RDataFactory.createEmptyStringVector());
        }
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RAbstractDoubleVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        double[] data;
        if (resLength == 0) {
            data = new double[0];
        } else {
            data = new double[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!vector.isComplete() || !p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createDoubleVector(data, elementNACheck.neverSeenNA(), dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createDoubleVector(data, elementNACheck.neverSeenNA(), dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RDoubleVector resultVector = RDataFactory.createDoubleVector(data, elementNACheck.neverSeenNA(), dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RDoubleVector access(RAbstractDoubleVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        double[] data = new double[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = RRuntime.DOUBLE_NA;
            } else {
                data[i] = vector.getDataAt(position - 1);
                elementNACheck.check(data[i]);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createDoubleVector(data, elementNACheck.neverSeenNA(), names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RDoubleVector accessNA(RAbstractDoubleVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createDoubleVector(new double[]{RRuntime.DOUBLE_NA}, RDataFactory.INCOMPLETE_VECTOR);
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createDoubleVector(new double[]{RRuntime.DOUBLE_NA}, RDataFactory.INCOMPLETE_VECTOR, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RDoubleVector accessNames(RAbstractDoubleVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        double val = vector.getDataAt(position - 1);
        elementNACheck.check(val);
        return RDataFactory.createDoubleVector(new double[]{val}, elementNACheck.neverSeenNA(), getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected double access(RAbstractDoubleVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RDoubleVector accessPosZero(RAbstractDoubleVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyDoubleVector();
        } else {
            return RDataFactory.createDoubleVector(new double[0], RDataFactory.COMPLETE_VECTOR, RDataFactory.createEmptyStringVector());
        }
    }

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RLogicalVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        byte[] data;
        if (resLength == 0) {
            data = new byte[0];
        } else {
            data = new byte[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!vector.isComplete() || !p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createLogicalVector(data, elementNACheck.neverSeenNA(), dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createLogicalVector(data, elementNACheck.neverSeenNA(), dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RLogicalVector resultVector = RDataFactory.createLogicalVector(data, elementNACheck.neverSeenNA(), dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RLogicalVector access(RLogicalVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        byte[] data = new byte[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = RRuntime.LOGICAL_NA;
            } else {
                data[i] = vector.getDataAt(position - 1);
                elementNACheck.check(data[i]);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createLogicalVector(data, elementNACheck.neverSeenNA(), names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RLogicalVector accessNA(RLogicalVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_NA}, RDataFactory.INCOMPLETE_VECTOR);
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_NA}, RDataFactory.INCOMPLETE_VECTOR, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RLogicalVector accessNames(RAbstractLogicalVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        byte val = vector.getDataAt(position - 1);
        elementNACheck.check(val);
        return RDataFactory.createLogicalVector(new byte[]{val}, elementNACheck.neverSeenNA(), getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected byte access(RLogicalVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RLogicalVector accessPosZero(RLogicalVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyLogicalVector();
        } else {
            return RDataFactory.createLogicalVector(new byte[0], RDataFactory.COMPLETE_VECTOR, RDataFactory.createEmptyStringVector());
        }
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        String[] data;
        if (resLength == 0) {
            data = new String[0];
        } else {
            data = new String[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!vector.isComplete() || !p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createStringVector(data, elementNACheck.neverSeenNA(), dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createStringVector(data, elementNACheck.neverSeenNA(), dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RStringVector resultVector = RDataFactory.createStringVector(data, elementNACheck.neverSeenNA(), dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RStringVector access(RStringVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        String[] data = new String[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = RRuntime.STRING_NA;
            } else {
                data[i] = vector.getDataAt(position - 1);
                elementNACheck.check(data[i]);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createStringVector(data, elementNACheck.neverSeenNA(), names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RStringVector accessNA(RStringVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RStringVector accessNames(RAbstractStringVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        String val = vector.getDataAt(position - 1);
        elementNACheck.check(val);
        return RDataFactory.createStringVector(new String[]{val}, elementNACheck.neverSeenNA(), getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected String access(RStringVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RStringVector accessPosZero(RStringVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyStringVector();
        } else {
            return RDataFactory.createStringVector(new String[0], RDataFactory.COMPLETE_VECTOR, RDataFactory.createEmptyStringVector());
        }
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        double[] data;
        if (resLength == 0) {
            data = new double[0];
        } else {
            data = new double[resLength << 1];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!vector.isComplete() || !p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createComplexVector(data, elementNACheck.neverSeenNA(), dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createComplexVector(data, elementNACheck.neverSeenNA(), dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RComplexVector resultVector = RDataFactory.createComplexVector(data, elementNACheck.neverSeenNA(), dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RComplexVector access(RComplexVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        double[] data = new double[resLength << 1];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        int ind = 0;
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
                data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
            } else {
                RComplex val = vector.getDataAt(position - 1);
                data[ind++] = val.getRealPart();
                data[ind++] = val.getImaginaryPart();
                elementNACheck.check(val);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createComplexVector(data, elementNACheck.neverSeenNA(), names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RComplexVector accessNA(RComplexVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createComplexVector(new double[]{RRuntime.COMPLEX_NA_REAL_PART, RRuntime.COMPLEX_NA_IMAGINARY_PART}, RDataFactory.INCOMPLETE_VECTOR);
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createComplexVector(new double[]{RRuntime.COMPLEX_NA_REAL_PART, RRuntime.COMPLEX_NA_IMAGINARY_PART}, RDataFactory.INCOMPLETE_VECTOR, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RComplexVector accessNames(RAbstractComplexVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        RComplex val = vector.getDataAt(position - 1);
        elementNACheck.check(val);
        return RDataFactory.createComplexVector(new double[]{val.getRealPart(), val.getImaginaryPart()}, elementNACheck.neverSeenNA(), getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected RComplex access(RComplexVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RComplexVector accessPosZero(RComplexVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyComplexVector();
        } else {
            return RDataFactory.createComplexVector(new double[0], RDataFactory.COMPLETE_VECTOR, RDataFactory.createEmptyStringVector());
        }
    }

    @Specialization
    protected RRawVector access(VirtualFrame frame, RRawVector vector, @SuppressWarnings("unused") int recLevel, Object[] positions, RAbstractLogicalVector dropDim) {
        // compute length of dimensions array and of the resulting vector
        DimsAndResultLength res = getDimsAndResultLength(positions, dropDim.getLength() == 0 ? RRuntime.LOGICAL_TRUE : dropDim.getDataAt(0));
        int[] dimensions = res.dimensions;
        int resLength = res.resLength;
        int[] srcDimensions = vector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        byte[] data;
        if (resLength == 0) {
            data = new byte[0];
        } else {
            data = new byte[resLength];
            RIntVector p = (RIntVector) positions[positions.length - 1];
            int srcDimSize = srcDimensions[numSrcDimensions - 1];
            int accSrcDimensions = vector.getLength() / srcDimSize;
            int accDstDimensions = resLength / p.getLength();

            elementNACheck.enable(!p.isComplete());
            for (int i = 0; i < p.getLength(); i++) {
                int dstArrayBase = accDstDimensions * i;
                int pos = p.getDataAt(i);
                int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
                getMultiDimData(frame, data, vector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
            }
        }
        RList dimNames = vector.getDimNames();
        if (dimNames == null) {
            return RDataFactory.createRawVector(data, dimensions);
        } else {
            if (dimensions == null) {
                // construct names
                RStringVector names = getNames(frame, vector, positions, numSrcDimensions, namesNACheck);
                return RDataFactory.createRawVector(data, dimensions, names);
            } else {
                // construct dimnames
                int dimLength = dimensions.length;
                RRawVector resultVector = RDataFactory.createRawVector(data, dimensions);
                int numDstDimensions = dimLength;
                RList dstDimNames = RDataFactory.createList(new Object[dimLength]);
                getDimNames(frame, dstDimNames, vector, positions, numSrcDimensions, numDstDimensions, namesNACheck);
                resultVector.setDimNames(dstDimNames);
                return resultVector;
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RRawVector access(RRawVector vector, int recLevel, RIntVector p, RAbstractLogicalVector dropDim) {
        int resLength = p.getLength();
        byte[] data = new byte[resLength];
        elementNACheck.enable(!vector.isComplete() || !p.isComplete());
        for (int i = 0; i < p.getLength(); i++) {
            int position = p.getDataAt(i);
            if (elementNACheck.check(position)) {
                data[i] = 0;
            } else {
                data[i] = vector.getDataAt(position - 1).getValue();
                elementNACheck.check(data[i]);
            }
        }
        RStringVector names = getNamesVector(vector.getNames(), p, resLength, namesNACheck);
        return RDataFactory.createRawVector(data, names);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isPositionNA", "isSubset"})
    protected RRawVector accessNA(RRawVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createRawVector(new byte[]{0});
        } else {
            RStringVector names = RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, RDataFactory.INCOMPLETE_VECTOR);
            return RDataFactory.createRawVector(new byte[]{0}, names);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "hasNames", "isSubset", "!isPositionNA", "!isPositionNegative"})
    protected RRawVector accessNames(RAbstractRawVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        RRaw val = vector.getDataAt(position - 1);
        return RDataFactory.createRawVector(new byte[]{val.getValue()}, getName(vector, position));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPositionZero", "!isPositionNA", "!isPositionNegative"})
    protected RRaw access(RRawVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return vector.getDataAt(position - 1);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPositionZero")
    protected RRawVector accessPosZero(RRawVector vector, int recLevel, int position, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        if (vector.getNames() == RNull.instance) {
            return RDataFactory.createEmptyRawVector();
        } else {
            return RDataFactory.createRawVector(new byte[0], RDataFactory.createEmptyStringVector());
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "noPosition")
    protected Object accessListEmptyPos(RAbstractContainer container, int recLevel, RList positions, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "onePosition")
    protected Object accessListOnePos(RAbstractContainer container, int recLevel, RList positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "multiPos")
    protected Object accessListMultiPosList(RList vector, int recLevel, RList positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"multiPos", "!isVectorList"})
    protected Object accessListMultiPos(RAbstractContainer container, int recLevel, RList positions, RAbstractLogicalVector dropDim) {
        if (!isSubset) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object accessListMultiPos(RAbstractContainer container, int recLevel, RComplex positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object accessListMultiPos(RAbstractContainer container, int recLevel, RRaw positions, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
    }

    // this should really be implemented in R
    @Specialization(guards = "!isSubset")
    protected Object access(VirtualFrame frame, RDataFrame dataFrame, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return accessRecursive(frame, dataFrame.getVector(), position, recLevel, dropDim);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isSubset")
    protected Object accessSubset(RDataFrame dataFrame, int recLevel, int position, RAbstractLogicalVector dropDim) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.DATA_FRAMES_SUBSET_ACCESS);
    }

    @Specialization
    protected Object access(VirtualFrame frame, RExpression expression, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return accessRecursive(frame, expression.getList(), position, recLevel, dropDim);
    }

    @Specialization
    protected Object access(VirtualFrame frame, RLanguage lang, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return accessRecursive(frame, lang.getList(), position, recLevel, dropDim);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object access(RPairList pairlist, int recLevel, int position, RAbstractLogicalVector dropDim) {
        return pairlist.getDataAtAsObject(position - 1);
    }

    protected boolean outOfBounds(RList vector, @SuppressWarnings("unused") int recLevel, int position) {
        return position > vector.getLength();
    }

    protected boolean outOfBoundsNegative(RAbstractContainer container, @SuppressWarnings("unused") int recLevel, int position) {
        return -position > container.getLength();
    }

    @SuppressWarnings("unused")
    protected boolean oneElemVector(RAbstractContainer container, int recLevel, int position) {
        return container.getLength() == 1;
    }

    @SuppressWarnings("unused")
    protected boolean isPositionNegative(RAbstractContainer container, int recLevel, int position) {
        return position < 0;
    }

    protected boolean isVectorList(RAbstractContainer container) {
        return container.getElementClass() == Object.class;
    }

    protected boolean wrongDimensions(RAbstractContainer container, @SuppressWarnings("unused") int recLevel, Object[] positions) {
        return container.getDimensions() == null || container.getDimensions().length != positions.length;
    }

    @SuppressWarnings("unused")
    protected static boolean isFirstPositionPositive(RNull vector, int recLevel, RAbstractIntVector positions) {
        return positions.getDataAt(0) > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean isFirstPositionPositive(RFunction vector, int recLevel, RAbstractIntVector positions) {
        return positions.getDataAt(0) > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean isFirstPositionOne(RFunction vector, int recLevel, RAbstractIntVector positions) {
        return positions.getDataAt(0) == 1;
    }

    @SuppressWarnings("unused")
    protected static boolean isPositionZero(RAbstractContainer container, int recLevel, int position) {
        return position == 0;
    }

    @SuppressWarnings("unused")
    protected static boolean isPositionNA(RAbstractContainer container, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isSubset() {
        return isSubset;
    }

    @SuppressWarnings("unused")
    protected static boolean hasNames(RAbstractContainer container, int recLevel, int position) {
        return container.getNames() != RNull.instance;
    }

    @SuppressWarnings("unused")
    protected static boolean hasNames(RAbstractContainer container, int recLevel, RStringVector position) {
        return container.getNames() != RNull.instance;
    }

    @SuppressWarnings("unused")
    protected static boolean twoPosition(RAbstractContainer container, int recLevel, RAbstractVector p) {
        return p.getLength() == 2;
    }

    @SuppressWarnings("unused")
    protected static boolean onePosition(RAbstractContainer container, int recLevel, RAbstractVector p) {
        return p.getLength() == 1;
    }

    @SuppressWarnings("unused")
    protected static boolean noPosition(RAbstractContainer container, int recLevel, RAbstractVector p) {
        return p.getLength() == 0;
    }

    @SuppressWarnings("unused")
    protected static boolean multiPos(RAbstractContainer container, int recLevel, RAbstractVector positions) {
        return positions.getLength() > 1;
    }

    @SuppressWarnings("unused")
    protected static boolean inRecursion(RAbstractContainer container, int recLevel, RIntVector positions) {
        return recLevel > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean inRecursion(RNull vector, int recLevel, RAbstractIntVector positions) {
        return recLevel > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean inRecursion(RFunction vector, int recLevel, RAbstractIntVector positions) {
        return recLevel > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean inRecursion(RFunction vector, int recLevel, RAbstractStringVector positions) {
        return recLevel > 0;
    }

    @SuppressWarnings("unused")
    protected static boolean inRecursion(RFunction vector, int recLevel, Object positions) {
        return recLevel > 0;
    }

    public static AccessArrayNode create(boolean isSubset, RNode vector, PositionsArrayNode positions, RNode dropDim) {
        return AccessArrayNodeFactory.create(isSubset, vector, ConstantNode.create(0), positions, dropDim);
    }

}
