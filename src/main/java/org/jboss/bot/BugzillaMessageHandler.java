/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class BugzillaMessageHandler extends MessageHandler {

    private final long dupeTime;
    private final long expireTime;
    private final ThreadLocal<RecursionState> recursionState = new ThreadLocal<RecursionState>() {
        protected RecursionState initialValue() {
            return new RecursionState();
        }
    };

    public BugzillaMessageHandler(Properties properties) {
        dupeTime = Long.parseLong(properties.getProperty("bugzilla.cache.duplicate.ms", "10000"));
        expireTime = Long.parseLong(properties.getProperty("bugzilla.cache.expire.ms", "45000"));
    }

    private static final class CacheEntry {
        private final long timestamp;
        private final String key;
        private final String id;
        private final String product;
        private final String summary;
        private final String status;
        private final String priority;
        private final String assignee;
        private final String link;
        private final String redirect;

        private CacheEntry(final long timestamp, final String key, final String id, final String product, final String summary, final String status, final String priority, final String assignee, final String link, final String redirect) {
            this.timestamp = timestamp;
            this.key = key;
            this.id = id;
            this.product = product;
            this.summary = summary;
            this.status = status;
            this.priority = priority;
            this.assignee = assignee;
            this.link = link;
            this.redirect = redirect;
        }
    }

    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>() {
        protected boolean removeEldestEntry(final Map.Entry<String, CacheEntry> eldest) {
            return System.currentTimeMillis() > eldest.getValue().timestamp + expireTime;
        }
    });

    private static final Pattern BZ_URL = Pattern.compile("(?:(?:(https?://[.a-zA-Z_-]+(?::\\d+)?)/(?:[^?/]*/)*show_bug\\.cgi\\?id=)|(?:[Bb][Zz]\\s*#))(\\d+)");

    public boolean doHandle(final JBossBot bot, final String channel, final String msg) {
        final RecursionState state = recursionState.get();
        state.enter();
        try {
            int i = 5;
            final long now = System.currentTimeMillis();
            final StringBuilder builder = new StringBuilder();
            Matcher matcher = BZ_URL.matcher(msg);
            while (matcher.find() && i-- > 0) {
                String baseUrl = matcher.group(1);
                if (baseUrl == null) baseUrl = "https://bugzilla.redhat.com";
                String bugId = matcher.group(2);
                final String key = baseUrl + "/show_bug.cgi?id=" + bugId;
                if (! state.add(key)) {
                    // we already reported on this issue
                    continue;
                }
                CacheEntry cacheEntry = cache.get(key);
                if (cacheEntry != null) {
                    final long timestamp = cacheEntry.timestamp;
                    if (timestamp + dupeTime > now) {
                        // we just reported this issue very recently
                        continue;
                    }
                    if (timestamp + expireTime < now) {
                        // expired...
                        cache.remove(key);
                    }
                    // report the cache entry.
                } else {
                    // create a new cache entry.
                    cacheEntry = lookup(key, bugId, now);
                    if (cacheEntry == null) continue;
                    cache.put(key, cacheEntry);
                }
                if (cacheEntry.redirect != null) {
                    builder.append((char) 2).append("bugzilla").append((char)2).append((char) 15).append(' ');
                    builder.append('[').append((char)3).append('3').append(cacheEntry.product).append(' ').append((char)2).append('#').append(bugId).append((char)2).append((char) 15).append("] ");
                    builder.append((char) 3).append('7').append("Redirected to: ").append((char) 3).append("10").append(cacheEntry.redirect).append((char) 15);
                } else {
                    builder.append((char) 2).append("bugzilla").append((char)2).append((char) 15).append(' ');
                    builder.append('[').append((char)3).append('3').append(cacheEntry.product).append(' ').append((char)2).append('#').append(bugId).append((char)2).append((char) 15).append("] ");
                    builder.append(cacheEntry.summary);
                    builder.append(" [").append((char)3).append("10").append(cacheEntry.status).append((char) 15).append(',');
                    builder.append((char)3).append('7').append(' ').append(cacheEntry.priority).append((char) 15).append(',');
                    builder.append((char)3).append('6').append(' ').append(cacheEntry.assignee).append((char) 15).append("] ");
                    builder.append(key);
                }
                bot.sendMessage(channel, builder.toString());
                builder.setLength(0);
            }
            return false;
        } finally {
            state.exit();
        }
    }

    public boolean onMessage(final JBossBot bot, final String channel, final String sender, final String login, final String hostname, final String msg) {
        return doHandle(bot, channel, msg);
    }

    public boolean onAction(final JBossBot bot, final String sender, final String login, final String hostname, final String target, final String action) {
        return doHandle(bot, target, action);
    }

    public boolean onPrivateMessage(final JBossBot bot, final String sender, final String login, final String hostname, final String msg) {
        return doHandle(bot, sender, msg);
    }

    public boolean onSend(final JBossBot bot, final String target, final String msg) {
        return doHandle(bot, target, msg);
    }

    private static final byte[] junk = new byte[8192];

    private CacheEntry lookup(final String key, final String id, final long timestamp) {
        try {
            final URL url = new URL(key + "&ctype=xml");
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return new CacheEntry(timestamp, key, id, null, null, null, null, null, null, conn.getHeaderField("Location"));
                    }
                    System.err.println("URL " + url + " returned status " + code);
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(key, reader, timestamp, url.toURI().toString());
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
        } catch (URISyntaxException e) {
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

    private static CacheEntry parseDocument(final String key, XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(key, reader, timestamp, link);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.BUGZILLA) {
                        return null;
                    }
                    return parseBugzillaContents(key, reader, timestamp, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseRootElement(final String key, final XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.BUGZILLA) {
                        return null;
                    }
                    return parseBugzillaContents(key, reader, timestamp, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseBugzillaContents(final String key, final XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
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
                    return parseBugContents(key, reader, timestamp, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseBugContents(final String key, final XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
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
                    return new CacheEntry(timestamp, key, id, product, summary, status + " " + kind, priority, assignee, link, null);
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
        int level;
        Set<String> keys;

        private RecursionState() {
            level = 0;
            keys = new HashSet<String>();
        }

        void enter() {
            level ++;
        }

        boolean add(String key) {
            return keys.add(key);
        }

        void exit() {
            if (--level == 0) {
                keys.clear();
            }
        }
    }
}
