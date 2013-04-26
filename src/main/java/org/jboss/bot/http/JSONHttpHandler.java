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

package org.jboss.bot.http;

import com.zwitserloot.json.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class JSONHttpHandler implements HttpHandler {

    public final void handle(final HttpExchange httpExchange) throws IOException {
        final Headers requestHeaders = httpExchange.getRequestHeaders();

        System.out.println("Request: " + httpExchange.getRequestURI().toString());

        for (String key : requestHeaders.keySet()) {
            final List<String> list = requestHeaders.get(key);
            if (list != null) for (String value : list) {
                System.out.println("Header: " + key + "=" + value);
            }
        }

        final Headers queryParams = new Headers();

        final URI uri = httpExchange.getRequestURI();
        final String rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            final String[] parts = rawQuery.split("[&]");
            if (parts != null) for (String part : parts) {
                int idx = part.indexOf('=');
                String key, value;
                if (idx == -1) {
                    key = part;
                    value = null;
                    queryParams.add(key, "");
                } else {
                    key = part.substring(0, idx);
                    value = part.substring(idx + 1);
                    queryParams.add(key, value);
                }
            }
        }

        boolean xlate =  "application/x-www-form-urlencoded".equals(requestHeaders.getFirst("Content-type"));
        final InputStream requestBody = httpExchange.getRequestBody();
        try {
            final InputStreamReader rawReader = new InputStreamReader(requestBody, "UTF-8");
            StringBuilder b = new StringBuilder();
            int res;
            char[] buf = new char[256];
            while ((res = rawReader.read(buf)) != -1) {
                b.append(buf, 0, res);
            }
            httpExchange.sendResponseHeaders(200, -1L);
            String s = b.toString();
            if (xlate) {
                String p;
                int sidx = s.indexOf("payload=");
                if (sidx == -1) {
                    System.out.println("No payload");
                    return;
                }
                int eidx = s.indexOf('&', sidx);
                p = eidx == -1 ? s.substring(sidx + 8) : s.substring(sidx + 8, eidx);
                s = URLDecoder.decode(p, "UTF-8");
            }
            JSON json = JSON.parse(s);
            handle(requestHeaders, queryParams, uri, json);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } finally {
            requestBody.close();
            httpExchange.close();
        }
    }

    public abstract void handle(Headers requestHeaders, Headers queryParams, URI uri, JSON json) throws IOException;
}
