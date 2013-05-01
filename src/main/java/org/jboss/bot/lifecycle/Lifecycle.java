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

package org.jboss.bot.lifecycle;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import org.jboss.bot.JBossBot;
import org.jboss.logging.Logger;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.MotdEvent;
import org.pircbotx.hooks.events.NoticeEvent;

/**
 * Authenticates with services and joins all channels on connect, and handles reconnect.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Lifecycle extends ListenerAdapter<JBossBot> {

    private static final Logger log = Logger.getLogger("org.jboss.bot.lifecycle");
    private final AtomicBoolean identified = new AtomicBoolean();

    public void onConnect(final ConnectEvent<JBossBot> event) throws Exception {
        identified.set(false);
    }

    public void onNotice(final NoticeEvent<JBossBot> event) throws Exception {
        if (! identified.get()) {
            final Preferences preferences = event.getBot().getPrefNode();
            final String sourceNick = event.getUser().getNick();
            final String notice = event.getMessage();
            if (sourceNick.equals("NickServ") && notice.contains("This nickname is registered.")) {
                tryIdentify(event, preferences);
            } else if (sourceNick.equals("NickServ") && notice.contains("You are now identified for")) {
                identified.set(true);
                if (preferences.getBoolean("join", true)) {
                    final Preferences channelsNode = preferences.node("channels");
                    final String[] channels = channelsNode.childrenNames();
                    for (String channel : channels) {
                        final Preferences channelNode = channelsNode.node(channel);
                        if (channelNode.getBoolean("join", true)) {
                            final String keyword = channelNode.get("keyword", null);
                            if (keyword != null) {
                                event.getBot().joinChannel(channel, keyword);
                            } else {
                                event.getBot().joinChannel(channel);
                            }
                        }
                    }
                }
            }
        }
        super.onNotice(event);
    }

    private void tryIdentify(final Event<JBossBot> event, final Preferences preferences) {
        if (! identified.get()) {
            final String password = preferences.node("nickserv").get("password", null);
            if (password != null) {
                event.getBot().sendMessage("NickServ", "identify " + preferences.get("nick", "jbossbot") + " " + password);
            } else {
               log.warn("No nickserv password configured");
            }
        }
    }

    public void onMotd(final MotdEvent<JBossBot> event) throws Exception {
        tryIdentify(event, event.getBot().getPrefNode());
        super.onMotd(event);
    }

    public void onDisconnect(final DisconnectEvent<JBossBot> event) throws Exception {
        identified.set(false);
        for (;;) {
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
                // xx
            }
            try {
                try {
                    event.getBot().disconnect();
                } catch (Throwable ignored) {}
                event.getBot().connect();
                return;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IrcException e) {
                e.printStackTrace();
            }
        }

    }
}
