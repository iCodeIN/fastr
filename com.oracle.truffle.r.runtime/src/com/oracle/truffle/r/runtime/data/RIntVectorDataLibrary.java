package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

// TODO: add "assertions" library, possible checks:
//  - isComplete after write corresponds to the written value
//  - something similar for isSorted?
@GenerateLibrary
public abstract class RIntVectorDataLibrary extends Library {

    static final LibraryFactory<RIntVectorDataLibrary> FACTORY = LibraryFactory.resolve(RIntVectorDataLibrary.class);
    public static LibraryFactory<RIntVectorDataLibrary> getFactory() {
        return FACTORY;
    }

    // Iterators and exceptions: to be moved to RVectorDataLibrary

    public static RInternalError notWriteableError(Class<?> dataClass, String method) {
        CompilerDirectives.transferToInterpreter();
        throw RInternalError.shouldNotReachHere(String.format("RVectorData class '%s' is not writeable, it must be materialized before writing. Method: '%s'", dataClass.getSimpleName(), method));
    }

    public static abstract class Iterator {
        private final Object store;
        private final int length;

        protected Iterator(Object store, int length) {
            this.store = store;
            this.length = length;
        }

        // Note: intentionally package private
        final Object getStore() {
            return store;
        }

        public final int getLength() {
            return length;
        }
    }

    public static final class SeqIterator extends Iterator {
        private int index;

        protected SeqIterator(Object store, int length) {
            super(store, length);
            index = -1;
        }

        public boolean next() {
            return ++index < getLength();
        }

        public void nextWithWrap() {
            // TODO
        }

        public int getIndex() {
            return index;
        }
    }

    public static final class RandomAccessIterator extends Iterator {
        protected RandomAccessIterator(Object store, int length) {
            super(store, length);
        }
    }

    // ---------------------------------------------------------------------
    // Methods copy & pasted from VectorDataLibrary, specialized for integer

    public abstract RIntVectorData materialize(RIntVectorData receiver);

    public boolean isWriteable(RIntVectorData receiver) {
        return false;
    }

    public abstract RIntVectorData copy(RIntVectorData receiver, boolean deep);

    public abstract RIntVectorData copyResized(RIntVectorData receiver, int newSize, boolean deep, boolean fillNA);

    public abstract int getLength(RIntVectorData receiver);

    public boolean isComplete(RIntVectorData receiver) {
        return false;
    }

    public boolean isSorted(RIntVectorData receiver, boolean descending, boolean naLast) {
        return false;
    }

    public abstract SeqIterator iterator(RIntVectorData receiver);

    public abstract RandomAccessIterator randomAccessIterator(RIntVectorData receiver);

    // ---------------------------------------------------------------------
    // Methods specific to integer data

    /**
     * Gives a readonly Java array view on the data. The array may or may not be copy of the
     * underlying data. Note: if you need to send an array to the native code, you should use
     * {@code TODO:RIntVector#getDataPtr()} instead.
     */
    public abstract int[] getReadonlyIntData(RIntVectorData receiver);

    // TODO: switch this: implement getReadonlyIntData using getIntDataCopy

    public int[] getIntDataCopy(RIntVectorData receiver) {
        return getReadonlyIntData(receiver);
    }

    public abstract int getIntAt(RIntVectorData receiver, int index);

    public abstract int getNext(RIntVectorData receiver, SeqIterator it);

    public abstract int getAt(RIntVectorData receiver, RandomAccessIterator it, int index);

    /**
     * Sets the value under given index. The vector must be writeable (see
     * {@link #isWriteable(RIntVectorData)}. The {@code naCheck} is used to determine if it is necessary
     * to check whether {@code value} is {@code NA}. The {@code naCheck} must be "enabled" on the source
     * of the input data, i.e., the {@code value} argument. Using this overload makes sense if this method
     * is called multiple times with the same {@code naCheck} instance, otherwise use the overload without the {@code naCheck}.
     */
    public void setIntAt(RIntVectorData receiver, int index, int value, NACheck naCheck) {
        throw notWriteableError(RIntSeqVectorData.class, "setIntAt");
    }

    public void setNext(RIntVectorData receiver, SeqIterator it, int value, NACheck naCheck) {
        throw notWriteableError(RIntSeqVectorData.class, "setIntAt");
    }

    public void setAt(RIntVectorData receiver, RandomAccessIterator it, int index, int value, NACheck naCheck) {
        throw notWriteableError(RIntSeqVectorData.class, "setIntAt");
    }

    public final void setIntAt(RIntVectorData receiver, int index, int value) {
        setIntAt(receiver, index, value, NACheck.getEnabled());
    }

    public final void setIntAt(RIntVectorData receiver, SeqIterator it, int value) {
        setNext(receiver, it, value, NACheck.getEnabled());
    }

    public final void setIntAt(RIntVectorData receiver, RandomAccessIterator it, int index, int value) {
        setAt(receiver, it, index, value, NACheck.getEnabled());
    }
}
