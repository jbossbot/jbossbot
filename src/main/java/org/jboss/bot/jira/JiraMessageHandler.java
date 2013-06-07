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

package org.jboss.bot.jira;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flurg.thimbot.event.AbstractMessageEvent;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.MessageEvent;
import com.flurg.thimbot.source.Channel;
import com.flurg.thimbot.source.Target;
import com.flurg.thimbot.source.User;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class JiraMessageHandler extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.jira");

    private static final Pattern JIRA_KEY = Pattern.compile("\\b([A-Z]{2}[A-Z0-9]*)-\\d+", Pattern.CASE_INSENSITIVE);

    private final Map<Target, Map<String, Long>> cache = Collections.synchronizedMap(new HashMap<Target, Map<String, Long>>());

    public void handleEvent(final EventHandlerContext context, final AbstractMessageEvent<?, ?> event) throws Exception {
        final Target target = event.isToMe() ? ((User) event.getSource()).getNick() : event.getTarget();
        String message = event.getMessage();
        List<String> keys;
        List<String> keepKeys;
        keys = getIssueKeys(message);
        if (keys.isEmpty()) return;
        keepKeys = new ArrayList<String>(keys.size());
        final long dupTime = event.getBot().getPreferences().node("jira").getLong("duptime", 5L) * 1000000000L;

        synchronized (cache) {
            final long now = System.nanoTime();
            final Map<String, Long> timeMap = getTimeMap(target);
            for (String key : keys) {
                final Long time = timeMap.get(key);
                if (time == null) {
                    timeMap.put(key, Long.valueOf(now));
                } else if (now - time.longValue() <= dupTime) {
                    continue;
                } else {
                    // replace old expired key
                    timeMap.put(key, Long.valueOf(now));
                }
                keepKeys.add(key);
            }
        }

        processKeys(event, keepKeys);
        super.handleEvent(context, event);
    }

    private Map<String, Long> getTimeMap(final Target target) {
        Map<String, Long> timeMap = cache.get(target);
        if (timeMap == null) {
            cache.put(target, timeMap = new HashMap<String, Long>());
        }
        return timeMap;
    }

    private static List<String> getIssueKeys(final String message) {
        Matcher matcher = JIRA_KEY.matcher(message);
        if (matcher.find()) {
            final ArrayList<String> keys = new ArrayList<String>();
            do {
                keys.add(matcher.group().toUpperCase(Locale.US));
            } while (matcher.find());
            return keys;
        } else {
            return Collections.emptyList();
        }
    }

    private static String projectFor(String key) {
        return key.substring(0, key.indexOf('-'));
    }

    void processKeys(final AbstractMessageEvent<?, ?> event, List<String> keys) throws BackingStoreException, IOException {
        final Preferences jiraNode = event.getBot().getPreferences().node("jira");
        final Set<String> ignored = new HashSet<String>(Arrays.asList(jiraNode.get("ignored", "JSR").split(",\\s*")));
        final Preferences projectsNode = jiraNode.node("projects");
        String project;
        Preferences projectNode;
        String url;
        for (String key : keys) {
            project = projectFor(key);
            if (ignored.contains(project)) {
                continue;
            }
            if (projectsNode.nodeExists(project)) {
                projectNode = projectsNode.node(project);
            } else {
                projectNode = jiraNode.node("default");
            }
            url = projectNode.get("url", null);
            if (url == null) {
                url = jiraNode.node("default").get("url", null);
                if (url == null) {
                    continue;
                }
            }
            if (! url.endsWith("/")) {
                url += "/";
            }
            final IssueInfo issueInfo = lookup(url, key);
            if (issueInfo != null) printIssue("jira", event, issueInfo);
        }
    }

    void printIssue(final String prefix, final AbstractMessageEvent<?, ?> event, final IssueInfo issueInfo) throws IOException {
        final String key = issueInfo.key;
        final IrcStringBuilder builder = new IrcStringBuilder();
        if (issueInfo.redirect != null) {
            builder.b().append(prefix).b().nc().append(' ');
            builder.append('[').fc(3).append(key).nc().append("] ");
            builder.fc(7).append("Redirected to: ").fc(10).append(issueInfo.redirect).nc();
        } else {
            builder.b().append(prefix).b().nc().append(' ');
            builder.append('[').fc(3).append(key).nc().append("] ");
            builder.append(issueInfo.summary);
            String status;
            if (issueInfo.resolution == null && ! issueInfo.resolution.isEmpty()) {
                status = issueInfo.status;
            } else {
                status = issueInfo.status + " (" + issueInfo.resolution + ")";
            }
            builder.append(" [").fc(10).append(status).append(' ').append(issueInfo.type).nc().append(',');
            builder.fc(7).append(' ').append(issueInfo.priority).nc().append(',');
            if (! issueInfo.components.isEmpty()) {
                builder.fc(5).append(' ').append(join(issueInfo.components)).nc().append(',');
            }
            builder.fc(6).append(' ').append(issueInfo.assignee).nc().append("] ");
            builder.append(issueInfo.link);
        }
        event.respond(builder.toString());
    }

    static final class IssueInfo {
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

        IssueInfo(final String key, final String summary, final String status, final String priority, final String assignee, final String link, final String redirect, final String type, final List<String> components, final String resolution) {
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

    public void createdNote(final JBossBot bot, final String key) throws BackingStoreException, IOException {
        final Preferences jiraNode = bot.getPrefNode().node("jira");
        final Preferences projectsNode = jiraNode.node("projects");
        String project;
        Preferences projectNode;
        String url;

        project = projectFor(key);
        if (projectsNode.nodeExists(project)) {
            projectNode = projectsNode.node(project);
        } else {
            projectNode = jiraNode.node("default");
        }
        String[] channels = projectNode.get("channels", "").split(",\\s*");
        if (channels == null || channels.length == 0) {
            System.out.println("No channels for JIRA project " + project);
            return;
        }
        url = projectNode.get("url", null);
        if (url == null) {
            url = jiraNode.node("default").get("url", null);
            if (url == null) {
                System.out.println("No URL for JIRA project " + project);
                return;
            }
        }
        if (! url.endsWith("/")) {
            url += "/";
        }
        final IssueInfo issueInfo = lookup(url, key);
        final long now = System.nanoTime();
        if (issueInfo != null) {
            for (String name : channels) {
                final Channel channel = new Channel(name);
                synchronized (cache) {
                    getTimeMap(channel).put(key, Long.valueOf(now));
                }
                printIssue("new jira", new MessageEvent(bot.getThimBot(), bot.getThimBot().getBotUser(), channel, key), issueInfo);
            }
        }
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

    private IssueInfo lookup(final String urlString, final String key) {
        try {
            final URL url = new URL(urlString + "si/jira.issueviews:issue-xml/" + key + "/" + key + ".xml");
            final HttpURLConnection conn = (HttpURLConnection) JBossBotUtils.connectTo(url);
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return new IssueInfo(key, null, null, null, null, null, conn.getHeaderField("Location"), null, null, null);
                    }
                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader);
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

    private static IssueInfo parseDocument(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(reader);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.RSS) {
                        return null;
                    }
                    return parseRssContents(reader);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static IssueInfo parseRootElement(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.RSS) {
                        return null;
                    }
                    return parseRssContents(reader);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static IssueInfo parseRssContents(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) == Element.CHANNEL) {
                        return parseChannelContents(reader);
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

    private static IssueInfo parseChannelContents(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) == Element.ITEM) {
                        return parseItemContents(reader);
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

    private static IssueInfo parseItemContents(final XMLStreamReader reader) throws XMLStreamException {
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
                        return new IssueInfo(key, summary, status, priority, assignee, link, null, type, components, resolution);
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
}
