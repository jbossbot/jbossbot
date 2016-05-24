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

package org.jboss.bot.github;

import com.flurg.thimbot.Priority;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.HandlerKey;
import com.flurg.thimbot.event.MessageRespondableEvent;
import com.flurg.thimbot.util.IRCStringBuilder;
import com.flurg.thimbot.util.IRCStringUtil;
import com.zwitserloot.json.JSON;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.bot.JSONServletUtil;
import org.jboss.bot.http.HttpRequestEvent;
import org.jboss.bot.url.AbstractURLEvent;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GitHubMessageHandler extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.github");

    private final JBossBot bot;

    private HandlerKey<RecursionState> handlerKey = new HandlerKey<RecursionState>() {
        public RecursionState initialValue() {
            return new RecursionState();
        }
    };

    private static final ConcurrentMap<String, String> urlMap = new ConcurrentHashMap<>();

    private static final URL gitIo;

    static {
        try {
            gitIo = new URL("http://git.io");
        } catch (MalformedURLException e) {
            throw new IOError(e);
        }
    }

    private static String shorten(String url) {
        final String newUrl = urlMap.get(url);
        if (newUrl != null) {
            return newUrl;
        }
        try {
            final HttpURLConnection connection = (HttpURLConnection) gitIo.openConnection();
            byte[] body = ("url=" + url).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestMethod("POST");
            connection.setDoInput(false);
            connection.setDoOutput(true);
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) { os.write(body); }
            if (connection.getResponseCode() == 201) {
                final String location = connection.getHeaderField("Location");
                if (location != null) {
                    urlMap.putIfAbsent(url, location);
                    return location;
                }
            }
            return url;
        } catch (IOException e) {
            return url;
        }
    }

    static final class Key {
        private final String org;
        private final String repos;
        private final String id;
        private final String kind;

        Key(final String org, final String repos, final String id, final String kind) {
            this.org = org;
            this.repos = repos;
            this.id = id;
            this.kind = kind;
        }

        public String getOrg() {
            return org;
        }

        public String getRepos() {
            return repos;
        }

        public String getId() {
            return id;
        }

        public String getKind() {
            return kind;
        }

        public boolean equals(final Object obj) {
            return obj instanceof Key && equals((Key) obj);
        }

        public boolean equals(final Key obj) {
            return obj != null && id.equals(obj.id) && repos.equals(obj.repos) && kind.equals(obj.kind) && org.equals(obj.org);
        }

        public int hashCode() {
            int hc = id.hashCode();
            hc = 31 * hc + repos.hashCode();
            hc = 31 * hc + kind.hashCode();
            hc = 31 * hc + org.hashCode();
            return hc;
        }
    }

    public GitHubMessageHandler(JBossBot bot) {
        this.bot = bot;
    }

    private static final Pattern GH_AUTHORITY = Pattern.compile("(?:www\\.)?github\\.com");
    private static final Pattern GI_AUTHORITY = Pattern.compile("(?:www\\.)?gh\\.io");

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof HttpRequestEvent) {
            final HttpServletRequest req = ((HttpRequestEvent) event).getRequest();
            if (! req.getMethod().equalsIgnoreCase("POST")) {
                super.handleEvent(context, event);
                return;
            }
            final String gitHubEvent = req.getHeader("X-github-event");
            if (gitHubEvent != null) {
                final HttpServletResponse resp = ((HttpRequestEvent) event).getResponse();
                final JSONServletUtil.JSONRequest jsonRequest = JSONServletUtil.readJSONPost(req, resp);
                final JSON json = jsonRequest.getBody();
                final Map<String, String> queryParams = jsonRequest.getQuery();
                boolean simpleSingle = true;
                int limit = 7;
                final String limitStr = queryParams.get("limit");
                if (limitStr != null && ! limitStr.isEmpty()) {
                    limit = Integer.parseInt(limitStr);
                }
                final IrcStringBuilder b = new IrcStringBuilder();
                final JSON reposNameNode = json.get("repository").get("name");
                if (! reposNameNode.exists()) {
                    System.out.println("No repository name");
                    return;
                }
                String reposName = reposNameNode.asString();
                final JSON ownerNode = json.get("repository").get("owner");
                String owner = ownerNode.get("name").exists() ? ownerNode.get("name").asString() : ownerNode.get("login").exists() ? ownerNode.get("login").asString() : null;
                if (reposName == null || owner == null) {
                    return;
                }
                final boolean learn = bot.getPrefNode().node("github").getBoolean("learn", false);
                final Set<String> channels;
                final String unsplitChannels = bot.getPrefNode().node("github/projects").node(owner).node(reposName).get("channels", "");
                if (unsplitChannels == null || unsplitChannels.isEmpty()) {
                    channels = new HashSet<>(0);
                } else {
                    final String[] chArray = unsplitChannels.split("\\s*,\\s*");
                    if (chArray.length == 0) {
                        channels = new HashSet<>(0);
                    } else {
                        channels = new HashSet<>(Arrays.asList(chArray));
                    }
                }
                if (channels.isEmpty()) {
                    final String wcUnsplitChannels = bot.getPrefNode().node("github/projects").node(owner).node("*").get("channels", "");
                    if (wcUnsplitChannels != null && ! wcUnsplitChannels.isEmpty()) {
                        final String[] chArray = wcUnsplitChannels.split("\\s*,\\s*");
                        if (chArray.length > 0) {
                            channels.addAll(Arrays.asList(chArray));
                        }
                    }
                }
                if (learn) {
                    final String pathInfo = req.getPathInfo();
                    if (pathInfo.startsWith("/jbossbot/")) {
                        String chName = pathInfo.substring(10);
                        while (chName.endsWith("/")) chName = chName.substring(0, chName.length() - 1);
                        if (channels.add(chName) && IRCStringUtil.isChannel(chName)) {
                            Iterator<String> i = new TreeSet<String>(channels).iterator();
                            StringBuilder x = new StringBuilder();
                            if (i.hasNext()) {
                                x.append(i.next());
                                while (i.hasNext()) {
                                    x.append(',').append(i.next());
                                }
                            }
                            bot.getPrefNode().node("github/projects").node(owner).node(reposName).put("channels", x.toString());
                        }
                    }
                }
                if (channels.isEmpty()) {
                    System.out.printf("No channel mapping for %s/%s%n", owner, reposName);
                    return;
                }
                switch (gitHubEvent) {
                    case "push": {
                        final String ref = json.get("ref").asString();
                        final int refIdx = ref.lastIndexOf('/');
                        final String branch = refIdx == -1 ? ref : ref.substring(refIdx + 1);
                        final List<JSON> commitsList = json.get("commits").asList();
                        final List<JSON> commits = limit == -1 || limit > commitsList.size() ? commitsList : commitsList.subList(0, limit);
                        RecursionState state = context.getContextValue(handlerKey);
                        for (JSON commit : commits) {
                            b.clear();
                            b.b().append("git").b().nc().append(' ');
                            b.append('[').fc(12).append(reposName).nc().append("]");
                            b.append(' ').b().append("push ").b().nc().fc(10).append(branch).nc();
                            String commitId = commit.get("id").asString();
                            b.fc(7).append(' ').append(commitId.substring(0, 7)).append("..").nc().append(' ');
                            b.fc(6).append(commit.get("author").get("name").asString()).nc().append(' ');
                            String msg = commit.get("message").asString();
                            if (msg.indexOf('\n') != -1) {
                                b.append(msg.substring(0, msg.indexOf('\n'))).fc(14).append("...").nc();
                            } else {
                                b.append(msg);
                            }
                            b.fc(11).append(' ');
                            final String hash = commitId.substring(0, 9);
                            state.add(new Key(owner, reposName, hash, "commit"));
                            b.append(shorten(String.format("http://github.com/%s/%s/commit/%s", owner, reposName, hash)));
                            bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        }
                        if (commitsList.size() > commits.size()) {
                            final int diff = commitsList.size() - commits.size();
                            b.clear();
                            b.b().append("git").b().nc().append(' ');
                            b.append('[').fc(12).append(reposName).nc().append("]");
                            b.append(' ').b().append("push ").b().nc().fc(10).append(branch).nc();
                            b.append(" (").append(diff).append(" additional commit");
                            if (diff != 1) {
                                b.append('s');
                            }
                            b.append(" not shown)");
                            bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        }
                        final String before = json.get("before").asString();
                        final String after = json.get("after").asString();
                        b.clear();
                        b.b().append("git").b().nc().append(' ');
                        b.append('[').fc(12).append(reposName).nc().append("]");
                        b.append(' ').b().append("push ").b().nc().fc(10).append(branch).nc();
                        b.append(' ').b().append("URL: ").nc();
                        if (commits.size() == 1 && simpleSingle) {
                            final String hash = after.substring(0, 9);
                            state.add(new Key(owner, reposName, hash, "commit"));
                            b.append(shorten(String.format("http://github.com/%s/%s/commit/%s", owner, reposName, hash)));
                        } else {
                            b.append(shorten(String.format("http://github.com/%s/%s/compare/%s...%s", owner, reposName, before.substring(0, 7), after.substring(0, 7))));
                        }
                        bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        break;
                    }
                    case "pull_request": {
                        final JSON pullRequest = json.get("pull_request");
                        owner = pullRequest.get("base").get("repo").get("owner").get("login").asString();
                        reposName = pullRequest.get("base").get("repo").get("name").asString();
                        final String action = json.get("action").asString();
                        RecursionState state = context.getContextValue(handlerKey);
                        state.add(new Key(owner, reposName, json.get("number").asString(), "pull_request"));
                        b.clear();
                        b.b().append("git pull req ").append(action).b().nc().append(' ');
                        if (action.equals("labeled")) {
                            final String name = json.get("label").get("name").asString();
                            b.b().append('+').b().nc().fc(10).append(name).nc().append(' ');
                        } else if (action.equals("unlabeled")) {
                            final String name = json.get("label").get("name").asString();
                            b.b().append('-').b().nc().fc(7).append(name).nc().append(' ');
                        }
                        b.append('[').fc(12).append(reposName).nc().append("] ");
                        b.append('(').fc(7).append(pullRequest.get("state").asString()).nc().append(") ");
                        b.fc(6).append(getNameForUserId(pullRequest.get("user").get("login").asString())).nc().append(' ');
                        String title = pullRequest.get("title").asString();
                        b.append(title);
                        b.fc(11).append(' ').append(shorten(pullRequest.get("html_url").asString()));
                        bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        break;
                    }
                    case "issues": {
                        final JSON issue = json.get("issue");
                        owner = json.get("repository").get("owner").get("login").asString();
                        reposName = json.get("repository").get("name").asString();
                        final String action = json.get("action").asString();
                        RecursionState state = context.getContextValue(handlerKey);
                        state.add(new Key(owner, reposName, issue.get("number").asString(), "issue"));
                        b.clear();
                        b.b().append("git issue ").append(action).b().nc().append(' ');
                        if (action.equals("labeled")) {
                            final String name = json.get("label").get("name").asString();
                            b.b().append('+').b().nc().fc(10).append(name).nc().append(' ');
                        } else if (action.equals("unlabeled")) {
                            final String name = json.get("label").get("name").asString();
                            b.b().append('-').b().nc().fc(7).append(name).nc().append(' ');
                        }
                        b.append('[').fc(12).append(reposName).nc().append("] ");
                        b.append('(').fc(7).append(issue.get("state").asString()).nc().append(") ");
                        b.fc(6).append(getNameForUserId(issue.get("user").get("login").asString())).nc().append(' ');
                        String title = issue.get("title").asString();
                        b.append(title);
                        b.fc(11).append(' ').append(shorten(issue.get("html_url").asString()));
                        bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        break;
                    }
                    default: {
                        System.out.printf("Unknown git event type %s%n", gitHubEvent);
                        return;
                    }
                }
            } else {
                super.handleEvent(context, event);
                return;
            }
        } else if (event instanceof AbstractURLEvent) {
            final AbstractURLEvent<?> inboundUrlEvent = (AbstractURLEvent<?>) event;
            final URI uri = inboundUrlEvent.getUri();
            final String authority = uri.getAuthority();
            if (authority != null) {
                if (GH_AUTHORITY.matcher(authority).matches()) {
                    final String path = uri.getPath();
                    if (path != null) {
                        RecursionState state = context.getContextValue(handlerKey);
                        if (state == null) context.putContextValue(handlerKey, state = new RecursionState());
                        final String[] parts = path.split("/+");
                        if (parts.length >= 5) {
                            final String obj = parts[3];
                            switch (obj) {
                                case "pull":
                                    lookupPullReq(inboundUrlEvent, state, parts[1], parts[2], parts[4]);
                                    break;
                                case "commit":
                                    lookup(inboundUrlEvent, state, parts[1], parts[2], parts[4]);
                                    break;
                                case "issues":
                                    lookupIssue(inboundUrlEvent, state, parts[1], parts[2], parts[4]);
                                    break;
                                default:
                                    System.out.println("didn't match '" + obj + "'");
                                    break;
                            }
                        }
                    }
                    return;
                } else if (GI_AUTHORITY.matcher(authority).matches()) try {
                    // shortened URL
                    String fast = urlMap.get(uri.toString());
                    URI newUri;
                    if (fast != null) {
                        newUri = new URI(fast);
                    } else {
                        final HttpURLConnection urlConnection = (HttpURLConnection) uri.toURL().openConnection();
                        urlConnection.setDoInput(false);
                        urlConnection.setDoOutput(false);
                        urlConnection.setInstanceFollowRedirects(false);
                        final int responseCode = urlConnection.getResponseCode();
                        if (responseCode == 302) {
                            newUri = new URI(urlConnection.getHeaderField("Location"));
                            urlMap.putIfAbsent(uri.toString(), newUri.toString());
                        } else {
                            // ignore
                            super.handleEvent(context, event);
                            return;
                        }
                    }
                    final AbstractURLEvent<? extends MessageRespondableEvent> newEvent = inboundUrlEvent.copyWithNewUri(newUri);
                    context.redispatch(newEvent);
                    return;
                } catch (IOException ignored) {}
            }
        }
        super.handleEvent(context, event);
    }

    void add(RecursionState state, String org, String repos, String hash) {
        state.add(new Key(org, repos, hash, "commit"));
    }

    void addPR(RecursionState state, String org, String repos, String prId) {
        state.add(new Key(org, repos, prId, "pull_request"));
    }

    private static void lookup(final AbstractURLEvent<?> event, final RecursionState state, final String org, final String repos, final String hash) {
        final String urlString = String.format("https://api.github.com/repos/%s/%s/commits/%s", org, repos, hash);
        if (! state.add(new Key(org, repos, hash, "commit"))) {
            // already got it this time round
            return;
        }
        try {
            final URL url = new URL(urlString);
            final HttpURLConnection conn = (HttpURLConnection) JBossBotUtils.connectTo(url);
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return;
                }
                final IRCStringBuilder b = new IRCStringBuilder();
                try (final InputStream is = conn.getInputStream()) {
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        try (InputStreamReader reader = new InputStreamReader(bis)) {
                            int c;
                            while ((c = reader.read()) != -1) {
                                b.append((char) c);
                            }
                        }
                    }
                }
                final JSON json = JSON.parse(b.toString());
                b.setLength(0);
                b.b().append("git").b().nc().append(' ');
                JSON commit = json.get("commit");
                String commitId = json.get("sha").asString();
                commitId = " " + commitId.substring(0, 7) + "..";
                b.append('[').fc(12).append(repos).nc().append("]");
                b.fc(7).append(commitId).nc().append(' ');
                b.fc(6).append(commit.get("author").get("name").asString()).nc().append(' ');
                String commitMsg = commit.get("message").asString();
                if (commitMsg.indexOf('\n') != -1) {
                    b.append(commitMsg.substring(0, commitMsg.indexOf('\n')));
                    b.fc(14).append("...").nc();
                } else {
                    b.append(commitMsg);
                }
                final JSON ownerNode = json.get("repository").get("owner");
                String owner = ownerNode.get("name").exists() ? ownerNode.get("name").asString() : ownerNode.get("login").exists() ? ownerNode.get("login").asString() : null;
                if (owner != null) {
                    final JSON reposNameNode = json.get("repository").get("name");
                    if (reposNameNode.exists()) {
                        String reposName = reposNameNode.asString();
                        b.fc(11).append(' ');
                        b.append(shorten(String.format("http://github.com/%s/%s/commit/%s", owner, reposName, hash)));
                        b.nc();
                    }
                }
                event.sendMessageResponse(b.toString());
            } finally {
//                        conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    private static void lookupPullReq(final AbstractURLEvent<?> event, final RecursionState state, final String org, final String repos, final String prId) {
        final String urlString = String.format("https://api.github.com/repos/%s/%s/pulls/%s", org, repos, prId);
        if (! state.add(new Key(org, repos, prId, "pull_request"))) {
            // already got it this time round
            return;
        }
        try {
            final URL url = new URL(urlString);
            final HttpURLConnection conn = (HttpURLConnection) JBossBotUtils.connectTo(url);
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return;
                }
                final IrcStringBuilder b = new IrcStringBuilder();
                try (InputStream is = conn.getInputStream()) {
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        try (InputStreamReader reader = new InputStreamReader(bis)) {
                            int c;
                            while ((c = reader.read()) != -1) {
                                b.append((char) c);
                            }
                        }
                    }
                }
                final JSON json = JSON.parse(b.toString());
                b.setLength(0);
                b.b().append("git pull req").b().nc().append(' ');
                b.append('[').fc(12).append(repos).nc().append("] ");
                b.append('(').fc(7).append(json.get("state").asString()).nc().append(") ");
                b.b().fc(6).append(getNameForUserId(json.get("user").get("login").asString())).nc().append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.fc(11).append(' ').append(shorten(json.get("html_url").asString()));
                event.sendMessageResponse(b.toString());
            } finally {
//                        conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    private static void lookupIssue(final AbstractURLEvent<?> event, final RecursionState state, final String org, final String repos, final String issueId) {
        final String urlString = String.format("https://api.github.com/repos/%s/%s/issues/%s", org, repos, issueId);
        if (! state.add(new Key(org, repos, issueId, "issue"))) {
            // already got it this time round
            return;
        }
        try {
            final URL url = new URL(urlString);
            final HttpURLConnection conn = (HttpURLConnection) JBossBotUtils.connectTo(url);
            try {
                final int code = conn.getResponseCode();
                if (code != 200) {
                    log.debugf("URL %s returned status %d", url, Integer.valueOf(code));
                    return;
                }
                final IrcStringBuilder b = new IrcStringBuilder();
                try (InputStream is = conn.getInputStream()) {
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        try (InputStreamReader reader = new InputStreamReader(bis)) {
                            int c;
                            while ((c = reader.read()) != -1) {
                                b.append((char) c);
                            }
                        }
                    }
                }
                final JSON json = JSON.parse(b.toString());
                b.setLength(0);
                b.b().append("git issue").b().nc().append(' ');
                b.append('[').fc(12).append(repos).nc().append("] ");
                b.append('(').fc(7).append(json.get("state").asString()).nc().append(") ");
                b.fc(6).append(getNameForUserId(json.get("user").get("login").asString())).nc().append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.fc(11).append(' ').append(shorten(json.get("html_url").asString()));
                event.sendMessageResponse(b.toString());
            } finally {
//                        conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    private static final Map<String, String> LRU_NAMES = Collections.synchronizedMap(new LinkedHashMap<String, String>(16, 0.5f, true) {
        protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
            return size() >= 100;
        }
    });

    private static String getNameForUserId(String userId) {
        String name;
        final Map<String, String> lruNames = LRU_NAMES;
        synchronized (lruNames) {
            name = lruNames.get(userId);
            if (name != null) {
                return name;
            }
            try {
                final URLConnection connection = JBossBotUtils.connectTo(URI.create("https://api.github.com/users/" + userId).toURL());
                final StringBuilder b = new StringBuilder();
                try (InputStream is = connection.getInputStream()) {
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        try (InputStreamReader reader = new InputStreamReader(bis)) {
                            int c;
                            while ((c = reader.read()) != -1) {
                                b.append((char) c);
                            }
                        }
                    }
                }
                final JSON json = JSON.parse(b.toString());
                final String realName = json.get("name").asString();
                if (realName != null && ! realName.isEmpty()) {
                    lruNames.put(userId, realName);
                    return realName;
                } else {
                    lruNames.put(userId, userId);
                    return userId;
                }
            } catch (IOException e) {
                lruNames.put(userId, userId);
                return userId;
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
