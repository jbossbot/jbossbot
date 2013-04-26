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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.Listener;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.managers.ListenerManager;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class JBossBotListenerManager<E extends PircBotX> implements ListenerManager<E> {

    private static final Listener[] NO_LISTENERS = new Listener[0];
    private final ExecutorService executorService;
    private final Set<Listener> listeners = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<Listener, Boolean>()));
    private final AtomicLong id = new AtomicLong();

    public JBossBotListenerManager() {
        final ThreadFactory threadFactory = new ThreadFactory() {
            public Thread newThread(final Runnable r) {
                final Thread thread = new MyThread(r);
                thread.setName("Listener thread ID#" + Integer.toHexString(thread.hashCode()));
                thread.setDaemon(true);
                return thread;
            }
        };
        executorService = new ThreadPoolExecutor(8, 16, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1024), threadFactory);
    }

    public void dispatchEvent(Event<E> event) {
        if (event instanceof MessageEvent) {
            final MessageEvent messageEvent = (MessageEvent) event;
            event = new MessageEvent<E>(event.getBot(), messageEvent.getChannel(), messageEvent.getUser(), IrcStringUtil.deformat(messageEvent.getMessage()));
        } else if (event instanceof PrivateMessageEvent) {
            final PrivateMessageEvent privateMessageEvent = (PrivateMessageEvent) event;
            event = new PrivateMessageEvent<E>(event.getBot(), privateMessageEvent.getUser(), IrcStringUtil.deformat(privateMessageEvent.getMessage()));
        } else if (event instanceof ActionEvent) {
            final ActionEvent actionEvent = (ActionEvent) event;
            event = new ActionEvent<E>(event.getBot(), actionEvent.getUser(), actionEvent.getChannel(), IrcStringUtil.deformat(actionEvent.getMessage()));
        }
        final Thread thread = Thread.currentThread();
        if (thread instanceof MyThread) {
            final MyThread myThread = (MyThread) thread;
            myThread.getEvents().addLast(event);
        } else {
            final Event<E> sendEvent = event;
            executorService.execute(new Runnable() {
                public void run() {
                    final MyThread myThread = (MyThread) Thread.currentThread();
                    final ArrayDeque<Event> events = myThread.getEvents();
                    events.addLast(sendEvent);
                    Event e;
                    final Listener[] listeners = JBossBotListenerManager.this.listeners.toArray(NO_LISTENERS);
                    while ((e = events.pollFirst()) != null) {
                        for (Listener listener : listeners) {
                            try {
                                listener.onEvent(e);
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
    }

    public boolean addListener(final Listener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(final Listener listener) {
        return listeners.remove(listener);
    }

    public boolean listenerExists(final Listener listener) {
        return listeners.contains(listener);
    }

    public Set<Listener> getListeners() {
        synchronized (listeners) {
            return new HashSet<Listener>(listeners);
        }
    }

    public void setCurrentId(final long currentId) {
        id.lazySet(currentId);
    }

    public long getCurrentId() {
        return id.get();
    }

    public long incrementCurrentId() {
        return id.getAndIncrement();
    }

    static class MyThread extends Thread {
        private final ArrayDeque<Event> events = new ArrayDeque<Event>();

        ArrayDeque<Event> getEvents() {
            return events;
        }

        public MyThread(final Runnable r) {
            super(r);
        }
    }
}
