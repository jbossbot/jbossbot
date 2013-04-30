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
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.bot.AbstractJSONServlet;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GitHubHttpHandler extends AbstractJSONServlet {
    private final JBossBot bot;
    private final GitHubMessageHandler messageHandler;

    public GitHubHttpHandler(final JBossBot bot, final GitHubMessageHandler messageHandler) {
        this.bot = bot;
        this.messageHandler = messageHandler;
    }

    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String userAgent = req.getHeader("User-agent");
        if (userAgent != null && userAgent.toLowerCase(Locale.US).contains("github")) {
            super.doPost(req, resp);
        }
    }

    protected void handleRequest(final HttpServletRequest req, final HttpServletResponse resp, final Map<String, String> queryParams, final JSON json) throws IOException {
        boolean simpleSingle = true;
        int limit = 7;
        final String path = req.getContextPath();
        if (path == null) {
            System.err.println("Request with no path");
            return;
        }
        if (! path.startsWith("/jbossbot/")) {
            System.err.println("Request with wrong path");
            return;
        }
        String channel = path.substring(10);
        while (channel.endsWith("/")) {
            channel = channel.substring(0, channel.length() - 1);
        }

        final IrcStringBuilder b = new IrcStringBuilder();

        final String limitStr = queryParams.get("limit");
        if (limitStr != null && ! limitStr.isEmpty()) {
            limit = Integer.parseInt(limitStr);
        }
        final String event = req.getHeader("X-github-event");
        if ("push".equals(event)) {
            final String reposName = json.get("repository").get("name").asString();
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
                }
                b.append(msg);
                bot.sendMessage(channel, b.toString());
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
                bot.sendMessage(channel, b.toString());
            }
            final String before = json.get("before").asString();
            final String after = json.get("after").asString();
            final String owner = json.get("repository").get("owner").get("name").asString();
            b.clear();
            b.b().append("git").b().nc().append(' ');
            b.append('[').fc(12).append(reposName).nc().append("]");
            b.append(' ').b().append("push ").b().nc().fc(10).append(branch).nc();
            b.append(' ').b().append("URL: ").nc();
            if (commits.size() == 1 && simpleSingle) {
                b.append("http://github.com/").append(owner).append('/').append(reposName).append("/commit/");
                b.append(after.substring(0, 9));
            } else {
                b.append("http://github.com/").append(owner).append('/').append(reposName).append("/compare/");
                b.append(before.substring(0, 7)).append("...").append(after.substring(0, 7));
            }
            messageHandler.enter();
            try {
                messageHandler.add(owner, reposName, after.substring(0, 9));
                bot.sendMessage(channel, b.toString());
            } finally {
                messageHandler.exit();
            }
        } else if ("pull_request".equals(event)) {
//                        System.out.println(json.toJSON());
            final JSON pullRequest = json.get("pull_request");
//                        final String fullName = pullRequest.get("base").get("repo").get("full_name").asString();
//                        final String owner = fullName.substring(0, fullName.indexOf('/'));
            final String owner = pullRequest.get("base").get("repo").get("owner").get("login").asString();
            final String reposName = pullRequest.get("base").get("repo").get("name").asString();
            final String action = json.get("action").asString();
            if ("opened".equals(action) || "reopened".equals(action)) {
                b.clear();
                b.b().append("new git pull req").b().nc().append(' ');
                b.append('[').fc(12).append(reposName).nc().append("] ");
                b.append('(').fc(7).append(pullRequest.get("state").asString()).nc().append(") ");
                b.fc(6).append(pullRequest.get("user").get("login").asString()).nc().append(' ');
                String title = pullRequest.get("title").asString();
                b.append(title);
                b.fc(11).append(' ').append(pullRequest.get("html_url").asString());
                messageHandler.enter();
                try {
                    messageHandler.addPR(owner, reposName, pullRequest.get("number").asString());
                    bot.sendMessage(channel, b.toString());
                } finally {
                    messageHandler.exit();
                }
            }
        }
        
    }
}
