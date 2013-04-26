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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.Mask;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Administration extends ListenerAdapter<JBossBot> {
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

    public void onMessage(final MessageEvent<JBossBot> event) throws Exception {
        handleMessage(event);
    }

    public void onPrivateMessage(final PrivateMessageEvent<JBossBot> event) throws Exception {
        handleMessage(event);
    }

    void handleMessage(final GenericMessageEvent<JBossBot> event) throws Exception {
        final User user = event.getUser();
        for (Mask admin : admins) {
            if (admin.matches(user)) {
                handleCheckedMessage(event, true);
                return;
            }
        }
        handleCheckedMessage(event, false);
    }

    void handleCheckedMessage(final GenericMessageEvent<JBossBot> event, final boolean authed) throws Exception {
        final String msg = event.getMessage();
        final String trimmed = msg.trim();
        final JBossBot bot = event.getBot();
        if (LEAVE.matcher(trimmed).matches()) {
            if (event instanceof MessageEvent) {
                event.respond("Leaving by user request.");
                bot.partChannel(((MessageEvent) event).getChannel(), "I was asked to leave.");
                return;
            }
        }
        if (! authed) return;
        final Matcher joinMatcher = JOIN.matcher(trimmed);
        if (joinMatcher.matches()) {
            String[] joins = joinMatcher.group(1).split(", *");
            for (String j : joins) {
                bot.joinChannel(j);
            }
            return;
        }
        final Matcher partMatcher = PART.matcher(trimmed);
        if (partMatcher.matches()) {
            String[] parts = partMatcher.group(1).split(", *");
            for (String p : parts) {
                bot.partChannel(bot.getChannel(p), "I was told to leave.");
            }
            return;
        }
        final Matcher getMatcher = GET.matcher(trimmed);
        if (getMatcher.matches()) {
            final String path = getMatcher.group(1);
            final String key = getMatcher.group(2);
            Preferences prefNode = bot.getPrefNode();
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
            Preferences prefNode = bot.getPrefNode();
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
            Preferences prefNode = bot.getPrefNode();
            if (path != null && ! path.isEmpty()) {
                prefNode = prefNode.node(path);
            }
            prefNode.put(key, value);
            prefNode.flush();
            event.respond(new IrcStringBuilder().fc(10).append(key).nc().append(" set to ").b().append(prefNode.get(key, "")).b().nc().toString());
        }
        final Matcher reconnectMatcher = RECONNECT.matcher(trimmed);
        if (reconnectMatcher.matches()) {
            event.getBot().quitServer("I was told to reconnect");
            return;
        }
    }
}
