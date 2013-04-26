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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import org.pircbotx.Channel;
import org.pircbotx.OutputThread;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("unchecked")
public final class JBossBot extends PircBotX {

    private static final Pattern PONG_PATTERN = Pattern.compile("PONG [^ ]+ :sync\\d+$");

    private final Preferences prefNode = Preferences.userRoot().node("jbossbot");
    private final SSLSocketFactory sslSocketFactory;

    public JBossBot(final SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        System.out.println("Configuring...");
        Preferences prefs = prefNode;
        try {
            setEncoding(prefs.get("encoding", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // unlikely; just ignore it in any case
        }
        setVerbose(true);
        setAutoNickChange(true);
        setAutoReconnect(false);
        setAutoReconnectChannels(false);
        setListenerManager(new JBossBotListenerManager<PircBotX>());
        setLogin(prefs.get("login", "jbossbot"));
        setName(prefs.get("nick", "jbossbot"));
        setFinger(prefs.get("realname", "JBossBot"));
        setVersion(prefs.get("version", "JBoss Bot, accept no substitute!"));
    }

    private int sem = 5;

    public Preferences getPrefNode() {
        return prefNode;
    }

    public void connect() throws IOException, IrcException {
        Preferences prefs = prefNode;
        final String serverName = prefs.get("server", "irc.freenode.net");
        final boolean ssl = prefs.getBoolean("tls", false);
        final int port = prefs.getInt("server-port", 6667);
        final SocketFactory socketFactory;
        if (ssl) {
            socketFactory = sslSocketFactory;
        } else {
            socketFactory = SocketFactory.getDefault();
        }
        connect(serverName, port, socketFactory);
    }

    public long getMessageDelay() {
        if (Thread.currentThread() instanceof OutputThread) {
            synchronized (this) {
                int v = --sem;
                if (v == 0) {
                    sendRawLineNow("PING sync" + (System.nanoTime() & 0x0fffffff));
                    do {
                        try {
                            wait();
                            v = sem;
                        } catch (InterruptedException e) {
                        }
                    } while (v == 0);
                }
            }
        }
        return 1L;
    }

    protected void handleLine(final String s) throws IOException {
        if (PONG_PATTERN.matcher(s).find()) {
            synchronized (this) {
                sem += prefNode.getInt("window", 5);
                notifyAll();
            }
        }
        super.handleLine(s);
    }

    public void dispatchEvent(final Event<?> event) {
        getListenerManager().dispatchEvent((Event)event);
    }

    public void sendMessage(final Channel chan, final User user, final String message) {
        super.sendMessage(chan, user, message);
        dispatchEvent(new MessageEvent<JBossBot>(this, chan, getUserBot(), message));
    }

    public void sendMessage(final User target, final String message) {
        super.sendMessage(target, message);
        dispatchEvent(new PrivateMessageEvent<JBossBot>(this, target, message));
    }

    public void sendAction(final Channel target, final String action) {
        super.sendAction(target, action);
        dispatchEvent(new ActionEvent<JBossBot>(this, getUserBot(), target, action));
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
}
