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
package com.oracle.truffle.r.nodes.builtin.fastr;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

@RBuiltin(name = "fastr.tree", kind = PRIMITIVE)
@RBuiltinComment("Prints the Truffle tree of a function. Use debug.tree(a, TRUE) for more detailed output.")
public abstract class FastRTreeBuiltin extends RBuiltinNode {

    private static final Object[] PARAMETER_NAMES = new Object[]{"function", "verbose"};

    @Override
    public Object[] getParameterNames() {
        return PARAMETER_NAMES;
    }

    @Override
    public RNode[] getParameterValues() {
        return new RNode[]{ConstantNode.create(RMissing.instance), ConstantNode.create(RRuntime.LOGICAL_FALSE)};
    }

    @Specialization
    public Object printTree(RFunction function, byte verbose) {
        controlVisibility();
        RootNode root = function.getTarget().getRootNode();
        if (verbose == RRuntime.LOGICAL_TRUE) {
            return NodeUtil.printTreeToString(root);
        } else {
            return NodeUtil.printCompactTreeToString(root);
        }
    }

    @Specialization
    public RNull printTree(VirtualFrame frame, Object function, @SuppressWarnings("unused") Object verbose) {
        controlVisibility();
        throw RError.error(frame, RError.Message.INVALID_VALUE, RRuntime.toString(function));
    }
}
