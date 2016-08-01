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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import com.flurg.thimbot.event.DisconnectEvent;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.handler.AuthenticationHandler;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Singleton
@Startup
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
public class JBossBotEJB {

    private volatile JBossBot bot;

    private static final ScheduledExecutorService exec = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r, "JBossBot Scheduler Thread");
            thread.setDaemon(true);
            thread.start();
            return thread;
        }
    });


    @PostConstruct
    public void startup() {
        final String openshiftDataDir = System.getenv("OPENSHIFT_DATA_DIR");
        System.setProperty("java.util.prefs.userRoot", openshiftDataDir + "/" + "java-user-prefs");
        System.setProperty("java.util.prefs.systemRoot", openshiftDataDir + "/" + "java-system-prefs");

        bot = new JBossBot();
        Preferences nickserv = bot.getPrefNode().node("nickserv");
        String nick = nickserv.get("nick", "jbossbot");
        char[] password = nickserv.get("password", "").toCharArray();
        bot.getThimBot().addEventHandler(new AuthenticationHandler(nick, password));
        bot.getThimBot().addEventHandler(new EventHandler() {
            public void handleEvent(final EventHandlerContext context, final DisconnectEvent event) throws Exception {
                exec.schedule(new Runnable() {
                    public void run() {
                        try {
                            event.getBot().connect();
                        } catch (IOException e) {
                            event.getBot().dispatch(event);
                        }
                    }
                }, 10L, TimeUnit.SECONDS);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        final JBossBot bot = this.bot;
        if (bot != null) try {
            bot.getThimBot().quit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JBossBot getBot() {
        return bot;
    }
}
