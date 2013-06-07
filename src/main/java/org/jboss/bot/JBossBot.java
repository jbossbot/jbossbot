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

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.prefs.Preferences;

import com.flurg.thimbot.ThimBot;
import org.jboss.logging.Logger;

import javax.net.SocketFactory;

@SuppressWarnings("unchecked")
public final class JBossBot {

    private static final Logger log = Logger.getLogger("org.jboss.bot");

    private final Preferences prefNode = Preferences.userRoot().node("jbossbot");
    private final ThimBot bot;

    public JBossBot() {
        log.debug("Configuring...");
        Preferences prefs = prefNode;
        final String serverName = prefs.get("server", "irc.freenode.net");
        final boolean ssl = prefs.getBoolean("tls", true);
        final int port = prefs.getInt("server-port", 7070);
        final SocketFactory socketFactory;
        if (ssl) {
            socketFactory = JBossBotUtils.getSSLSocketFactory();
        } else {
            socketFactory = JBossBotUtils.getSocketFactory();
        }
        final ThimBot bot = new ThimBot(prefNode, new InetSocketAddress(serverName, port), socketFactory);
        bot.setLogin(prefs.get("login", "jbossbot"));
        bot.setInitialNick(prefs.get("nick", "jbossbot"));
        bot.setRealName(prefs.get("realname", "JBossBot"));
        bot.setVersion(prefs.get("version", "JBoss Bot, accept no substitute!"));
        this.bot = bot;
    }

    public Preferences getPrefNode() {
        return prefNode;
    }

    static void safeClose(Closeable c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    static void safeClose(Socket c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    static void safeClose(ServerSocket c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    public ThimBot getThimBot() {
        return bot;
    }
}
