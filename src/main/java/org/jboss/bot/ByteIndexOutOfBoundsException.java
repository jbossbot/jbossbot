/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.bot;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ByteIndexOutOfBoundsException extends IndexOutOfBoundsException {

    private static final long serialVersionUID = -4361363053527790524L;

    /**
     * Constructs a {@code ByteIndexOutOfBoundsException} with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public ByteIndexOutOfBoundsException() {
    }

    /**
     * Constructs a {@code ByteIndexOutOfBoundsException} with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public ByteIndexOutOfBoundsException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code ByteIndexOutOfBoundsException} with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param index the index that was out of bound
     */
    public ByteIndexOutOfBoundsException(final int index) {
        super("Byte index out of range: " + index);
    }

    /**
     * Constructs a {@code ByteIndexOutOfBoundsException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ByteIndexOutOfBoundsException(final Throwable cause) {
        initCause(cause);
    }

    /**
     * Constructs a {@code ByteIndexOutOfBoundsException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ByteIndexOutOfBoundsException(final String msg, final Throwable cause) {
        super(msg);
        initCause(cause);
    }

}
