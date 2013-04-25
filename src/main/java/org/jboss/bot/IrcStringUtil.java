/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.util.regex.Pattern;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class IrcStringUtil {

    private IrcStringUtil() {
    }

    private static final Pattern DEFORMAT = Pattern.compile(
        "(?:" +
            "[\\x02\\x0F\\x11\\x12\\x16\\x1d\\x1f]" + // single character color codes
            "|" +
            "\\x03\\d{0,2},\\d{0,2}" + // standard mIRC colors
            "|" +
            "\\x04[0-9a-fA-F]{6}" + // VisualIRC-style RGB codes
            "|" +
            "\\x1b\\[[?=]?(?:\\d+(?:;\\d+)*)?[@-_]" + // ANSI argument sequences
        ")+");

    /**
     * Strip IRC colors and formatting from a string.
     *
     * @param original the original
     * @return the clean string
     */
    public static String deformat(String original) {
        return DEFORMAT.matcher(original).replaceAll("");
    }
}
