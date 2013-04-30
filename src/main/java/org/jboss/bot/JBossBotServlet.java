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

package org.jboss.bot;

import java.io.IOException;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import org.pircbotx.exception.IrcException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@WebServlet
public final class JBossBotServlet extends HttpServlet {
    private final CopyOnWriteArrayList<HttpServlet> subServlets = new CopyOnWriteArrayList<HttpServlet>();
    private volatile JBossBot bot;

    public void init() throws ServletException {
        final JBossBot bot = new JBossBot();

        for (JBossBotServiceProvider provider : ServiceLoader.load(JBossBotServiceProvider.class, JBossBotServlet.class.getClassLoader())) {
            System.out.println("Registering " + provider);
            provider.register(bot, this);
        }

        try {
            bot.connect();
        } catch (IOException e) {
            throw new ServletException(e);
        } catch (IrcException e) {
            throw new ServletException(e);
        }
        this.bot = bot;
    }

    public void destroy() {
        subServlets.clear();
        final JBossBot bot = this.bot;
        if (bot != null) {
            bot.shutdown(true);
        }
    }

    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        for (HttpServlet subServlet : subServlets) {
            subServlet.service(req, resp);
            if (resp.isCommitted()) {
                return;
            }
        }
        super.service(req, resp);
    }

    public void register(HttpServlet subServlet) {
        subServlets.add(subServlet);
    }

    public JBossBot getBot() {
        return bot;
    }
}
