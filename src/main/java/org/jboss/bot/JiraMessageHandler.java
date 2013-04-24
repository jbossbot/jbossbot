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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class JiraMessageHandler extends MessageHandler {

    private final long dupeTime;
    private final long expireTime;
    private final Map<String, Site> sites;
    private final Map<String, String> channels;
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

    public JiraMessageHandler(Properties properties) {
        int i = 0;
        final Map<String, Site> sites = new HashMap<String, Site>();
        for (;;i++) {
            final String url = properties.getProperty("jira.site." + i + ".url");
            if (url == null) {
                break;
            }
            final String username = properties.getProperty("jira.site." + i + ".username");
            final String password = properties.getProperty("jira.site." + i + ".password");
            final String[] prefixes = properties.getProperty("jira.site." + i + ".prefixes").split(",");
            for (String prefix : prefixes) {
                sites.put(prefix, new Site(url, username, password));
            }
        }
        final String ignored = properties.getProperty("jira.ignored");
        if (ignored != null) for (String prefix : ignored.split(",")) {
            sites.put(prefix, null);
        }
        this.sites = sites;
        final Map<String, String> channels = new HashMap<String, String>();
        for (i = 0;;i++) {
            final String keyString = properties.getProperty("jira.monitor." + i + ".key");
            if (keyString == null) {
                break;
            }
            final String keys[] = keyString.split(",");
            final String channel = properties.getProperty("jira.monitor." + i + ".channel");
            if (channel != null) {
                for (String key : keys) {
                    channels.put(key, channel);
                }
            }
        }
        this.channels = channels;
        final String url = properties.getProperty("jira.site.default.url");
        final String username = properties.getProperty("jira.site.default.username");
        final String password = properties.getProperty("jira.site.default.password");
        if (url != null) {
            defaultSite = new Site(url, username, password);
        } else {
            defaultSite = null;
        }
        dupeTime = Long.parseLong(properties.getProperty("jira.cache.duplicate.ms", "20000"));
        expireTime = Long.parseLong(properties.getProperty("jira.cache.expire.ms", "45000"));
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
        private final String type;
        private final List<String> components;
        private final String resolution;

        private CacheEntry(final long timestamp, final String key, final String summary, final String status, final String priority, final String assignee, final String link, final String redirect, final String type, final List<String> components, final String resolution) {
            this.timestamp = timestamp;
            this.key = key;
            this.summary = summary;
            this.status = status;
            this.priority = priority;
            this.assignee = assignee;
            this.link = link;
            this.redirect = redirect;
            this.type = type;
            this.components = components;
            this.resolution = resolution;
        }
    }

    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>() {
        protected boolean removeEldestEntry(final Map.Entry<String, CacheEntry> eldest) {
            return System.currentTimeMillis() > eldest.getValue().timestamp + expireTime;
        }
    });

    private static final Pattern JIRA_KEY = Pattern.compile("\\b([A-Z]{2}[A-Z0-9]*|teiiddes)-\\d+");

    final AtomicLong lastNote = new AtomicLong(System.currentTimeMillis());

    public void createdNote(final JBossBot bot, final String key) {
        final int idx = key.indexOf('-');
        if (idx == -1) {
            return;
        }
        final String prefix = key.substring(0, idx);
        try {
            final long now = System.currentTimeMillis();
            final RecursionState state = recursionState.get();
            state.enter();
            try {
                if (! state.add(key)) {
                    System.err.println("Recursion state already contains " + key);
                    return;
                }
                final String channelListString = channels.get(prefix);
                if (channelListString == null) {
                    System.err.println("No channel mapping for " + key);
                    return;
                }
                final String[] channels = channelListString.split(",");
                final Site site = sites.containsKey(prefix) ? sites.get(prefix) : defaultSite;
                if (site == null) {
                    // ignored or otherwise not recognized
                    System.err.println("No site mapping for " + key);
                    return;
                }
                CacheEntry cacheEntry = cache.get(key);
                if (cacheEntry != null) {
                    final long timestamp = cacheEntry.timestamp;
                    if (timestamp + dupeTime > now) {
                        // we just reported this issue very recently
                        System.err.println("Cache hit for " + key);
                        return;
                    }
                }
                // create a new cache entry.
                cacheEntry = lookup(site, key, now);
                if (cacheEntry == null) {
                    System.err.println("Lookup failed for " + key);
                    return;
                }
                cache.put(key, cacheEntry);
                for (String channel : channels) {
                    if (channel != null && !channel.isEmpty()) {
                        sendCacheEntry(bot, channel, key, cacheEntry, "new jira");
                    }
                }
            } finally {
                state.exit();
            }
        } catch (RuntimeException ignored) {}
    }

    public boolean doHandle(final JBossBot bot, final String channel, final String msg) {
        final RecursionState state = recursionState.get();
        state.enter();
        try {
            final Matcher matcher = JIRA_KEY.matcher(msg);
            int i = 20;
            final long now = System.currentTimeMillis();
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
                }
                // create a new cache entry.
                cacheEntry = lookup(site, key, now);
                if (cacheEntry == null) continue;
                cache.put(key, cacheEntry);
                sendCacheEntry(bot, channel, key, cacheEntry);
            }
            return false;
        } finally {
            state.exit();
        }
    }

    private static void sendCacheEntry(final JBossBot bot, final String channel, final String key, final CacheEntry cacheEntry) {
        sendCacheEntry(bot, channel, key, cacheEntry, "jira");
    }

    private static void sendCacheEntry(final JBossBot bot, final String channel, final String key, final CacheEntry cacheEntry, final String banner) {
        final StringBuilder builder = new StringBuilder();
        if (cacheEntry.redirect != null) {
            builder.append((char) 2).append(banner).append((char)2).append((char) 15).append(' ');
            builder.append('[').append((char)3).append('3').append(key).append((char) 15).append("] ");
            builder.append((char) 3).append('7').append("Redirected to: ").append((char) 3).append("10").append(cacheEntry.redirect).append((char) 15);
        } else {
            builder.append((char) 2).append(banner).append((char)2).append((char) 15).append(' ');
            builder.append('[').append((char) 3).append('3').append(key).append((char) 15).append("] ");
            builder.append(cacheEntry.summary);
            String status;
            if (cacheEntry.resolution == null && ! cacheEntry.resolution.isEmpty()) {
                status = cacheEntry.status;
            } else {
                status = cacheEntry.status + " (" + cacheEntry.resolution + ")";
            }
            builder.append(" [").append((char)3).append("10").append(status).append(' ').append(cacheEntry.type).append((char) 15).append(',');
            builder.append((char) 3).append('7').append(' ').append(cacheEntry.priority).append((char) 15).append(',');
            if (! cacheEntry.components.isEmpty()) {
                builder.append((char)3).append('5').append(' ').append(join(cacheEntry.components)).append((char) 15).append(',');
            }
            builder.append((char)3).append('6').append(' ').append(cacheEntry.assignee).append((char) 15).append("] ");
            builder.append(cacheEntry.link);
        }
        bot.sendMessage(channel, builder.toString());
    }

    private static String join(List<String> strings) {
        final StringBuilder b = new StringBuilder();
        Iterator<String> iterator = strings.iterator();
        while (iterator.hasNext()) {
            b.append(iterator.next());
            if (iterator.hasNext()) {
                b.append('/');
            }
        }
        return b.toString();
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
            final URL url = new URL(site.urlPrefix + "si/jira.issueviews:issue-xml/" + key + "/" + key + ".xml");
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return new CacheEntry(timestamp, key, null, null, null, null, null, conn.getHeaderField("Location"), null, null, null);
                    }
                    System.err.println("URL " + url + " returned status " + code);
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader, timestamp);
                    } finally {
                        reader.close();
                    }
                } finally {
                    is.close();
                }
            } finally {
//                conn.disconnect();
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    enum Element {
        RSS,
        CHANNEL,
        ITEM,
        SUMMARY,
        STATUS,
        PRIORITY,
        ASSIGNEE,
        KEY,
        LINK,
        TYPE,
        COMPONENT,
        RESOLUTION,
        UNKNOWN;
        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName("rss"), Element.RSS);
            elementsMap.put(new QName("channel"), Element.CHANNEL);
            elementsMap.put(new QName("item"), Element.ITEM);
            elementsMap.put(new QName("summary"), Element.SUMMARY);
            elementsMap.put(new QName("status"), Element.STATUS);
            elementsMap.put(new QName("priority"), Element.PRIORITY);
            elementsMap.put(new QName("assignee"), Element.ASSIGNEE);
            elementsMap.put(new QName("key"), Element.KEY);
            elementsMap.put(new QName("link"), Element.LINK);
            elementsMap.put(new QName("type"), Element.TYPE);
            elementsMap.put(new QName("component"), Element.COMPONENT);
            elementsMap.put(new QName("resolution"), Element.RESOLUTION);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }

    }

//    private List<CacheEntry> parseAtomDocument(XMLStreamReader reader, long timestamp, final Monitor monitor) throws XMLStreamException {
//
//    }

    private static CacheEntry parseDocument(XMLStreamReader reader, long timestamp) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(reader, timestamp);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.RSS) {
                        return null;
                    }
                    return parseRssContents(reader, timestamp);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseRootElement(final XMLStreamReader reader, long timestamp) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.RSS) {
                        return null;
                    }
                    return parseRssContents(reader, timestamp);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseRssContents(final XMLStreamReader reader, long timestamp) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) == Element.CHANNEL) {
                        return parseChannelContents(reader, timestamp);
                    }
                    consumeElement(reader);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseChannelContents(final XMLStreamReader reader, final long timestamp) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) == Element.ITEM) {
                        return parseItemContents(reader, timestamp);
                    }
                    consumeElement(reader);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static CacheEntry parseItemContents(final XMLStreamReader reader, final long timestamp) throws XMLStreamException {
        String summary = null;
        String key = null;
        String status = null;
        String priority = null;
        String assignee = null;
        String link = null;
        String resolution = null;
        String type = "unknown";
        List<String> components = new ArrayList<String>();
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case SUMMARY: {
                            summary = reader.getElementText().trim();
                            break;
                        }
                        case KEY: {
                            key = reader.getElementText().trim();
                            break;
                        }
                        case STATUS: {
                            status = reader.getElementText().trim();
                            break;
                        }
                        case PRIORITY: {
                            priority = reader.getElementText().trim();
                            break;
                        }
                        case ASSIGNEE: {
                            assignee = reader.getElementText().trim();
                            break;
                        }
                        case LINK: {
                            link = reader.getElementText().trim();
                            break;
                        }
                        case TYPE: {
                            type = reader.getElementText().trim();
                            break;
                        }
                        case COMPONENT: {
                            components.add(reader.getElementText().trim());
                            break;
                        }
                        case RESOLUTION: {
                            resolution = reader.getElementText().trim();
                            break;
                        }
                        default: {
                            consumeElement(reader);
                            break;
                        }
                    }
                }
                case XMLStreamConstants.END_ELEMENT: {
                    if (summary != null && key != null && status != null && priority != null && assignee != null && link != null) {
                        return new CacheEntry(timestamp, key, summary, status, priority, assignee, link, null, type, components, resolution);
                    }
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
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
