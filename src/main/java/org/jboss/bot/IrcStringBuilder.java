/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
 * A string builder wrapper that supports colors.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class IrcStringBuilder {
    private final StringBuilder b = new StringBuilder();

    public IrcStringBuilder clear() {
        b.setLength(0);
        return this;
    }

    /**
     * Set foreground color.
     *
     * @param color the color 0-15
     * @return this builder
     */
    public IrcStringBuilder fc(int color) {
        if (color >= 0 && color <= 15) {
            b.append((char)3).append(color);
        }
        return this;
    }

    /**
     * Set background color; might restore foreground color depending on the client.
     *
     * @param color the color 0-15
     * @return this builder
     */
    public IrcStringBuilder bc(int color) {
        if (color >= 0 && color <= 15) {
            b.append((char)3).append(',').append(color);
        }
        return this;
    }

    /**
     * Set foreground and background color.
     *
     * @param color the fg color 0-15
     * @param color2 the bg color 0-15
     * @return this builder
     */
    public IrcStringBuilder c(int color, int color2) {
        if (color >= 0 && color <= 15 && color2 >=0 && color2 <= 15) {
            b.append((char)3).append(color).append(',').append(color2);
        }
        return this;
    }



    /**
     * Set normal (default) colors.  On some clients this also resets boldface and other attributes.
     *
     * @return this builder
     */
    public IrcStringBuilder nc() {
        b.append((char)15);
        return this;
    }

    /**
     * Set fixed-pitch font.  Not supported by all clients.
     *
     * @return this builder
     */
    public IrcStringBuilder f() {
        b.append((char)17);
        return this;
    }

    /**
     * Toggle reverse video.  Not supported by all clients.  On some clients this just swaps FG and BG color.
     *
     * @return this builder
     */
    public IrcStringBuilder iv() {
        b.append((char)18);
        return this;
    }

    /**
     * Toggle boldface.
     *
     * @return this builder
     */
    public IrcStringBuilder b() {
        b.append((char)2);
        return this;
    }

    /**
     * Toggle underline.
     *
     * @return this builder
     */
    public IrcStringBuilder u() {
        b.append((char)31);
        return this;
    }

    /**
     * Toggle inverse or italics, depending on the client.
     *
     * @return this builder
     */
    public IrcStringBuilder i() {
        b.append((char)22);
        return this;
    }

    /**
     * Toggle italics.  Not supported by all clients.
     *
     * @return this builder
     */
    public IrcStringBuilder it() {
        b.append((char)29);
        return this;
    }

    public IrcStringBuilder append(final Object obj) {
        b.append(obj);
        return this;
    }

    public IrcStringBuilder append(final String str) {
        b.append(str);
        return this;
    }

    public IrcStringBuilder append(final StringBuffer sb) {
        b.append(sb);
        return this;
    }

    public IrcStringBuilder append(final CharSequence s) {
        b.append(s);
        return this;
    }

    public IrcStringBuilder append(final CharSequence s, final int start, final int end) {
        b.append(s, start, end);
        return this;
    }

    public IrcStringBuilder append(final char[] str) {
        b.append(str);
        return this;
    }

    public IrcStringBuilder append(final char[] str, final int offset, final int len) {
        b.append(str, offset, len);
        return this;
    }

    public IrcStringBuilder append(final boolean b) {
        this.b.append(b);
        return this;
    }

    public IrcStringBuilder append(final char c) {
        b.append(c);
        return this;
    }

    public IrcStringBuilder append(final int i) {
        b.append(i);
        return this;
    }

    public IrcStringBuilder append(final long lng) {
        b.append(lng);
        return this;
    }

    public IrcStringBuilder append(final float f) {
        b.append(f);
        return this;
    }

    public IrcStringBuilder append(final double d) {
        b.append(d);
        return this;
    }

    public IrcStringBuilder appendCodePoint(final int codePoint) {
        b.appendCodePoint(codePoint);
        return this;
    }

    public int length() {
        return b.length();
    }

    public int capacity() {
        return b.capacity();
    }

    public void trimToSize() {
        b.trimToSize();
    }

    public void ensureCapacity(final int minimumCapacity) {
        b.ensureCapacity(minimumCapacity);
    }

    public void setLength(final int newLength) {
        b.setLength(newLength);
    }

    public String toString() {
        return b.toString();
    }
}
