/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.functions;

import org.junit.Test;

import com.oracle.truffle.r.test.TestRBase;

public class TestS3Dispatch extends TestRBase {

    @Test
    public void testUseMethodSimple() {
        // Basic UseMethod
        assertEval("{f <- function(x){ UseMethod(\"f\",x); };f.first <- function(x){cat(\"f first\",x)};f.second <- function(x){cat(\"f second\",x)};obj <-1;" +
                        "attr(obj,\"class\")  <- \"first\";f(obj);attr(obj,\"class\")  <- \"second\";}");
        assertEval("{f<-function(x){UseMethod(\"f\")};f.logical<-function(x){print(\"logical\")};f(TRUE)}");
    }

    @Test
    public void testUseMethodOneArg() {
        // If only one argument is passed to UseMethod(), the call should
        // be resolved based on first argument to enclosing function.
        assertEval("{f <- function(x){ UseMethod(\"f\"); };f.first <- function(x){cat(\"f first\",x)}; f.second <- function(x){cat(\"f second\",x)}; obj <-1; attr(obj,\"class\")  <- \"first\"; f(obj); attr(obj,\"class\")  <- \"second\";}");
    }

    @Test
    public void testUseMethodLocalVars() {
        // The variables defined before call to UseMethod should be
        // accessible to target function.
        assertEval(Ignored.Unknown,
                        "{f <- function(x){ y<-2;locFun <- function(){cat(\"local\")}; UseMethod(\"f\"); }; f.second <- function(x){cat(\"f second\",x);locFun();}; obj <-1; attr(obj,\"class\")  <- \"second\"; f(obj);}");
    }

    @Test
    public void testUseMethodNested() {
        // The UseMethod call can be nested deep compared to where target is
        // defined.
        assertEval("{f <- function(x){g<- function(x){ h<- function(x){ UseMethod(\"f\");}; h(x)}; g(x) }; f.second <- function(x){cat(\"f second\",x);}; obj <-1; attr(obj,\"class\")  <- \"second\"; f(obj);}");
    }

    @Test
    public void testUseMethodEnclFuncArgs() {
        // All the argument passed to the caller of UseMethod() should be
        // accessible to the target method.
        assertEval("{f <- function(x,y,z){ UseMethod(\"f\"); }; f.second <- function(x,y,z){cat(\"f second\",x,y,z)}; obj <-1; attr(obj,\"class\") <- \"second\"; arg2=2; arg3=3; f(obj,arg2,arg3);}");

    }

    @Test
    public void testUseMethodReturn() {
        // All the statements after UseMethod() call should get ignored.
        assertEval("{f <- function(x){ UseMethod(\"f\");cat(\"This should not be executed\"); }; f.second <- function(x){cat(\"f second\",x);}; obj <-1; attr(obj,\"class\")  <- \"second\"; f(obj);}");
    }

    @Test
    public void testUseMethodArgsMatchingAfterDispatch() {
        assertEval("{ foo.default <- function(y, ...) { y }; foo <- function(x, ...) { UseMethod('foo') }; foo(42); }");
        assertEval("{ foo.default <- function(y, ...) { y }; foo <- function(x, ...) { UseMethod('foo') }; foo(1,2,y=3); }");
        assertEval("{ foo.default <- function(a,b,c) { print(list(a=a,b=b,c=c)) }; foo <- function(c,a,b) { UseMethod('foo') }; foo('a','b','c'); }");
    }

    @Test
    public void testNextMethod() {
        assertEval("{ g<-function(){ x<-1; class(x)<-c(\"a\",\"b\",\"c\"); f<-function(x){UseMethod(\"f\")}; f.a<-function(x){cat(\"a\");NextMethod(\"f\",x)}; f.b<-function(x){cat(\"b\")}; f(x); }; g() }");
        assertEval("{ g<-function(){ x<-1; class(x)<-c(\"a\",\"b\",\"c\"); f<-function(x){UseMethod(\"f\")}; f.a<-function(x){cat(\"a\");NextMethod(\"f\",x, 42)}; f.b<-function(x, y=7){cat(\"b\", y)}; f(x); }; g(); }");
        assertEval("{ g<-function(){ x<-1; class(x)<-c(\"a\",\"b\",\"c\"); f<-function(x){UseMethod(\"f\")}; f.a<-function(x){cat(\"a\");NextMethod(\"f\",x,\"m\",\"n\")}; f.b<-function(x, y=\"h\", z=\"i\"){cat(\"b\", y, z)}; f(x); }; g() }");
        assertEval("{ g<-function(){ x<-1; class(x)<-c(\"a\",\"b\",\"c\"); f<-function(x){UseMethod(\"f\")}; f.a<-function(x){cat(\"a\");NextMethod(\"f\",x,z=\"m\",y=\"n\")}; f.b<-function(x, y=\"h\", z=\"i\"){cat(\"b\", y, z)}; f(x); }; g() }");
        assertEval("{ foo <- function(x,y) UseMethod('foo'); foo.bar <- function(x, y) { y <- 10; NextMethod() }; foo.default <- function(x,y) cat(x,y); v <- c(1,2,3); class(v) <- 'bar'; foo(v,5) }");
        assertEval("{ f.default<-function(x, a=7) a; f.foo<-function(x, a=v) { b<-NextMethod(\"f\"); v=42; c(a,b) }; x<-1; class(x)<-\"foo\"; f<-function(x) UseMethod(\"f\"); f(x) }");
    }

    @Test
    public void testSummaryGroupDispatch() {
        assertEval("{x<-c(1,2,3);class(x)<-\"foo\";Summary.foo<-function(x,...){\"summary\"};max(x)}");
        assertEval("{x<-c(1,2,3);class(x)<-\"foo\";Summary.foo<-function(x,...){\"summary\"};min(x)}");
        assertEval("{x<-c(1,2,3);class(x)<-\"foo\";min.foo<-function(x,...){\"summary\"};min(x)}");
        // Note: default value for na.rm should be provided:
        assertEval("{Summary.myclass <- function(...,na.rm)c(list(...),na.rm); max(structure(42,class='myclass'));}");
        // Note: na.rm as named argument should be ignored when deciding on the class for dispatch
        assertEval("{Summary.myclass <- function(...,na.rm)c(list(...),na.rm); max(na.rm=TRUE,structure(42,class='myclass'));}");
    }

    @Test
    public void testOpsGroupDispatch() {
        assertEval("{x<-1;y<-7;class(x)<-\"foo\";class(y)<-\"foo\";\"*.foo\"<-function(e1,e2){min(e1,e2)};x*y}");
        assertEval("{x<-1;y<-7;class(x)<-\"foo\";class(y)<-\"fooX\";\"*.foo\"<-function(e1,e2){min(e1,e2)};x*y}");
        assertEval("{x<-1;y<-7;class(x)<-\"fooX\";class(y)<-\"foo\";\"*.foo\"<-function(e1,e2){min(e1,e2)};x*y}");
        assertEval("{x<-1;y<-7;class(x)<-\"fooX\";class(y)<-\"fooX\";\"*.foo\"<-function(e1,e2){min(e1,e2)};x*y}");

        assertEval("{x<-1;y<-7;class(x)<-\"foo\";class(y)<-\"foo\";\"^.foo\"<-function(e1,e2){e1+e2};x^y}");

        assertEval("{x<-1;class(x)<-\"foo\";\"!.foo\"<-function(e1,e2){x};!x}");
    }

    @Test
    public void testOpsGroupDispatchLs() {
        assertEval("{x<-1;y<-7;class(x)<-\"foo\";class(y)<-\"foo\";\"*.foo\"<-function(e1,e2){min(e1,e2)}; ls()}");
    }

    @Test
    public void testMathGroupDispatch() {
        assertEval("{x<--7;class(x)<-\"foo\";Math.foo<-function(z){x};abs(x);}");
        assertEval("{x<--7;class(x)<-\"foo\";Math.foo<-function(z){-z;};log(x);}");
    }

    @Test
    public void testComplexGroupDispatch() {
        assertEval("{x<--7+2i;class(x)<-\"foo\";Complex.foo<-function(z){1;};Im(x);}");
    }

    @Test
    public void testMethodTableDispatch() {
        // this test ensures that print.ts is found in the method table before print.foo is found in
        // the calling environment
        assertEval("t <- ts(1:3); class(t) <- c('ts', 'foo'); print.foo <- function(x, ...) 'foo'; print(t)");
    }

    @Test
    public void testDefaultArguments() {
        assertEval("foo<-function(x,def1=TRUE)UseMethod('foo'); foo.default<-function(x,...)list(...); foo(42);");
        assertEval("foo<-function(x,def1=TRUE)UseMethod('foo'); foo.default<-function(x,def1)def1; foo(42);");
    }

    @Test
    public void testDispatchWithPartialNameMatching() {
        assertEval("f.default<-function(abc, bbb, ...)list(abc, bbb, ...); f<-function(x,...)UseMethod('f'); f(13, ab=42, b=1, c=5);");
    }

    @Test
    public void testGenericDispatchThroughMethodsTable() {
        // Note: `[.term` is "private" in stats, but it has entry in __S3MethodsTable__
        assertEval("terms(x~z)[1];");
        assertEval("{ assign('Ops.myclass', function(a,b) 42, envir=.__S3MethodsTable__.); x<-1; class(x)<-'myclass'; x+x; }");
        assertEval("{ assign('[[.myclass', function(a,b) 42, envir=.__S3MethodsTable__.); x<-1; class(x)<-'myclass'; x[[99]]; }");
    }

    @Override
    public String getTestDir() {
        return "functions/S3";
    }
}
