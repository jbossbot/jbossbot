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

package org.jboss.bot.youtrack;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.logging.Logger;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class YouTrackMessageHandler extends ListenerAdapter<JBossBot> {

    private static final Logger log = Logger.getLogger("org.jboss.bot.youtrack");

    private static final Pattern YOUTRACK_KEY = Pattern.compile("\\b([A-Z]{2}[A-Z0-9]*)-\\d+", Pattern.CASE_INSENSITIVE);

    private final Map<Channel, Map<String, Long>> channelMap = Collections.synchronizedMap(new HashMap<Channel, Map<String, Long>>());
    private final Map<User, Map<String, Long>> userMap = Collections.synchronizedMap(new HashMap<User, Map<String, Long>>());

    public void onPrivateMessage(final PrivateMessageEvent<JBossBot> event) throws Exception {
        final User user = event.getUser();
        String message = event.getMessage();
        List<String> keys;
        List<String> keepKeys;
        keys = getIssueKeys(message);
        if (keys.isEmpty()) return;
        keepKeys = new ArrayList<String>(keys.size());
        final long dupTime = event.getBot().getPrefNode().node("youtrack").getLong("duptime", 5L) * 1000000000L;

        synchronized (userMap) {
            final long now = System.nanoTime();
            final Map<String, Long> timeMap = getUserTimeMap(user, dupTime);
            for (String key : keys) {
                final Long time = timeMap.get(key);
                if (time == null) {
                    timeMap.put(key, Long.valueOf(now));
                } else if (time.longValue() + dupTime > now) {
                    continue;
                }
                keepKeys.add(key);
            }
        }

        processKeys(event, keepKeys);
    }

    public void onMessage(final MessageEvent<JBossBot> event) throws Exception {
        onChannelMessage(event, event.getChannel());
    }

    public void onAction(final ActionEvent<JBossBot> event) throws Exception {
        onChannelMessage(event, event.getChannel());
    }

    void onChannelMessage(final GenericMessageEvent<JBossBot> event, Channel channel) throws Exception {
        String message = event.getMessage();
        List<String> keys;
        List<String> keepKeys;
        keys = getIssueKeys(message);
        if (keys.isEmpty()) return;
        keepKeys = new ArrayList<String>(keys.size());
        final long dupTime = event.getBot().getPrefNode().node("youtrack").getLong("duptime", 5L) * 1000000000L;

        synchronized (channelMap) {
            final long now = System.nanoTime();
            final Map<String, Long> timeMap = getChannelTimeMap(channel, dupTime);
            for (String key : keys) {
                final Long time = timeMap.get(key);
                if (time == null) {
                    timeMap.put(key, Long.valueOf(now));
                } else if (time.longValue() + dupTime > now) {
                    continue;
                }
                keepKeys.add(key);
            }
        }

        processKeys(event, keepKeys);
    }

    private Map<String, Long> getChannelTimeMap(final Channel channel, final long dupTime) {
        Map<String, Long> timeMap = channelMap.get(channel);
        if (timeMap == null) {
            channelMap.put(channel, timeMap = new LinkedHashMap<String, Long>() {
                protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
                    return eldest.getValue().longValue() + dupTime < System.nanoTime();
                }
            });
        }
        return timeMap;
    }

    private Map<String, Long> getUserTimeMap(final User user, final long dupTime) {
        Map<String, Long> timeMap = userMap.get(user);
        if (timeMap == null) {
            userMap.put(user, timeMap = new LinkedHashMap<String, Long>() {
                protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
                    return eldest.getValue().longValue() + dupTime < System.nanoTime();
                }
            });
        }
        return timeMap;
    }

    private static List<String> getIssueKeys(final String message) {
        Matcher matcher = YOUTRACK_KEY.matcher(message);
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

    void processKeys(GenericMessageEvent<JBossBot> event, List<String> keys) throws BackingStoreException {
        final Preferences youtrackNode = event.getBot().getPrefNode().node("youtrack");
        final Set<String> ignored = new HashSet<String>(Arrays.asList(youtrackNode.get("ignored", "JSR").split(",\\s*")));
        final Preferences projectsNode = youtrackNode.node("projects");
        String project;
        Preferences projectNode;
        String url;
        for (String key : keys) {
            project = projectFor(key);
            if (ignored.contains(key)) {
                continue;
            }
            if (projectsNode.nodeExists(project)) {
                projectNode = projectsNode.node(project);
            } else {
                projectNode = youtrackNode.node("default");
            }
            url = projectNode.get("url", null);
            if (url == null) {
                continue;
            }
            if (! url.endsWith("/")) {
                url += "/";
            }
            final IssueInfo issueInfo = lookup(url, key);
            if (issueInfo != null) printIssue("youtrack", event, issueInfo);
        }
    }

    void printIssue(final String prefix, final GenericMessageEvent<JBossBot> event, final IssueInfo issueInfo) {
        final String key = issueInfo.key;
        final IrcStringBuilder builder = new IrcStringBuilder();
        if (issueInfo.redirect != null) {
            builder.append((char) 2).append(prefix).append((char) 2).append((char) 15).append(' ');
            builder.append('[').append((char) 3).append('3').append(key).append((char) 15).append("] ");
            builder.append((char) 3).append('7').append("Redirected to: ").append((char) 3).append("10").append(issueInfo.redirect).append((char) 15);
        } else {
            builder.append((char) 2).append(prefix).append((char) 2).append((char) 15).append(' ');
            builder.append('[').append((char) 3).append('3').append(key).append((char) 15).append("] ");
            builder.append(issueInfo.summary);
            builder.append(" [").append((char)3).append("10").append(issueInfo.status).append((char) 15).append(',');
            builder.append((char)3).append('7').append(' ').append(issueInfo.priority).append((char) 15).append(',');
            builder.append((char) 3).append('6').append(' ').append(issueInfo.assignee).append((char) 15).append("] ");
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

        IssueInfo(final String key, final String summary, final String status, final String priority, final String assignee, final String link, final String redirect) {
            this.key = key;
            this.summary = summary;
            this.status = status;
            this.priority = priority;
            this.assignee = assignee;
            this.link = link;
            this.redirect = redirect;
        }
    }

    private IssueInfo lookup(final String urlPrefix, final String key) {
        try {
            final URL url = new URL(urlPrefix + "rest/issue/" + key);
            final URI userUri = new URI(urlPrefix + "/issue/" + key).normalize();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 301 || code == 302 || code == 303) {
                        return new IssueInfo(key, null, null, null, null, null, conn.getHeaderField("Location"));
                    }
                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return null;
                }
                final InputStream is = conn.getInputStream();
                try {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader, userUri.toString());
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

    private static IssueInfo parseDocument(XMLStreamReader reader, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    return parseRootElement(reader, link);
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.ISSUE) {
                        return null;
                    }
                    return parseIssueContents(reader, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static IssueInfo parseRootElement(final XMLStreamReader reader, String link) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.ISSUE) {
                        return null;
                    }
                    return parseIssueContents(reader, link);
                }
                default: {
                    // ignore
                    break;
                }
            }
        }
        return null;
    }

    private static IssueInfo parseIssueContents(final XMLStreamReader reader, String link) throws XMLStreamException {
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
                    return new IssueInfo(key, summary, status + " " + kind, priority, assignee, link, null);
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
}
