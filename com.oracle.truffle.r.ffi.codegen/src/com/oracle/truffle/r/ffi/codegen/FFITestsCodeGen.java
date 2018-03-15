/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.codegen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.r.ffi.impl.upcalls.StdUpCallsRFFI;
import com.oracle.truffle.r.ffi.processor.RFFICpointer;
import com.oracle.truffle.r.ffi.processor.RFFICstring;

/**
 * Generates 1) C wrapper that calls each RFFI function and converts between SEXP and primitive
 * types, 2) sequence of calls to CALLDEF to register all those functions as ".Call" targets.
 * 
 * This creates R interface to all applicable RFFI functions. RFFI functions working with raw
 * pointers are excluded.
 * 
 * The generated code is to be used in testrffi package located in
 * "com.oracle.truffle.r.test.native/packages/testrffi/testrffi".
 */
public class FFITestsCodeGen {
    private static final String FUN_PREFIX = "api_";
    private static final HashSet<String> IGNORE_FUNS = new HashSet<>(Arrays.asList("Rf_duplicate", "SET_TYPEOF_FASTR", "R_ToplevelExec", "R_CleanUp", "R_ParseVector", "octsize", "R_NewHashedEnv"));

    public static void main(String[] args) {
        if (Arrays.stream(args).anyMatch(x -> "-init".equals(x))) {
            generateCInit();
        } else if (Arrays.stream(args).anyMatch(x -> "-h".equals(x))) {
            generateH();
        } else if (Arrays.stream(args).anyMatch(x -> "-r".equals(x))) {
            generateR();
        } else {
            generateC();
        }
    }

    private static void generateR() {
        System.out.println("#############");
        System.out.printf("# Code generated by %s class run with option '-r'\n", FFITestsCodeGen.class.getName());
        System.out.println("# R wrappers for all the generated RFFI C wrappers\n");
        getFFIMethods().forEach(method -> {
            System.out.printf("api.%s <- function(...) .Call(C_api_%s, ...)\n", getName(method), getName(method));
        });
    }

    private static void generateCInit() {
        System.out.printf("// Code generated by %s class run with option '-init'\n", FFITestsCodeGen.class.getName());
        System.out.println("// The following code registers all C functions that wrap RFFI functions and convert SEXP <-> primitive types.");
        System.out.println("// The definitions of the C functions could be generated by the same Java class (but run without any option)");
        System.out.println("// RFFI functions that take/return C pointers are ignored");
        getFFIMethods().forEach(method -> {
            System.out.printf("CALLDEF(%s%s, %d),\n", FUN_PREFIX, getName(method), method.getParameterCount());
        });
        System.out.println("// ---- end of generated code");
    }

    private static void generateH() {
        System.out.println(COPYRIGHT);
        System.out.printf("// Code generated by %s class run with option '-h'\n", FFITestsCodeGen.class.getName());
        System.out.println("// See the corresponding C file for more details");
        printIncludes();
        getFFIMethods().forEach(method -> {
            System.out.println(getDeclaration(method) + ";\n");
        });
    }

    private static void generateC() {
        System.out.println(COPYRIGHT);
        System.out.printf("// Code generated by %s class", FFITestsCodeGen.class.getName());
        System.out.println("// The following code defines a 'SEXP' variant of every RFFI function implemented in FastR");
        System.out.println("// Run the same Java class with '-init' option to get sequence of CALLDEF statements that register those functions for use from R");
        System.out.println("// RFFI functions that take/return C pointers are ignored");
        printIncludes();
        System.out.println("#include \"rffiwrappers.h\"\n");
        System.out.println("#pragma GCC diagnostic push");
        System.out.println("#pragma GCC diagnostic ignored \"-Wint-conversion\"\n");
        System.out.println("#pragma GCC diagnostic ignored \"-Wincompatible-pointer-types\"\n");
        getFFIMethods().forEach(method -> {
            System.out.println(getDeclaration(method) + " {");
            String stmt = String.format("%s(%s)", getName(method), Arrays.stream(method.getParameters()).map(FFITestsCodeGen::toCValue).collect(Collectors.joining(", ")));
            System.out.println("    " + getReturnStmt(method.getReturnType(), stmt) + ';');
            if (method.getReturnType() == void.class) {
                System.out.println("    return R_NilValue;");
            }
            System.out.println("}\n");
        });
        System.out.println("#pragma GCC diagnostic pop");
        System.out.println("#pragma GCC diagnostic pop");
    }

    private static String getDeclaration(Method method) {
        return String.format("SEXP %s%s(", FUN_PREFIX, getName(method)) +
                        Arrays.stream(method.getParameters()).map(p -> "SEXP " + p.getName()).collect(Collectors.joining(", ")) + ')';
    }

    private static String getName(Method m) {
        return m.getName().replace("_FASTR", "").replace("FASTR_", "");
    }

    private static void printIncludes() {
        System.out.print("#define NO_FASTR_REDEFINE\n" +
                        "#include <R.h>\n" +
                        "#include <Rdefines.h>\n" +
                        "#include <Rinterface.h>\n" +
                        "#include <Rinternals.h>\n" +
                        "#include <Rinterface.h>\n" +
                        "#include <R_ext/Parse.h>\n" +
                        "#include <R_ext/Connections.h>\n" +
                        "#include <Rmath.h>\n\n");
    }

    private static Stream<Method> getFFIMethods() {
        return Arrays.stream(StdUpCallsRFFI.class.getMethods()).filter(m -> !ignoreMethod(m));
    }

    // ignore methods with C pointers, we only support SEXP, strings and primitives
    private static boolean ignoreMethod(Method method) {
        return IGNORE_FUNS.contains(method.getName()) || method.getAnnotation(RFFICpointer.class) != null ||
                        Arrays.stream(method.getParameterAnnotations()).anyMatch(FFITestsCodeGen::anyCPointer);
    }

    private static String toCValue(Parameter param) {
        if (param.getAnnotation(RFFICstring.class) != null || param.getType() == String.class) {
            return "R_CHAR(STRING_ELT(" + param.getName() + ", 0))";
        } else if (param.getType() == int.class || param.getType() == long.class || param.getType() == boolean.class) {
            return "INTEGER_VALUE(" + param.getName() + ")";
        } else if (param.getType() == double.class) {
            return "NUMERIC_VALUE(" + param.getName() + ")";
        } else {
            return param.getName();
        }
    }

    private static String getReturnStmt(Class<?> returnType, String value) {
        return returnType == void.class ? value : ("return " + fromCValueToSEXP(returnType, value));
    }

    private static String fromCValueToSEXP(Class<?> fromType, String value) {
        if (fromType == int.class || fromType == long.class) {
            return "ScalarInteger(" + value + ")";
        } else if (fromType == double.class) {
            return "ScalarReal(" + value + ")";
        } else if (fromType == boolean.class) {
            return "ScalarLogical(" + value + ")";
        } else if (fromType == String.class) {
            return "ScalarString(Rf_mkString(" + value + "))";
        } else if (fromType == Object.class) {
            return value;
        } else {
            throw new RuntimeException("Unsupported return type of RFFI function: " + fromType.getSimpleName());
        }
    }

    private static boolean anyCPointer(Annotation[] items) {
        return Arrays.stream(items).anyMatch(a -> a.annotationType() == RFFICpointer.class);
    }

    private static final String COPYRIGHT = "/*\n" +
                    " * Copyright (c) 2018, YEAR, Oracle and/or its affiliates. All rights reserved.\n" +
                    " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                    " *\n" +
                    " * This code is free software; you can redistribute it and/or modify it\n" +
                    " * under the terms of the GNU General Public License version 2 only, as\n" +
                    " * published by the Free Software Foundation.\n" +
                    " *\n" +
                    " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
                    " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
                    " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
                    " * version 2 for more details (a copy is included in the LICENSE file that\n" +
                    " * accompanied this code).\n" +
                    " *\n" +
                    " * You should have received a copy of the GNU General Public License version\n" +
                    " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
                    " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
                    " *\n" +
                    " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
                    " * or visit www.oracle.com if you need additional information or have any\n" +
                    " * questions.\n" +
                    " */\n".replace("YEAR", "" + Calendar.getInstance().get(Calendar.YEAR));
}
