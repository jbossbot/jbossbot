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

package org.jboss.bot.github;

import com.zwitserloot.json.JSON;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JBossBotUtils;
import org.jboss.logging.Logger;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GitHubMessageHandler extends ListenerAdapter<JBossBot> {

    private static final Logger log = Logger.getLogger("org.jboss.bot.github");

    private final ThreadLocal<RecursionState> recursionState = new ThreadLocal<RecursionState>() {
        protected RecursionState initialValue() {
            return new RecursionState();
        }
    };

    public GitHubMessageHandler() {

    }

    private static final Pattern GIT_HUB_PATTERN = Pattern.compile("https?://(?:www\\.)?github\\.com/+([^/]+)/+([^/]+)/+(?:commit|blob)/+([0-9a-fA-F]+)");
    private static final Pattern MANUAL_PATTERN = Pattern.compile("%git\\s+(?:(\\S+)\\s+)?(\\S+)\\s+([0-9a-fA-F]+)\\s*");
    private static final Pattern PULL_REQ_PATTERN1 = Pattern.compile("(?:([-A-Za-z0-9_]+)/)?([-A-Za-z0-9_]+)\\s+#(\\d+)");
    private static final Pattern PULL_REQ_PATTERN2 = Pattern.compile("https?://(?:www\\.)?github\\.com/+([^/]+)/+([^/]+)/+pull/+(\\d+)");

    private boolean doHandle(final GenericMessageEvent<JBossBot> event) {
        final String msg = event.getMessage();
        final RecursionState state = recursionState.get();
        state.enter();
        try {
            Matcher matcher = GIT_HUB_PATTERN.matcher(msg);
            while (matcher.find()) {
                final String org = matcher.group(1);
                final String repos = matcher.group(2);
                final String hash = matcher.group(3);
                lookup(event, state, org, repos, hash);
            }
            matcher = MANUAL_PATTERN.matcher(msg);
            while (matcher.find()) {
                final String org = matcher.group(1);
                final String repos = matcher.group(2);
                final String hash = matcher.group(3);
                lookup(event, state, org == null ? "wildfly" : org, repos, hash);
            }
            matcher = PULL_REQ_PATTERN1.matcher(msg);
            while (matcher.find()) {
                final String org = matcher.group(1);
                final String repos = matcher.group(2);
                final String prId = matcher.group(3);
                lookupPullReq(event, state, org == null ? "wildfly" : org, repos, prId);
            }
            matcher = PULL_REQ_PATTERN2.matcher(msg);
            while (matcher.find()) {
                final String org = matcher.group(1);
                final String repos = matcher.group(2);
                final String prId = matcher.group(3);
                lookupPullReq(event, state, org, repos, prId);
            }
            return false;
        } finally {
            state.exit();
        }
    }

    void enter() {
        recursionState.get().enter();
    }

    void exit() {
        recursionState.get().exit();
    }

    void add(String org, String repos, String hash) {
        recursionState.get().add(String.format("https://api.github.com/repos/%s/%s/commits/%s", org, repos, hash));
    }

    void addPR(String org, String repos, String prId) {
        recursionState.get().add(String.format("https://api.github.com/repos/%s/%s/pulls/%s", org, repos, prId));
    }

    private static void lookup(final GenericMessageEvent<JBossBot> event, final RecursionState state, final String org, final String repos, final String hash) {
        final String urlString = String.format("https://api.github.com/repos/%s/%s/commits/%s", org, repos, hash);
        if (! state.add(urlString)) {
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
                final InputStream is = conn.getInputStream();
                try {
                    final InputStreamReader reader = new InputStreamReader(new BufferedInputStream(is));
                    int c;
                    while ((c = reader.read()) != -1) {
                        b.append((char) c);
                    }
                } finally {
                    is.close();
                }
                final JSON json = JSON.parse(b.toString());
                b.setLength(0);
                b.append((char)2).append("git").append((char)2).append((char)15).append(' ');
                JSON commit = json.get("commit");
                String commitId = json.get("sha").asString();
                commitId = " " + commitId.substring(0, 7) + "..";
                b.append('[').append((char)3).append("12").append(repos).append((char)15).append("]");
                b.append((char)3).append('7').append(commitId).append((char)15).append(' ');
                b.append((char)3).append("6").append(commit.get("author").get("name").asString()).append((char)15).append(' ');
                String commitMsg = commit.get("message").asString();
                if (commitMsg.indexOf('\n') != -1) {
                    commitMsg = commitMsg.substring(0, commitMsg.indexOf('\n')) + ((char)3) + "14" + "..." + ((char)15);
                }
                b.append(commitMsg);
                event.respond(b.toString());
            } finally {
//                        conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    private static void lookupPullReq(final GenericMessageEvent<JBossBot> event, final RecursionState state, final String org, final String repos, final String prId) {
        final String urlString = String.format("https://api.github.com/repos/%s/%s/pulls/%s", org, repos, prId);
        if (! state.add(urlString)) {
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
                final InputStream is = conn.getInputStream();
                try {
                    final InputStreamReader reader = new InputStreamReader(new BufferedInputStream(is));
                    int c;
                    while ((c = reader.read()) != -1) {
                        b.append((char) c);
                    }
                } finally {
                    is.close();
                }
                final JSON json = JSON.parse(b.toString());
                b.setLength(0);
                b.append((char)2).append("git pull req").append((char)2).append((char)15).append(' ');
                b.append('[').append((char)3).append("12").append(repos).append((char)15).append("] ");
                b.append('(').append((char)3).append("7").append(json.get("state").asString()).append((char)15).append(") ");
                b.append((char)3).append('6').append(json.get("user").get("login").asString()).append((char) 15).append(' ');
                String title = json.get("title").asString();
                b.append(title);
                b.append((char)3).append("11").append(' ').append(json.get("html_url").asString());
                event.respond(b.toString());
            } finally {
//                        conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    public void onMessage(final MessageEvent<JBossBot> event) throws Exception {
        doHandle(event);
    }

    public void onAction(final ActionEvent<JBossBot> event) throws Exception {
        doHandle(event);
    }

    public void onPrivateMessage(final PrivateMessageEvent<JBossBot> event) throws Exception {
        doHandle(event);
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
