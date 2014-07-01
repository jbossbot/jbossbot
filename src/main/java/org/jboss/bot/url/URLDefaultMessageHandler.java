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

package org.jboss.bot.url;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.flurg.thimbot.event.HandlerKey;
import com.flurg.thimbot.util.IRCStringBuilder;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class URLDefaultMessageHandler extends EventHandler {

    static final HandlerKey<Set<String>> KEY = new HandlerKey<Set<String>>() {
        public Set<String> initialValue() {
            return new HashSet<>();
        }
    };

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof AbstractURLEvent<?>) {
            handleEvent(context, (AbstractURLEvent<?>) event);
            return;
        }
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final AbstractURLEvent<?> event) throws Exception {
        final URI uri = event.getUri();
        final String uriString = uri.toString();
        final Set<String> set = context.getContextValue(KEY);
        if (! set.add(uriString)) {
            return;
        }
        final Connection connection = Jsoup.connect(uriString);
        connection.followRedirects(true);
        final Document document;
        String s;
        final IRCStringBuilder b = new IRCStringBuilder();
        try {
            document = connection.get();
            final String title = document.title().trim();
            s = b.b().append("Title: ").b().nc().fc(3).append(title).nc().append(" • ").b().u().append(uri).u().b().nc().toString();
        } catch (HttpStatusException e) {
            s = b.fc(4).append("Status ").append(e.getStatusCode()).nc().append(" • ").b().u().append(uri).u().b().nc().toString();
        }
        event.sendMessageResponse(s);
    }
}
