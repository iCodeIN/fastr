/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.printer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

public final class ValuePrinters implements ValuePrinter<Object> {

    private final Map<Class<?>, ValuePrinter<?>> printers = new HashMap<>();

    private static final ValuePrinters INSTANCE = new ValuePrinters();

    private ValuePrinters() {
        printers.put(String.class, StringPrinter.INSTANCE);
        printers.put(Double.class, DoublePrinter.INSTANCE);
        printers.put(Integer.class, IntegerPrinter.INSTANCE);
    }

    public static void printValue(Object x, PrintContext printCtx) throws IOException {
        INSTANCE.print(x, printCtx);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void print(Object x, PrintContext printCtx) throws IOException {
        if (x == null) {
            NullPrinter.INSTANCE.print(null, printCtx);
        } else {
            ValuePrinter printer = printers.get(x.getClass());
            if (printer == null) {
                if (x instanceof RAbstractStringVector) {
                    printer = StringVectorPrinter.INSTANCE;
                } else if (x instanceof RAbstractDoubleVector) {
                    printer = DoubleVectorPrinter.INSTANCE;
                } else if (x instanceof RAbstractIntVector) {
                    printer = IntegerVectorPrinter.INSTANCE;
                } else if (x instanceof RAbstractComplexVector) {
                    printer = ComplexVectorPrinter.INSTANCE;
                } else if (x instanceof RAbstractListVector) {
                    printer = ListPrinter.INSTANCE;
                } else {
                    throw new UnsupportedOperationException("TODO");
                }
            }
            printer.print(x, printCtx);
        }
    }

}
