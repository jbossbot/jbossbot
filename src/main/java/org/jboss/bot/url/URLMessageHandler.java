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
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flurg.thimbot.event.ChannelActionEvent;
import com.flurg.thimbot.event.ChannelMessageEvent;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.OutboundActionEvent;
import com.flurg.thimbot.event.OutboundMessageEvent;
import com.flurg.thimbot.event.PrivateActionEvent;
import com.flurg.thimbot.event.PrivateMessageEvent;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class URLMessageHandler extends EventHandler {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^ ]*[^ ,.?]", Pattern.CASE_INSENSITIVE);

    public void handleEvent(final EventHandlerContext context, final ChannelActionEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new ChannelActionURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new ChannelActionEvent(event.getBot(), event.getFromUser(), event.getChannel(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }

    public void handleEvent(final EventHandlerContext context, final ChannelMessageEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new ChannelMessageURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new ChannelMessageEvent(event.getBot(), event.getFromUser(), event.getChannel(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }

    public void handleEvent(final EventHandlerContext context, final PrivateMessageEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new PrivateMessageURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new PrivateMessageEvent(event.getBot(), event.getFromUser(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }

    public void handleEvent(final EventHandlerContext context, final PrivateActionEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new PrivateActionURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new PrivateActionEvent(event.getBot(), event.getFromUser(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }

    public void handleEvent(final EventHandlerContext context, final OutboundMessageEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new OutboundMessageURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new OutboundMessageEvent(event.getBot(), event.getTargets(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }

    public void handleEvent(final EventHandlerContext context, final OutboundActionEvent event) throws Exception {
        final StringBuilder cleanString = new StringBuilder();
        final String message = event.getText();
        final Matcher matcher = URL_PATTERN.matcher(message);
        int last = 0;
        if (matcher.find()) {
            do {
                final URI uri;
                try {
                    uri = new URI(matcher.group());
                } catch (URISyntaxException e) {
                    continue;
                }
                cleanString.append(message.substring(last, matcher.start()));
                cleanString.append("[URL]");
                context.redispatch(new OutboundActionURLEvent(event.getBot(), event, uri));
                last = matcher.end();
            } while (matcher.find());
            cleanString.append(message.substring(last, message.length()));
            super.handleEvent(context, new OutboundActionEvent(event.getBot(), event.getTargets(), cleanString.toString()));
        } else {
            super.handleEvent(context, event);
        }
    }
}
