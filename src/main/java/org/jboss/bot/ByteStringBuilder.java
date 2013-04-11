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

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ByteStringBuilder {

    private byte[] bytes;

    private int len;

    public ByteStringBuilder(final int capacity) {
        if (capacity < 16) {
            bytes = new byte[16];
        } else {
            bytes = new byte[capacity];
        }
    }

    public ByteStringBuilder() {
        this(16);
    }

    void expandCapacity(int minimumCapacity) {
        int newCapacity = (bytes.length + 1) * 2;
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        } else if (minimumCapacity > newCapacity) {
            newCapacity = minimumCapacity;
        }
        bytes = Arrays.copyOf(bytes, newCapacity);
    }

    public ByteStringBuilder setLength(int newLength) {
        if (newLength < 0) {
            throw new ByteIndexOutOfBoundsException(newLength);
        } else if (newLength > bytes.length) {
            expandCapacity(newLength);
        }
        if (len < newLength) {
            Arrays.fill(bytes, len, newLength, (byte) 0);
        }
        len = newLength;
        return this;
    }

    public ByteStringBuilder appendBytes(int b) {
        if (len == bytes.length) {
            expandCapacity(len + 1);
        }
        bytes[len++] = (byte) b;
        return this;
    }

    public ByteStringBuilder appendBytes(int b1, int b2) {
        if (len >= bytes.length - 1) {
            expandCapacity(len + 2);
        }
        bytes[len++] = (byte) b1;
        bytes[len++] = (byte) b2;
        return this;
    }

    public ByteStringBuilder appendBytes(int b1, int b2, int b3) {
        if (len >= bytes.length - 2) {
            expandCapacity(len + 3);
        }
        bytes[len++] = (byte) b1;
        bytes[len++] = (byte) b2;
        bytes[len++] = (byte) b3;
        return this;
    }

    public ByteStringBuilder appendBytes(int... bytes) {
        final int newCount = len + bytes.length;
        if (newCount > this.bytes.length) {
            expandCapacity(newCount);
        }
        for (int a : bytes) {
            this.bytes[len++] = (byte) a;
        }
        return this;
    }

    public ByteStringBuilder append(byte[] bytes, int offs, int len) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes is null");
        }
        if (offs < 0 || offs > bytes.length || len < 0 || offs + len > bytes.length) {
            throw new ByteIndexOutOfBoundsException(offs);
        }
        final int newCount = this.len + len;
        if (newCount > this.bytes.length) {
            expandCapacity(newCount);
        }
        System.arraycopy(bytes, offs, this.bytes, this.len, len);
        this.len = newCount;
        return this;
    }

    public ByteStringBuilder append(byte[] bytes) {
        append(bytes, 0, bytes.length);
        return this;
    }

    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public ByteStringBuilder appendAscii(String s) {
        append(s.getBytes(US_ASCII));
        return this;
    }

    public ByteStringBuilder appendUtf8(String s) {
        append(s.getBytes(UTF_8));
        return this;
    }

    public ByteStringBuilder append(String s, Charset encoding) {
        append(s.getBytes(encoding));
        return this;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, len);
    }

    public ByteStringBuilder clear() {
        len = 0;
        return this;
    }

    public ByteStringBuilder backUp(int count) {
        setLength(len - count);
        return this;
    }

    public int length() {
        return len;
    }
}
