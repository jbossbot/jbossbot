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

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdminMessageHandler extends MessageHandler {

    private static final Mask[] NONE = new Mask[0];
    private final Mask[] adminMasks;

    public AdminMessageHandler(Properties properties) {
        final String admins = properties.getProperty("admins");
        if (admins != null) {
            final String[] split = admins.split(",");
            final int splitLength = split.length;
            final Mask[] adminMasks = new Mask[splitLength];
            for (int i = 0; i < splitLength; i++) {
                adminMasks[i] = new Mask(admins.split(",")[i]);
            }
            this.adminMasks = adminMasks;
        } else {
            adminMasks = NONE;
        }
    }

    private static final Pattern LEAVE = Pattern.compile("^%leave$");
    private static final Pattern JOIN = Pattern.compile("^%join +([^ ,]+(?:, *[^ ,]+)*)");
    private static final Pattern PART = Pattern.compile("^%part +([^ ,]+(?:, *[^ ,]+)*)");

    public boolean onMessage(final JBossBot bot, final String channel, final String sender, final String login, final String hostname, final String msg) {
        final String trimmed = msg.trim();
        if (LEAVE.matcher(trimmed).matches()) {
            bot.sendMessage(channel, "Leaving by user request.");
            bot.partChannel(channel);
            return true;
        }
        boolean ok = false;
        for (Mask mask : adminMasks) {
            if (mask.matches(sender, login, hostname)) {
                ok = true;
                break;
            }
        }
        if (! ok) return false;
        final Matcher joinMatcher = JOIN.matcher(trimmed);
        if (joinMatcher.matches()) {
            String[] joins = joinMatcher.group(1).split(", *");
            for (String j : joins) {
                bot.joinChannel(j);
            }
            return true;
        }
        final Matcher partMatcher = PART.matcher(trimmed);
        if (partMatcher.matches()) {
            String[] parts = partMatcher.group(1).split(", *");
            for (String p : parts) {
                bot.partChannel(p);
            }
            return true;
        }
        return false;
    }

    public boolean onPrivateMessage(final JBossBot bot, final String sender, final String login, final String hostname, final String msg) {
        final String trimmed = msg.trim();
        boolean ok = false;
        for (Mask mask : adminMasks) {
            if (mask.matches(sender, login, hostname)) {
                ok = true;
                break;
            }
        }
        if (! ok) return false;
        final Matcher joinMatcher = JOIN.matcher(trimmed);
        if (joinMatcher.matches()) {
            String[] joins = joinMatcher.group(1).split(", *");
            for (String j : joins) {
                bot.joinChannel(j);
            }
            return true;
        }
        final Matcher partMatcher = PART.matcher(trimmed);
        if (partMatcher.matches()) {
            String[] parts = partMatcher.group(1).split(", *");
            for (String p : parts) {
                bot.partChannel(p);
            }
            return true;
        }
        return false;
    }
}
