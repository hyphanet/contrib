/* Copyright (C) 2004 - 2008  db4objects Inc.  http://www.db4o.com

This file is part of the db4o open source object database.

db4o is free software; you can redistribute it and/or modify it under
the terms of version 2 of the GNU General Public License as published
by the Free Software Foundation and as clarified by db4objects' GPL 
interpretation policy, available at
http://www.db4o.com/about/company/legalpolicies/gplinterpretation/
Alternatively you can write to db4objects, Inc., 1900 S Norfolk Street,
Suite 350, San Mateo, CA 94403, USA.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. */
package com.db4o.foundation;

import java.io.*;

/**
 * @exclude
 *
 * Just delegates to the platform chaining mechanism.
 * 
 * @decaf.mixin ChainedRuntimeExceptionMixin
 */
public abstract class ChainedRuntimeException extends RuntimeException {

    public ChainedRuntimeException() {
    }

    /**
     * @decaf.replaceFirst super(msg);
     */
    public ChainedRuntimeException(String msg) {
        super(msg, null);
    }

    /**
     * @decaf.replaceFirst super(msg);
     */
    public ChainedRuntimeException(String msg, Throwable cause) {
        super(msg, cause);
    }
    
    /**
     * Provides jdk11 compatible exception chaining. decaf will mix this class
     * into {@link ChainedRuntimeException}.
     * 
     * @exclude
     */
    public static class ChainedRuntimeExceptionMixin {

        public ChainedRuntimeException _subject;
        public Throwable _cause;

        public ChainedRuntimeExceptionMixin(ChainedRuntimeException mixee) {
            _subject = mixee;
            _cause = null;
        }
        
        public ChainedRuntimeExceptionMixin(ChainedRuntimeException mixee, String msg) {
            _subject = mixee;
            _cause = null;
        }
        
        public ChainedRuntimeExceptionMixin(ChainedRuntimeException mixee, String msg, Throwable cause) {
            _subject = mixee;
            _cause = cause;
        }

        /**
        * @return The originating exception, if any
        */
        public final Throwable getCause() {
            return _cause;
        }

        public void printStackTrace() {
            printStackTrace(System.err);
        }

        public void printStackTrace(PrintStream ps) {
            printStackTrace(new PrintWriter(ps, true));
        }

        public void printStackTrace(PrintWriter pw) {
            _subject.printStackTrace(pw);
            if (_cause != null) {
                pw.println("Nested cause:");
                _cause.printStackTrace(pw);
            }
        }
    }
}

