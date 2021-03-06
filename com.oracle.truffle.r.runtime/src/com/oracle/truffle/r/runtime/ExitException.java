/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * This exception is thrown when a Polyglot R engine wants to exit, usually via the {@code quit}
 * builtin. It allows systems using multiple contexts via {@code .fastr.context.op} to handle exits
 * gracefully.
 */
@ExportLibrary(InteropLibrary.class)
public class ExitException extends AbstractTruffleException {
    private static final long serialVersionUID = 1L;
    private final int status;
    private final boolean saveHistory;

    public ExitException(int status, boolean saveHistory) {
        this.status = status;
        this.saveHistory = saveHistory;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    ExceptionType getExceptionType() {
        return ExceptionType.EXIT;
    }

    @ExportMessage
    int getExceptionExitStatus() {
        return status;
    }

    public int getStatus() {
        return status;
    }

    public boolean saveHistory() {
        return saveHistory;
    }
}
