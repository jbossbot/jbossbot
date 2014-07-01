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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.prefs.Preferences;

import com.flurg.thimbot.ThimBot;
import com.flurg.thimbot.handler.AuthenticationHandler;
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
        bot.setDesiredNick(prefs.get("nick", "jbossbot"));
        bot.setRealName(prefs.get("realname", "JBossBot"));
        bot.setVersion(prefs.get("version", "JBoss Bot, accept no substitute!"));
        this.bot = bot;
    }

    public Preferences getPrefNode() {
        return prefNode;
    }

    public ThimBot getThimBot() {
        return bot;
    }

    public static void main(String[] args) throws IOException {
        final JBossBot bot = new JBossBot();
        Preferences nickserv = bot.getPrefNode().node("nickserv");
        String nick = nickserv.get("nick", "jbossbot");
        char[] password = nickserv.get("password", "").toCharArray();
        bot.getThimBot().addEventHandler(new AuthenticationHandler(nick, password));
        final ArrayList<JBossBotServiceProvider> providers = new ArrayList<JBossBotServiceProvider>();
        for (JBossBotServiceProvider provider : ServiceLoader.load(JBossBotServiceProvider.class, JBossBotServlet.class.getClassLoader())) {
            providers.add(provider);
        }
        Collections.sort(providers, JBossBotServiceProvider.COMPARATOR);
        for (JBossBotServiceProvider provider : providers) {
            log.debugf("Registering %s", provider);
            provider.register(bot, null);
        }
        bot.getThimBot().connect();
    }
}
