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

import static org.jboss.bot.JBossBotUtils.safeClose;

import com.zwitserloot.json.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractJSONServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger("org.jboss.bot");

    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final Map<String, String> queryParams = new HashMap<String, String>();

        final String rawQuery = req.getQueryString();
        if (rawQuery != null) {
            final String[] parts = rawQuery.split("[&]");
            if (parts != null) for (String part : parts) {
                int idx = part.indexOf('=');
                String key, value;
                if (idx == -1) {
                    key = part;
                    value = null;
                    queryParams.put(key, "");
                } else {
                    key = part.substring(0, idx);
                    value = part.substring(idx + 1);
                    queryParams.put(key, value);
                }
            }
        }

        final String contentType = req.getHeader("Content-type");
        boolean xlate =  "application/x-www-form-urlencoded".equals(contentType) || "application/vnd.github.v3+form".equals(contentType);
        final InputStream requestBody = req.getInputStream();
        try {
            final InputStreamReader rawReader = new InputStreamReader(requestBody, "UTF-8");
            StringBuilder b = new StringBuilder();
            int res;
            char[] buf = new char[256];
            while ((res = rawReader.read(buf)) != -1) {
                b.append(buf, 0, res);
            }
            safeClose(requestBody);
            resp.setContentLength(0);
            resp.setStatus(200);
            resp.flushBuffer();
            safeClose(resp.getOutputStream());
            String s = b.toString();
            if (xlate) {
                String p;
                int sidx = s.indexOf("payload=");
                if (sidx == -1) {
                    log.debug("No payload");
                    return;
                }
                int eidx = s.indexOf('&', sidx);
                p = eidx == -1 ? s.substring(sidx + 8) : s.substring(sidx + 8, eidx);
                s = URLDecoder.decode(p, "UTF-8");
            }
            log.debugf("Payload:%n%s", s);
            JSON json = JSON.parse(s);
            handleRequest(req, resp, queryParams, json);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } finally {
            safeClose(requestBody);
        }

    }

    protected abstract void handleRequest(final HttpServletRequest req, HttpServletResponse resp, final Map<String, String> queryParams, JSON payload) throws IOException;
}
