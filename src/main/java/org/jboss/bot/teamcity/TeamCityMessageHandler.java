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

package org.jboss.bot.teamcity;

import java.util.Arrays;
import java.util.HashSet;

import com.flurg.thimbot.Priority;
import com.flurg.thimbot.event.Event;
import com.flurg.thimbot.event.EventHandler;
import com.flurg.thimbot.event.EventHandlerContext;
import com.zwitserloot.json.JSON;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jboss.bot.IrcStringBuilder;
import org.jboss.bot.JBossBot;
import org.jboss.bot.JSONServletUtil;
import org.jboss.bot.http.HttpRequestEvent;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TeamCityMessageHandler extends EventHandler {

    private final JBossBot bot;

    public TeamCityMessageHandler(final JBossBot bot) {
        this.bot = bot;
    }

    public void handleEvent(final EventHandlerContext context, final Event event) throws Exception {
        if (event instanceof HttpRequestEvent) {
            final HttpServletRequest req = ((HttpRequestEvent) event).getRequest();
            final HttpServletResponse resp = ((HttpRequestEvent) event).getResponse();
            final String pathInfo = req.getPathInfo();
            if (pathInfo.equals("/jbossbot/teamcity")) {
                final JSONServletUtil.JSONRequest jsonRequest = JSONServletUtil.readJSONPost(req, resp);
                final JSON payload = jsonRequest.getBody();
                final JSON projectIdNode = payload.get("build").get("projectId");
                if (! projectIdNode.exists()) {
                    System.out.println("No project IDs");
                    return;
                }
                final JSON projectNameNode = payload.get("build").get("projectName");
                if (! projectNameNode.exists()) {
                    System.out.println("No project name");
                    return;
                }
                final JSON messageNode = payload.get("build").get("message");
                if (! messageNode.exists()) {
                    System.out.println("No message");
                }
                final JSON branchNameNode = payload.get("build").get("branchName");
                final String projectId = projectIdNode.asString();
                final String unsplitChannels = bot.getPrefNode().node("teamcity").node("projects").node(projectId).get("channels", "");
                if (unsplitChannels == null || unsplitChannels.isEmpty()) {
                    System.out.println("No channels for TeamCity project " + projectId);
                    return;
                }
                final String[] channels = unsplitChannels.split("\\s*,\\s*");
                final IrcStringBuilder b = new IrcStringBuilder();
                b.b().append("teamcity").b().nc().append(" [").fc(9).append(projectNameNode.asString()).nc().append("] ");
                if (branchNameNode.exists()) {
                    b.append('(').fc(10).append(branchNameNode.asString()).nc().append(") ");
                }
                b.append(messageNode.asString());
                bot.getThimBot().sendMessage(Priority.NORMAL, new HashSet<String>(Arrays.asList(channels)), b.toString());
            } else {
                super.handleEvent(context, event);
            }
        } else {
            super.handleEvent(context, event);
        }
    }
}
