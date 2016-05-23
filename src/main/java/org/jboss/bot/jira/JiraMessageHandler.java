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

package org.jboss.bot.jira;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flurg.thimbot.Priority;
import com.flurg.thimbot.event.ChannelActionEvent;
import com.flurg.thimbot.event.ChannelEvent;
import com.flurg.thimbot.event.ChannelMessageEvent;
import com.flurg.thimbot.event.CommonEvent;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.FromUserEvent;
import com.flurg.thimbot.event.HandlerKey;
import com.flurg.thimbot.event.MessageRespondableEvent;
import com.flurg.thimbot.event.MultiTargetEvent;
import com.flurg.thimbot.event.OutboundActionEvent;
import com.flurg.thimbot.event.OutboundMessageEvent;
import com.flurg.thimbot.event.PrivateActionEvent;
import com.flurg.thimbot.event.PrivateMessageEvent;
import com.flurg.thimbot.event.TextEvent;
import com.zwitserloot.json.JSON;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.bot.JSONServletUtil;
import org.jboss.bot.http.HttpRequestEvent;
import org.jboss.bot.url.AbstractURLEvent;
import org.jboss.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class JiraMessageHandler extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.jira");

    private static final Pattern JIRA_KEY = Pattern.compile("\\b([A-Z]{2}[A-Z0-9]*)-\\d+");

    private final ConcurrentMap<String, Map<String, CommonEvent>> events = new ConcurrentHashMap<String, Map<String, CommonEvent>>();
    private final HandlerKey<RecursionState> handlerKey = new HandlerKey<RecursionState>();

    private final JBossBot bot;

    public JiraMessageHandler(final JBossBot bot) {
        this.bot = bot;
    }

    static final class Key {
        private final String server;
        private final String key;

        Key(final String server, final String key) {
            this.server = server;
            this.key = key;
        }

        public String getServer() {
            return server;
        }

        public String getKey() {
            return key;
        }

        public boolean equals(final Object obj) {
            return obj instanceof Key && key.equals(((Key) obj).key) && server.equals(((Key) obj).server);
        }

        public int hashCode() {
            return key.hashCode() * 31 + server.hashCode();
        }
    }

    public void handleEvent(final EventHandlerContext context, final ChannelMessageEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final ChannelActionEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final PrivateMessageEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final PrivateActionEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final OutboundMessageEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    public void handleEvent(final EventHandlerContext context, final OutboundActionEvent event) throws Exception {
        doHandle(context, event);
        super.handleEvent(context, event);
    }

    private <E extends TextEvent & MessageRespondableEvent> void doHandle(final EventHandlerContext context, final E event) throws Exception {
        Matcher matcher = JIRA_KEY.matcher(event.getText());
        List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        if (keys.isEmpty()) return;
        processKeys(context, event, keys);
    }

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof HttpRequestEvent) {
            final HttpServletRequest req = ((HttpRequestEvent) event).getRequest();
            final HttpServletResponse resp = ((HttpRequestEvent) event).getResponse();
            final String ua = req.getHeader("User-agent").toLowerCase(Locale.US);
            if (! (ua.contains("jira") && ua.contains("atlassian"))) {
                super.handleEvent(context, event);
                return;
            }
            final JSONServletUtil.JSONRequest jsonRequest = JSONServletUtil.readJSONPost(req, resp);
            if (jsonRequest == null) {
                System.out.println("No payload, skipping request");
                return;
            }
            final JSON payload = jsonRequest.getBody();
            try {
                createdNote(bot, context, payload.get("issue").get("key").asString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (!(event instanceof AbstractURLEvent)) {
            super.handleEvent(context, event);
            return;
        }
        final AbstractURLEvent urlEvent = (AbstractURLEvent) event;
        URI uri = urlEvent.getUri();
        final String path = uri.getPath();
        final int ls = path.lastIndexOf('/');
        if (ls == -1) {
            super.handleEvent(context, event);
            return;
        }
        final int sls = path.lastIndexOf('/', ls - 1);
        if (sls == -1) {
            super.handleEvent(context, event);
            return;
        }
        if (! path.substring(sls + 1, ls).equals("browse")) {
            super.handleEvent(context, event);
            return;
        }
        // check whole region
        final Matcher matcher = JIRA_KEY.matcher(path.substring(ls + 1));
        if (! matcher.matches()) {
            super.handleEvent(context, event);
            return;
        }
        final String key = matcher.group();
        processKeys(context, urlEvent, Collections.singletonList(key));
    }

    private static String projectFor(String key) {
        return key.substring(0, key.indexOf('-'));
    }

    void processKeys(final EventHandlerContext context, final MessageRespondableEvent event, List<String> keys) throws BackingStoreException, IOException {
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
            RecursionState state = context.getContextValue(handlerKey);
            if (state == null) context.putContextValue(handlerKey, state = new RecursionState());
            final ArrayList<String> writeTargets = new ArrayList<>();
            if (state.add(key)) {
                // new item
                final ConcurrentMap<String, Map<String, CommonEvent>> events = this.events;
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
                final IssueInfo issueInfo = lookup(url, key);
                if (issueInfo != null) printIssue("jira", event, issueInfo);
            }

        }
    }

    private boolean checkApply(final CommonEvent event, final String key, final ConcurrentMap<String, Map<String, CommonEvent>> events, final String target) {
        Map<String, CommonEvent> subMap = events.get(target);
        if (subMap == null) {
            Map<String, CommonEvent> appearing = events.putIfAbsent(target, subMap = new LinkedHashMap<String, CommonEvent>() {
                protected boolean removeEldestEntry(final Map.Entry<String, CommonEvent> eldest) {
                    return eldest.getValue().getClockTime() < System.currentTimeMillis() - 15000L;
                }
            });
            if (appearing != null) subMap = appearing;
        }
        synchronized (subMap) {
            CommonEvent test = subMap.get(key);
            if (test != null) {
                if (test.getClockTime() < System.currentTimeMillis() - 15000L) {
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

    void printIssue(final String prefix, final MessageRespondableEvent event, final IssueInfo issueInfo) throws IOException {
        final String key = issueInfo.key;
        final IrcStringBuilder builder = new IrcStringBuilder();
        if (issueInfo.redirect != null) {
            return;
        } else {
            builder.b().append(prefix).b().nc().append(' ');
            builder.append('[').fc(3).append(key).nc().append("] ");
            builder.append(issueInfo.summary);
            String status;
            if (issueInfo.resolution == null || issueInfo.resolution.isEmpty()) {
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
        event.sendMessageResponse(builder.toString());
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

    public void createdNote(final JBossBot bot, final EventHandlerContext context, final String key) throws BackingStoreException, IOException, URISyntaxException {
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
        if (channels == null || channels.length == 0 || channels.length == 1 && channels[0].isEmpty()) {
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
        RecursionState state = context.getContextValue(handlerKey);
        if (state == null) context.putContextValue(handlerKey, state = new RecursionState());
        state.add(key);
        final IssueInfo issueInfo = lookup(url, key);
        if (issueInfo != null) {
            printIssue("new jira", new OutboundMessageEvent(bot.getThimBot(), Priority.NORMAL, new HashSet<String>(Arrays.asList(channels)), key), issueInfo);
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
                try (InputStream is = conn.getInputStream()) {
                    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
                    try {
                        return parseDocument(reader);
                    } finally {
                        reader.close();
                    }
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
        String summary = "(none)";
        String key = null;
        String status = "(unknown)";
        String priority = "(unknown)";
        String assignee = "(unknown)";
        String link = null;
        String resolution = null;
        String type = "(unknown)";
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

    private static final class RecursionState {
        Set<String> keys;

        private RecursionState() {
            keys = new HashSet<String>();
        }

        boolean add(String key) {
            return keys.add(key);
        }
    }
}
