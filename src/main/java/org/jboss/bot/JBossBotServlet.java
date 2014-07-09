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

package org.jboss.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import org.jboss.logging.Logger;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@WebServlet(urlPatterns = {"/*"}, loadOnStartup = 1)
public final class JBossBotServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger("org.jboss.bot");

    private final CopyOnWriteArrayList<HttpServlet> subServlets = new CopyOnWriteArrayList<HttpServlet>();

    @EJB
    private JBossBotEJB ejb;

    public void init() throws ServletException {
        final JBossBot bot = ejb.getBot();

        final ArrayList<JBossBotServiceProvider> providers = new ArrayList<JBossBotServiceProvider>();
        for (JBossBotServiceProvider provider : ServiceLoader.load(JBossBotServiceProvider.class, JBossBotServlet.class.getClassLoader())) {
            providers.add(provider);
        }
        Collections.sort(providers, JBossBotServiceProvider.COMPARATOR);
        for (JBossBotServiceProvider provider : providers) {
            log.debugf("Registering %s", provider);
            provider.register(bot, this);
        }
        try {
            bot.getThimBot().connect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void destroy() {
        subServlets.clear();
    }

    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final Enumeration<String> requestHeaders = req.getHeaderNames();

        log.infof("Request: %s to %s", req.getMethod(), req.getRequestURI());

        while (requestHeaders.hasMoreElements()) {
            String key = requestHeaders.nextElement();
            final Enumeration<String> list = req.getHeaders(key);
            if (list != null) {
                while (list.hasMoreElements()) {
                    String value = list.nextElement();
                    log.infof("Header: %s = %s", key, value);
                }
            }
        }

        for (HttpServlet subServlet : subServlets) {
            subServlet.service(req, resp);
            if (resp.isCommitted()) {
                return;
            }
        }
        super.service(req, resp);
    }

    private static final String content = "<!doctype html>\n<html><body><div style=\"font-size: 300pt; width: 100%; text-align: center;\">&#128513;</div></body></html>\n";
    private static final byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentLength(contentBytes.length);
        resp.setContentType("text/html");
        ServletOutputStream os = resp.getOutputStream();
        os.write(contentBytes);
        os.close();
    }

    public void register(HttpServlet subServlet) {
        subServlets.add(subServlet);
    }

    public JBossBot getBot() {
        return ejb.getBot();
    }
}
