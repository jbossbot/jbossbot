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

import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.flurg.thimbot.event.HandlerKey;
import com.flurg.thimbot.util.IRCStringBuilder;
import com.zwitserloot.json.JSON;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.bot.url.AbstractURLEvent;
import org.jboss.bot.url.ChannelMessageURLEvent;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GitHubMessageHandler extends EventHandler {

    private static final Logger log = Logger.getLogger("org.jboss.bot.github");

    private final long dupeTime;

    private final ConcurrentMap<String, Map<Key, Event>> events = new ConcurrentHashMap<String, Map<Key, Event>>();
    private HandlerKey<RecursionState> handlerKey = new HandlerKey<RecursionState>();

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
        dupeTime = bot.getPrefNode().getLong("github.cache.duplicate.ms", 10000);
    }

    private static final Pattern GH_AUTHORITY = Pattern.compile("(?:www\\.)?github\\.com");

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof AbstractURLEvent) {
            final AbstractURLEvent<?> inboundUrlEvent = (AbstractURLEvent<?>) event;
            final URI uri = inboundUrlEvent.getUri();
            final String authority = uri.getAuthority();
            if (authority != null && GH_AUTHORITY.matcher(authority).matches()) {
                System.out.println("matched authority");
                final String path = uri.getPath();
                if (path != null) {
                    System.out.println("matched path");
                    RecursionState state = context.getContextValue(handlerKey);
                    if (state == null) context.putContextValue(handlerKey, state = new RecursionState());
                    final String[] parts = path.split("/+");
                    if (parts.length >= 4) {
                        System.out.println("matched four parts");
                        final String obj = parts[3];
                        if (obj.equals("pull")) {
                            System.out.println("matched pull");
                            lookupPullReq(inboundUrlEvent, state, parts[1], parts[2], parts[4]);
                        } else if (obj.equals("commit") || obj.equals("blob")) {
                            System.out.println("matched commit/blob");
                            lookup(inboundUrlEvent, state, parts[1], parts[2], parts[4]);
                        } else {
                            System.out.println("didn't match '" + obj + "'");
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

    private void lookup(final AbstractURLEvent<?> event, final RecursionState state, final String org, final String repos, final String hash) {
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
                    b.append(commitMsg.indexOf('\n'));
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
                b.append((char) 2).append("git pull req").append((char)2).append((char)15).append(' ');
                b.append('[').append((char)3).append("12").append(repos).append((char)15).append("] ");
                b.append('(').append((char)3).append("7").append(json.get("state").asString()).append((char)15).append(") ");
                b.append((char)3).append('6').append(json.get("user").get("login").asString()).append((char) 15).append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.append((char)3).append("11").append(' ').append(json.get("html_url").asString());
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
