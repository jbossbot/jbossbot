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

import com.flurg.thimbot.event.DisconnectEvent;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.MOTDEndEvent;
import com.flurg.thimbot.event.NoticeEvent;
import com.flurg.thimbot.source.Channel;
import com.flurg.thimbot.source.Nick;
import org.jboss.logging.Logger;

/**
 * Authenticates with services and joins all channels on connect, and handles reconnect.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Lifecycle extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.lifecycle");
    private final AtomicBoolean identified = new AtomicBoolean();

    public void handleEvent(final EventHandlerContext context, final NoticeEvent event) throws Exception {
        if (! identified.get()) {
            final Preferences preferences = event.getBot().getPreferences();
            final String sourceNick = event.getSource().getName();
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
//                            final String keyword = channelNode.get("keyword", null);
//                            if (keyword != null) {
//                                event.getBot().join(channel, keyword);
//                            } else {
//                            }
                            event.getBot().join(new Channel(channel));
                        }
                    }
                }
            }
        }
        super.handleEvent(context, event);
    }

    private void tryIdentify(final Event event, final Preferences preferences) throws IOException {
        if (! identified.get()) {
            final String password = preferences.node("nickserv").get("password", null);
            if (password != null) {
                event.getBot().sendMessage(new Nick("NickServ"), "identify " + preferences.get("nick", "jbossbot") + " " + password);
            } else {
               log.warn("No nickserv password configured");
            }
        }
    }

    public void handleEvent(final EventHandlerContext context, final MOTDEndEvent event) throws Exception {
        tryIdentify(event, event.getBot().getPreferences());
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final DisconnectEvent event) throws Exception {
        identified.set(false);
        event.getBot().connect();
    }
}
