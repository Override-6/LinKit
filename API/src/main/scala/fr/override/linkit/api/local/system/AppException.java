/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.override.linkit.api.local.system;

import scala.collection.mutable.StringBuilder;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * thrown to report an internal incident in the Relays
 */
public class AppException extends Exception {

    public AppException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public AppException(String msg) {
        super(msg);
    }

    @Override
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        s.println(implementationHeaders());
        super.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        s.println(implementationHeaders());
        super.printStackTrace(s);
    }

    protected void appendMessage(StringBuilder sb) {
        //Must be override by implementations to take effect.
    }

    private StringBuilder implementationHeaders() {
        StringBuilder sb = new StringBuilder();
        appendMessage(sb);

        //Remove possible double line separator that could occur because of implementation
        if (sb.toString().endsWith("\n"))
            sb.deleteCharAt(sb.length() - 1);

        return sb;
    }

}
