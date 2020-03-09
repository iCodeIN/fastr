/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.r.runtime.data.VectorDataLibraryUtils.RandomAccessIterator;
import com.oracle.truffle.r.runtime.data.VectorDataLibraryUtils.SeqIterator;
import com.oracle.truffle.r.runtime.data.closures.RClosure;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.RandomIterator;

@ExportLibrary(RDoubleVectorDataLibrary.class)
@ExportLibrary(VectorDataLibrary.class)
public class RDoubleVecClosureData extends RDoubleVectorData implements RClosure {
    private final RAbstractVector vector;

    public RDoubleVecClosureData(RAbstractVector vector) {
        this.vector = vector;
    }

    @ExportMessage(library = RDoubleVectorDataLibrary.class)
    @ExportMessage(library = VectorDataLibrary.class)
    @Override
    public int getLength() {
        return vector.getLength();
    }

    @ExportMessage(library = RDoubleVectorDataLibrary.class)
    public RDoubleArrayVectorData materialize() {
        return new RDoubleArrayVectorData(getReadonlyDoubleData(), isComplete());
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public RDoubleVecClosureData copy(@SuppressWarnings("unused") boolean deep) {
        // TODO deep
        return new RDoubleVecClosureData(this.vector);
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public RDoubleArrayVectorData copyResized(int newSize, boolean deep, boolean fillNA) {
        throw new RuntimeException("TODO?");
    }

    // TODO: this will be message exported by the generic VectorDataLibrary
    // @ExportMessage
    public void transferElement(RVectorData destination, int index,
                    @CachedLibrary("destination") RDoubleVectorDataLibrary dataLib) {
        dataLib.setDoubleAt(destination, index, getDoubleAt(index));
    }

    @ExportMessage
    public double[] getReadonlyDoubleData() {
        return getDoubleDataCopy();
    }

    @ExportMessage
    public double[] getDoubleDataCopy() {
        double[] res = new double[getLength()];
        for (int i = 0; i < res.length; i++) {
            res[i] = getDoubleAt(i);
        }
        return res;
    }

    // TODO: the accesses may be done more efficiently with nodes and actually using the "store" in
    // the iterator object

    @ExportMessage
    public SeqIterator iterator() {
        return new SeqIterator(null, getLength());
    }

    @ExportMessage
    public RandomAccessIterator randomAccessIterator() {
        return new RandomAccessIterator(null, getLength());
    }

    @ExportMessage
    @Override
    public double getDoubleAt(int index) {
        VectorAccess access = vector.slowPathAccess();
        RandomIterator it = access.randomAccess(vector);
        return access.getDouble(it, index);
    }

    @ExportMessage
    public double getNext(SeqIterator it) {
        return getDoubleAt(it.getIndex());
    }

    @ExportMessage
    public double getAt(@SuppressWarnings("unused") RandomAccessIterator it, int index) {
        return getDoubleAt(index);
    }

    // RClosure overrides:

    @Override
    public Object getDelegateDataAt(int idx) {
        return vector.getDataAtAsObject(idx);
    }

    @Override
    public RAbstractVector getDelegate() {
        return vector;
    }
}
