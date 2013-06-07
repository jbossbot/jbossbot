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

package org.jboss.bot.admin;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flurg.thimbot.ThimBot;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.MessageEvent;
import com.flurg.thimbot.source.Channel;
import com.flurg.thimbot.source.User;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.Mask;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Administration extends EventHandler {
    private final CopyOnWriteArrayList<Mask> admins = new CopyOnWriteArrayList<Mask>();

    private static final Pattern LEAVE = Pattern.compile("^%leave$");
    private static final Pattern JOIN = Pattern.compile("^%join +([^ ,]+(?:, *[^ ,]+)*)");
    private static final Pattern PART = Pattern.compile("^%part +([^ ,]+(?:, *[^ ,]+)*)");
    private static final Pattern REMOVE = Pattern.compile("^%remove +(?:((?:[^/ ]+/)*(?:[^/ ]+))/)?([^/ ]+)");
    private static final Pattern GET = Pattern.compile("^%get +(?:((?:[^/ ]+/)*(?:[^/ ]+))/)?([^/ ]+)");
    private static final Pattern SET = Pattern.compile("^%set +(?:((?:[^/ ]+/)*(?:[^/ ]+))/)?([^/ ]+) +(.*)");
    private static final Pattern RECONNECT = Pattern.compile("^%reconnect$");

    void addAdmin(Mask mask) {
        admins.add(mask);
    }

    public void handleEvent(final EventHandlerContext context, final MessageEvent event) throws Exception {
        final User user = event.getSource();
        for (Mask admin : admins) {
            if (admin.matches(user)) {
                handleCheckedMessage(event, true);
                super.handleEvent(context, event);
                return;
            }
        }
        handleCheckedMessage(event, false);
        super.handleEvent(context, event);
    }

    void handleCheckedMessage(final MessageEvent event, final boolean authed) throws IOException, BackingStoreException {
        final String msg = event.getMessage();
        final String trimmed = msg.trim();
        final ThimBot bot = event.getBot();
        if (LEAVE.matcher(trimmed).matches()) {
            if (event.getTarget() instanceof Channel) {
                bot.sendMessage(event.getTarget(), "Leaving by user request.");
                bot.part((Channel) event.getTarget(), "I was asked to leave.");
            } else {
                // ignore
                return;
            }
        }
        if (! authed) return;
        final Matcher joinMatcher = JOIN.matcher(trimmed);
        if (joinMatcher.matches()) {
            String[] joins = joinMatcher.group(1).split(", *");
            for (String j : joins) {
                bot.join(new Channel(j));
            }
            return;
        }
        final Matcher partMatcher = PART.matcher(trimmed);
        if (partMatcher.matches()) {
            String[] parts = partMatcher.group(1).split(", *");
            for (String p : parts) {
                bot.part(new Channel(p), "I was told to leave.");
            }
            return;
        }
        final Matcher getMatcher = GET.matcher(trimmed);
        if (getMatcher.matches()) {
            final String path = getMatcher.group(1);
            final String key = getMatcher.group(2);
            Preferences prefNode = bot.getPreferences();
            if (path != null && ! path.isEmpty()) {
                prefNode = prefNode.node(path);
            }
            final String value = prefNode.get(key, null);
            if (value != null) {
                event.respond(new IrcStringBuilder().fc(10).append(key).nc().append("=").b().append(value).b().nc().toString());
            } else {
                event.respond(new IrcStringBuilder().fc(10).append(key).nc().append(" not found").toString());
            }
            return;
        }
        final Matcher removeMatcher = REMOVE.matcher(trimmed);
        if (removeMatcher.matches()) {
            final String path = removeMatcher.group(1);
            final String key = removeMatcher.group(2);
            Preferences prefNode = bot.getPreferences();
            if (path != null && ! path.isEmpty()) {
                prefNode = prefNode.node(path);
            }
            prefNode.remove(key);
            prefNode.flush();
            event.respond(new IrcStringBuilder().fc(10).append(key).nc().append(" removed").toString());
            return;
        }
        final Matcher setMatcher = SET.matcher(trimmed);
        if (setMatcher.matches()) {
            final String path = setMatcher.group(1);
            final String key = setMatcher.group(2);
            final String value = setMatcher.group(3);
            Preferences prefNode = bot.getPreferences();
            if (path != null && ! path.isEmpty()) {
                prefNode = prefNode.node(path);
            }
            prefNode.put(key, value);
            prefNode.flush();
            event.respond(new IrcStringBuilder().fc(10).append(key).nc().append(" set to ").b().append(prefNode.get(key, "")).b().nc().toString());
        }
        final Matcher reconnectMatcher = RECONNECT.matcher(trimmed);
        if (reconnectMatcher.matches()) {
            event.getBot().quit("I was told to reconnect");
            return;
        }
    }
}
