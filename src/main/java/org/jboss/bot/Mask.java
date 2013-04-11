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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Mask {
    private static final Pattern ALL = Pattern.compile(".*");

    private final Pattern nickMask;
    private final Pattern nameMask;
    private final Pattern hostMask;
    private static final Pattern MASK_CHARS = Pattern.compile("(\\*)|(\\?)|[^*?]+");

    public Mask(String mask) {
        final int nickSep = mask.indexOf('!');
        final int hostSep = mask.indexOf('@', nickSep + 1);
        if (nickSep == -1) {
            nickMask = ALL;
        } else {
            nickMask = maskToPattern(mask.substring(0, nickSep));
        }
        if (hostSep == -1) {
            nameMask = ALL;
            hostMask = maskToPattern(mask.substring(nickSep + 1));
        } else {
            nameMask = maskToPattern(mask.substring(nickSep + 1, hostSep));
            hostMask = maskToPattern(mask.substring(hostSep + 1));
        }
    }

    public boolean matches(String nick, String login, String hostName) {
        if (! nickMask.matcher(nick).matches()) {
            return false;
        }
        if (! nameMask.matcher(login).matches()) {
            return false;
        }
        if (! hostMask.matcher(hostName).matches()) {
            return false;
        }
        return true;
    }

    public static Pattern maskToPattern(String mask) {
        final StringBuilder b = new StringBuilder();
        final Matcher m = MASK_CHARS.matcher(mask);
        while (m.find()) {
            if (m.group(1) != null) {
                b.append(".*");
            } else if (m.group(2) != null) {
                b.append(".");
            } else {
                b.append(Pattern.quote(m.group()));
            }
        }
        return Pattern.compile(b.toString());
    }
}
