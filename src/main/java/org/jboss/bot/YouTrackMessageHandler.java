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
import java.net.URI;
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

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class YouTrackMessageHandler extends MessageHandler {

    private final long dupeTime;
    private final long expireTime;
    private final Map<String, Site> sites;
    private final Site defaultSite;
    private final ThreadLocal<RecursionState> recursionState = new ThreadLocal<RecursionState>() {
        protected RecursionState initialValue() {
            return new RecursionState();
        }
    };

    private static final class Site {
        private final String urlPrefix;
        private final String username;
        private final String password;

        private Site(final String urlPrefix, final String username, final String password) {
            this.urlPrefix = urlPrefix;
            this.username = username;
            this.password = password;
        }
    }

    public YouTrackMessageHandler(Properties properties) {
        int i = 0;
        final Map<String, Site> sites = new HashMap<String, Site>();
        for (;;i++) {
            final String url = properties.getProperty("youtrack.site." + i + ".url");
            if (url == null) {
                break;
            }
            final String username = properties.getProperty("youtrack.site." + i + ".username");
            final String password = properties.getProperty("youtrack.site." + i + ".password");
            final String[] prefixes = properties.getProperty("youtrack.site." + i + ".prefixes").split(",");
            for (String prefix : prefixes) {
                sites.put(prefix, new Site(url, username, password));
            }
        }
        final String ignored = properties.getProperty("youtrack.ignored");
        if (ignored != null) for (String prefix : ignored.split(",")) {
            sites.put(prefix, null);
        }
        this.sites = sites;
        final String url = properties.getProperty("youtrack.site.default.url");
        final String username = properties.getProperty("youtrack.site.default.username");
        final String password = properties.getProperty("youtrack.site.default.password");
        if (url != null) {
            defaultSite = new Site(url, username, password);
        } else {
            defaultSite = null;
        }
        dupeTime = Long.parseLong(properties.getProperty("youtrack.cache.duplicate.ms", "10000"));
        expireTime = Long.parseLong(properties.getProperty("youtrack.cache.expire.ms", "45000"));
    }

    private static final class CacheEntry {
        private final long timestamp;
        private final String key;
        private final String summary;
        private final String status;
        private final String priority;
        private final String assignee;
        private final String link;
        private final String redirect;

        private CacheEntry(final long timestamp, final String key, final String summary, final String status, final String priority, final String assignee, final String link, final String redirect) {
            this.timestamp = timestamp;
            this.key = key;
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

    private static final Pattern YOUTRACK_KEY = Pattern.compile("\\b([A-Z]{2,}|teiiddes)-\\d+");

    public boolean doHandle(final JBossBot bot, final String channel, final String msg) {
        final RecursionState state = recursionState.get();
        state.enter();
        try {
            final Matcher matcher = YOUTRACK_KEY.matcher(msg);
            int i = 5;
            final long now = System.currentTimeMillis();
            final StringBuilder builder = new StringBuilder();
            while (matcher.find() && i-- > 0) {
                final String key = matcher.group().toUpperCase();
                if (! state.add(key)) {
                    // we already reported on this issue
                    continue;
                }
                final String prefix = matcher.group(1).toUpperCase();
                final Site site = sites.containsKey(prefix) ? sites.get(prefix) : defaultSite;
                if (site == null) {
                    // ignored or otherwise not recognized
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
                    cacheEntry = lookup(site, key, now);
                    if (cacheEntry == null) continue;
                    cache.put(key, cacheEntry);
                }
                if (cacheEntry.redirect != null) {
                    builder.append((char) 2).append("youtrack").append((char)2).append((char) 15).append(' ');
                    builder.append('[').append((char)3).append('3').append(key).append((char) 15).append("] ");
                    builder.append((char) 3).append('7').append("Redirected to: ").append((char) 3).append("10").append(cacheEntry.redirect).append((char) 15);
                } else {
                    builder.append((char) 2).append("youtrack").append((char)2).append((char) 15).append(' ');
                    builder.append('[').append((char)3).append('3').append(key).append((char) 15).append("] ");
                    builder.append(cacheEntry.summary);
                    builder.append(" [").append((char)3).append("10").append(cacheEntry.status).append((char) 15).append(',');
                    builder.append((char)3).append('7').append(' ').append(cacheEntry.priority).append((char) 15).append(',');
                    builder.append((char)3).append('6').append(' ').append(cacheEntry.assignee).append((char) 15).append("] ");
                    builder.append(cacheEntry.link);
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

    private CacheEntry lookup(final Site site, final String key, final long timestamp) {
        try {
            final URL url = new URL(site.urlPrefix + "rest/issue/" + key);
            final URI userUri = new URI(site.urlPrefix + "/issue/" + key).normalize();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return new CacheEntry(timestamp, key, null, null, null, null, null, conn.getHeaderField("Location"));
                    }
                    System.err.println("URL " + url + " returned status " + code);
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader, timestamp, userUri.toString());
                    } finally {
                        reader.close();
                    }
                } finally {
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

        ISSUE,
        FIELD,
        VALUE,
        ;
        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName("issue"), Element.ISSUE);
            elementsMap.put(new QName("field"), Element.FIELD);
            elementsMap.put(new QName("value"), Element.VALUE);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }

    }

    private static CacheEntry parseDocument(XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(reader, timestamp, link);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.ISSUE) {
                        return null;
                    }
                    return parseIssueContents(reader, timestamp, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseRootElement(final XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.ISSUE) {
                        return null;
                    }
                    return parseIssueContents(reader, timestamp, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseIssueContents(final XMLStreamReader reader, long timestamp, String link) throws XMLStreamException {
        String summary = null;
        String key = reader.getAttributeValue(null, "id");
        String status = null;
        String priority = null;
        String kind = null;
        String assignee = "(unassigned)";
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    final Element element = Element.of(reader.getName());
                    if (element == Element.FIELD) {
                        String name = reader.getAttributeValue(XMLConstants.NULL_NS_URI, "name");
                        if (name.equalsIgnoreCase("summary")) {
                            summary = parseValue(reader);
                        } else if (name.equalsIgnoreCase("state")) {
                            status = parseValue(reader);
                        } else if (name.equalsIgnoreCase("priority")) {
                            priority = parseValue(reader);
                        } else if (name.equalsIgnoreCase("assigneeFullName")) {
                            assignee = parseValue(reader);
                        } else if (name.equalsIgnoreCase("type")) {
                            kind = parseValue(reader);
                        }
                    }
                    consumeElement(reader);
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    return new CacheEntry(timestamp, key, summary, status + " " + kind, priority, assignee, link, null);
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
        if (reader.nextTag() != XMLStreamConstants.START_ELEMENT) {
            throw new XMLStreamException("Unexpected content");
        }
        if (!reader.getLocalName().equals("value")) {
            throw new XMLStreamException("Unexpected content: " + reader.getName());
        }
        final String text = reader.getElementText();
        return text;
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
