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

import com.zwitserloot.json.JSON;
import java.io.IOException;
import java.util.Map;
import org.jboss.bot.AbstractJSONServlet;
import org.jboss.bot.JBossBot;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class JiraHttpHandler extends AbstractJSONServlet {
    private final JBossBot bot;
    private final JiraMessageHandler messageHandler;

    public JiraHttpHandler(final JBossBot bot, final JiraMessageHandler messageHandler) {
        this.bot = bot;
        this.messageHandler = messageHandler;
    }

    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if ("Atlassian-Webhooks-Plugin".equals(req.getHeader("User-agent"))) {
            super.doPost(req, resp);
        }
    }

    protected void handleRequest(final HttpServletRequest req, final HttpServletResponse resp, final Map<String, String> queryParams, final JSON payload) throws IOException {
        try {
//            System.out.println(payload.toJSON());
            messageHandler.createdNote(bot, payload.get("issue").get("key").asString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
