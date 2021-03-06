/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.codegen;

import java.util.Arrays;
import java.util.stream.Stream;

import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.ffi.RFFIVariables;

public class FFIVariablesCodeGen {
    /**
     * Generates C code necessary to glue the Java and C part together. To run this, run
     * {@code mx -v r} to get full command line that runs R and replace the main class with this
     * class and add {@code mxbuild/com.oracle.truffle.r.ffi.codegen/bin} to the classpath.
     */
    public static void main(String[] args) {
        System.out.println("// Update com.oracle.truffle.r.native/fficall/src/common/rffi_variablesindex.h with the following: \n");
        System.out.printf("// Generated by %s\n\n", FFIVariablesCodeGen.class.getSimpleName());
        for (RFFIVariables var : RFFIVariables.values()) {
            System.out.printf("#define %s_x %d\n", var.name(), var.ordinal());
        }
        System.out.printf("\n#define VARIABLES_TABLE_SIZE %d", RFFIVariables.values().length);

        System.out.println("\n\n// Update com.oracle.truffle.r.native/fficall/src/truffle_common/variables_common.h with the following: \n");

        System.out.printf("// Generated by %s\n\n", FFIVariablesCodeGen.class.getSimpleName());

        for (RFFIVariables val : RFFIVariables.values()) {
            if (val == RFFIVariables.R_Interactive) {
                System.out.printf("Rboolean %s;\n", val.name());
            } else if (val.getValue() instanceof Double) {
                System.out.printf("double %s;\n", val.name());
            } else if (val.getValue() instanceof Integer) {
                System.out.printf("int %s;\n", val.name());
            } else if (val.getValue() instanceof String) {
                System.out.printf("char* %s;\n", val.name());
            } else if (val.getValue() instanceof RSymbol) {
                System.out.printf("SEXP %s; /* \"%s\" */\n", val.name(), ((RSymbol) val.getValue()).getName());
            } else {
                System.out.printf("SEXP %s;\n", val.name());
            }
        }

        System.out.println("\nvoid Call_initvar_double(int index, double value) {");
        printInitVarFor(Arrays.stream(RFFIVariables.values()).filter(x -> x.getValue() instanceof Double), "Call_initvar_double");
        System.out.println("}\n");

        System.out.println("void Call_initvar_int(int index, int value) {");
        printInitVarFor(Arrays.stream(RFFIVariables.values()).filter(x -> x.getValue() instanceof Integer), "Call_initvar_int");
        System.out.println("}\n");

        System.out.println("void Call_initvar_string(int index, char* value) {");
        printInitVarFor(Arrays.stream(RFFIVariables.values()).filter(x -> x.getValue() instanceof String), "copystring(value)", "Call_initvar_string");
        System.out.println("}\n");

        System.out.println("void Call_initvar_obj_common(int index, void* value) {");
        printInitVarFor(Arrays.stream(RFFIVariables.values()).filter(x -> !(x.getValue() instanceof Number || x.getValue() instanceof String)), "Call_initvar_obj_common");
        System.out.println("}\n");
    }

    private static void printInitVarFor(Stream<RFFIVariables> vars, String callName) {
        printInitVarFor(vars, "value", callName);
    }

    private static void printInitVarFor(Stream<RFFIVariables> vars, String value, String callName) {
        System.out.println("    switch (index) {");
        vars.forEachOrdered(x -> System.out.printf("        case %s_x: %s = %s; break;\n", x.name(), x.name(), value));
        System.out.println("        default:");
        System.out.printf("            printf(\"%s: unimplemented index %%d\\n\", index);\n", callName);
        System.out.println("            exit(1);");
        System.out.println("    }");
    }
}
