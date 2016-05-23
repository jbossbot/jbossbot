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

package org.jboss.bot.bugzilla;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flurg.thimbot.Priority;
import com.flurg.thimbot.event.ChannelActionEvent;
import com.flurg.thimbot.event.ChannelEvent;
import com.flurg.thimbot.event.ChannelMessageEvent;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.FromUserEvent;
import com.flurg.thimbot.event.HandlerKey;
import com.flurg.thimbot.event.MultiTargetEvent;
import com.flurg.thimbot.event.OutboundActionEvent;
import com.flurg.thimbot.event.OutboundMessageEvent;
import com.flurg.thimbot.event.PrivateActionEvent;
import com.flurg.thimbot.event.PrivateMessageEvent;
import com.flurg.thimbot.event.TextEvent;
import com.flurg.thimbot.util.IRCStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.bot.url.AbstractURLEvent;
import org.jboss.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class BugzillaMessageHandler extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.bugzilla");

    private final long dupeTime;

    private final ConcurrentMap<String, Map<Key, Event>> events = new ConcurrentHashMap<String, Map<Key, Event>>();
    private final HandlerKey<RecursionState> handlerKey = new HandlerKey<RecursionState>();

    public BugzillaMessageHandler(JBossBot bot) {
        dupeTime = bot.getPrefNode().getLong("bugzilla.cache.duplicate.ms", 10000);
    }

    static final class Key {
        private final String server;
        private final long id;

        Key(final String server, final long id) {
            this.server = server;
            this.id = id;
        }

        public String getServer() {
            return server;
        }

        public long getId() {
            return id;
        }

        public boolean equals(final Object obj) {
            return obj instanceof Key && id == ((Key) obj).id && server.equals(((Key) obj).server);
        }

        public int hashCode() {
            return (int) (id * 31 + server.hashCode());
        }
    }

    private static final Pattern BZ_PATTERN = Pattern.compile("(?:[Bb][Zz]\\s*#)(\\d+)");

    public void handleEvent(final EventHandlerContext context, final ChannelMessageEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final ChannelActionEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final PrivateMessageEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final PrivateActionEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final OutboundMessageEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final OutboundActionEvent event) throws Exception {
        doHandle(context, (TextEvent) event);
        super.handleEvent(context, event);
    }

    private boolean doHandle(final EventHandlerContext context, final TextEvent event) throws IOException {
        Matcher matcher = BZ_PATTERN.matcher(event.getText());
        while (matcher.find()) {
            String baseUrl = "https://bugzilla.redhat.com";
            String bugId = matcher.group(1);
            try {
                processEvent(context, (Event) event, new Key(baseUrl, Long.parseLong(bugId)));
            } catch (NumberFormatException ignored) {}
        }
        return true;
    }

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof AbstractURLEvent) {
            URI uri = ((AbstractURLEvent<?>) event).getUri();
            final String path = uri.getPath();
            if (path != null && (path.equals("show_bug.cgi") || path.endsWith("/show_bug.cgi"))) {
                final String baseUrl;
                if (path.indexOf("/show_bug.cgi") > 0) {
                    baseUrl = uri.getScheme() + "://" + uri.getAuthority() + path.substring(0, path.length() - 13);
                } else {
                    baseUrl = uri.getScheme() + "://" + uri.getAuthority();
                }
                final String id = JBossBotUtils.getURIParameterValue(uri, "id", null);
                if (id != null) {
                    final Key key = new Key(baseUrl, Long.parseLong(id));
                    processEvent(context, event, key);
                }
            } else {
                super.handleEvent(context, event);
            }
        } else {
            super.handleEvent(context, event);
        }
    }

    private void processEvent(final EventHandlerContext context, final Event event, final Key key) throws IOException {
        RecursionState state = context.getContextValue(handlerKey);
        if (state == null) context.putContextValue(handlerKey, state = new RecursionState());
        final ArrayList<String> writeTargets = new ArrayList<>();
        if (state.add(key)) {
            // new item
            final ConcurrentMap<String, Map<Key, Event>> events = this.events;
            if (event instanceof MultiTargetEvent) {
                for (String target : ((MultiTargetEvent) event).getTargets()) {
                    if (checkApply(event, key, events, target)) writeTargets.add(target);
                }
            } else if (event instanceof ChannelEvent) {
                String target = ((ChannelEvent) event).getChannel();
                if (checkApply(event, key, events, target)) writeTargets.add(target);
            } else if (event instanceof FromUserEvent) {
                String target = ((FromUserEvent) event).getFromNick();
                if (checkApply(event, key, events, target)) writeTargets.add(target);
            }
        }
        if (! writeTargets.isEmpty()) {
            String message = getMessage(key);
            if (message != null) {
                event.getBot().sendMessage(Priority.NORMAL, writeTargets, message);
            }
        }
    }

    private boolean checkApply(final Event event, final Key key, final ConcurrentMap<String, Map<Key, Event>> events, final String target) {
        Map<Key, Event> subMap = events.get(target);
        if (subMap == null) {
            Map<Key, Event> appearing = events.putIfAbsent(target, subMap = new LinkedHashMap<Key, Event>() {
                protected boolean removeEldestEntry(final Map.Entry<Key, Event> eldest) {
                    return eldest.getValue().getClockTime() < System.currentTimeMillis() - dupeTime;
                }
            });
            if (appearing != null) subMap = appearing;
        }
        synchronized (subMap) {
            Event test = subMap.get(key);
            if (test != null) {
                if (test.getClockTime() < System.currentTimeMillis() - dupeTime) {
                    subMap.put(key, event);
                    return true;
                }
            } else {
                subMap.put(key, event);
                return true;
            }
        }
        return false;
    }

    private static final byte[] junk = new byte[8192];

    private String getMessage(final Key key) {
        BzEntry entry = lookup(key);
        if (entry == null) return null;

        IRCStringBuilder builder = new IRCStringBuilder();
        builder.b().append("bugzilla").b().nc().append(' ');
        builder.append('[').fc(3).append(entry.product).append(' ').b().append('#').append(key.getId()).b().nc().append("] ");
        builder.append(entry.summary);
        builder.append(" [").fc(10).append(entry.status).nc().append(',');
        builder.fc(7).append(' ').append(entry.priority).nc().append(',');
        builder.fc(6).append(' ').append(entry.assignee).nc().append("] ");
        builder.append(entry.url);
        return builder.toString();
    }

    static class BzEntry {

        BzEntry(final String url, final String summary, final String assignee, final String priority, final String status, final String product) {
            this.url = url;
            this.summary = summary;
            this.assignee = assignee;
            this.priority = priority;
            this.status = status;
            this.product = product;
        }

        String product;
        String status;
        String priority;
        String assignee;
        String summary;
        String url;
    }

    private BzEntry lookup(final Key key) {
        try {
            final String urlString = key.getServer() + "/show_bug.cgi?id=" + key.getId();
            final URL url = new URL(urlString + "&ctype=xml");
            final HttpURLConnection conn = (HttpURLConnection) JBossBotUtils.connectTo(url);
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return null;
                    }

                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader, urlString);
                    } finally {
                        reader.close();
                    }
                } finally {
                    try {
                        while (is.read(junk) > -1);
                    } catch (IOException ignored) {}
                    is.close();
                }
            } finally {
//                conn.disconnect();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    enum Element {
        UNKNOWN,

        BUGZILLA,
        BUG,
        BUG_ID,
        ASSIGNED_TO,
        BUG_SEVERITY,
        CF_TYPE,
        PRODUCT,
        SHORT_DESC,
        BUG_STATUS,
        ;
        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName("bugzilla"), Element.BUGZILLA);
            elementsMap.put(new QName("bug"), Element.BUG);
            elementsMap.put(new QName("bug_id"), Element.BUG_ID);
            elementsMap.put(new QName("assigned_to"), Element.ASSIGNED_TO);
            elementsMap.put(new QName("bug_severity"), Element.BUG_SEVERITY);
            elementsMap.put(new QName("cf_type"), Element.CF_TYPE);
            elementsMap.put(new QName("product"), Element.PRODUCT);
            elementsMap.put(new QName("short_desc"), Element.SHORT_DESC);
            elementsMap.put(new QName("bug_status"), Element.BUG_STATUS);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }

    }

    private static BzEntry parseDocument(XMLStreamReader reader, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(reader, link);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.BUGZILLA) {
                        return null;
                    }
                    return parseBugzillaContents(reader, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static BzEntry parseRootElement(final XMLStreamReader reader, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.BUGZILLA) {
                        return null;
                    }
                    return parseBugzillaContents(reader, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static BzEntry parseBugzillaContents(final XMLStreamReader reader, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.BUG) {
                        return null;
                    }
                    for (int i = 0; i < reader.getAttributeCount(); i ++) {
                        if ("error".equals(reader.getAttributeLocalName(i))) {
                            return null;
                        }
                    }
                    return parseBugContents(reader, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static BzEntry parseBugContents(final XMLStreamReader reader, String link) throws XMLStreamException {
        String summary = null;
        String status = "(?)";
        String priority = "(?)";
        String kind = "(?)";
        String assignee = "(unassigned)";
        String id = "(?)";
        String product = "(?)";
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    final Element element = Element.of(reader.getName());
                    if (element == Element.SHORT_DESC) {
                        summary = parseValue(reader);
                    } else if (element == Element.CF_TYPE) {
                        kind = parseValue(reader);
                    } else if (element == Element.ASSIGNED_TO) {
                        for (int i = 0; i < reader.getAttributeCount(); i ++) {
                            if ("name".equals(reader.getAttributeLocalName(i))) {
                                assignee = reader.getAttributeValue(i);
                            }
                        }
                        consumeElement(reader);
                    } else if (element == Element.BUG_SEVERITY) {
                        priority = parseValue(reader);
                    } else if (element == Element.BUG_STATUS) {
                        status = parseValue(reader);
                    } else if (element == Element.PRODUCT) {
                        product = parseValue(reader);
                    } else {
                        consumeElement(reader);
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    return new BzEntry(link, summary, assignee, priority, status + " " + kind, product);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        throw new XMLStreamException("Unexpected end");
    }

    private static String parseValue(final XMLStreamReader reader) throws XMLStreamException {
        return reader.getElementText().trim();
    }

    private static void consumeElement(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    consumeElement(reader);
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static final class RecursionState {
        Set<Key> keys;

        private RecursionState() {
            keys = new HashSet<Key>();
        }

        boolean add(Key key) {
            return keys.add(key);
        }
    }
}
