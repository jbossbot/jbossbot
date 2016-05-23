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

import java.io.IOException;
import java.net.URI;

import com.flurg.thimbot.Priority;
import com.flurg.thimbot.ThimBot;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.MessageRespondableEvent;
import com.flurg.thimbot.event.TextEvent;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractURLEvent<P extends Event & MessageRespondableEvent> extends Event implements TextEvent, MessageRespondableEvent {
    private final P parent;
    private final URI uri;

    AbstractURLEvent(final ThimBot bot, final P parent, final URI uri) {
        super(bot);
        this.parent = parent;
        this.uri = uri;
    }

    public P getParent() {
        return parent;
    }

    public URI getUri() {
        return uri;
    }

    public String getText() {
        return uri.toASCIIString();
    }

    public String getRawText() {
        return uri.toASCIIString();
    }

    public void sendMessageResponse(final Priority priority, final String message) throws IOException {
        parent.sendMessageResponse(priority, message);
    }

    public void sendMessageResponse(final String message) throws IOException {
        parent.sendMessageResponse(message);
    }

    public void sendActionResponse(final Priority priority, final String message) throws IOException {
        parent.sendActionResponse(priority, message);
    }

    public void sendActionResponse(final String message) throws IOException {
        parent.sendActionResponse(message);
    }

    public String[] getResponseTargets() {
        return parent.getResponseTargets();
    }

    public abstract AbstractURLEvent<P> copyWithNewUri(URI uri);

    protected void toStringAddendum(final StringBuilder b) {
        b.append(" URL \"").append(uri).append('"');
    }
}
