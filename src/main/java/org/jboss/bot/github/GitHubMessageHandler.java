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
import com.flurg.thimbot.util.IRCStringBuilder;
import com.flurg.thimbot.util.IRCStringUtil;
import com.zwitserloot.json.JSON;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
                            RecursionState state = context.getContextValue(handlerKey);
                            final String hash = after.substring(0, 9);
                            state.add(new Key(owner, reposName, hash, "commit"));
                            b.append("http://github.com/").append(owner).append('/').append(reposName).append("/commit/");
                            b.append(hash);
                        } else {
                            b.append("http://github.com/").append(owner).append('/').append(reposName).append("/compare/");
                            b.append(before.substring(0, 7)).append("...").append(after.substring(0, 7));
                        }
                        bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        break;
                    }
                    case "pull_request": {
                        final JSON pullRequest = json.get("pull_request");
                        owner = pullRequest.get("base").get("repo").get("owner").get("login").asString();
                        reposName = pullRequest.get("base").get("repo").get("name").asString();
                        final String action = json.get("action").asString();
                        if ("opened".equals(action) || "reopened".equals(action) || "closed".equals(action)) {
                            RecursionState state = context.getContextValue(handlerKey);
                            state.add(new Key(owner, reposName, json.get("number").asString(), "pull_request"));
                            b.clear();
                            b.b().append("git pull req ").append(action).b().nc().append(' ');
                            b.append('[').fc(12).append(reposName).nc().append("] ");
                            b.append('(').fc(7).append(pullRequest.get("state").asString()).nc().append(") ");
                            b.fc(6).append(pullRequest.get("user").get("login").asString()).nc().append(' ');
                            String title = pullRequest.get("title").asString();
                            b.append(title);
                            b.fc(11).append(' ').append(pullRequest.get("html_url").asString());
                            bot.getThimBot().sendMessage(Priority.NORMAL, channels, b.toString());
                        }
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
                        b.append('[').fc(12).append(reposName).nc().append("] ");
                        b.append('(').fc(7).append(issue.get("state").asString()).nc().append(") ");
                        b.fc(6).append(issue.get("user").get("login").asString()).nc().append(' ');
                        String title = issue.get("title").asString();
                        b.append(title);
                        b.fc(11).append(' ').append(issue.get("html_url").asString());
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
            if (authority != null && GH_AUTHORITY.matcher(authority).matches()) {
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
                    b.append(commitMsg.substring(commitMsg.indexOf('\n')));
                    b.fc(14).append("...").nc();
                } else {
                    b.append(commitMsg);
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
                final StringBuilder b = new StringBuilder();
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
                b.append((char) 2).append("git pull req").append((char) 2).append((char) 15).append(' ');
                b.append('[').append((char)3).append("12").append(repos).append((char)15).append("] ");
                b.append('(').append((char)3).append("7").append(json.get("state").asString()).append((char)15).append(") ");
                b.append((char)3).append('6').append(json.get("user").get("login").asString()).append((char) 15).append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.append((char) 3).append("11").append(' ').append(json.get("html_url").asString());
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
                final StringBuilder b = new StringBuilder();
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
                b.append((char) 2).append("git issue").append((char) 2).append((char) 15).append(' ');
                b.append('[').append((char)3).append("12").append(repos).append((char)15).append("] ");
                b.append('(').append((char)3).append("7").append(json.get("state").asString()).append((char)15).append(") ");
                b.append((char)3).append('6').append(json.get("user").get("login").asString()).append((char) 15).append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.append((char) 3).append("11").append(' ').append(json.get("html_url").asString());
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
