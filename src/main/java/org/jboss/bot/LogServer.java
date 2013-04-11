/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import static org.jboss.bot.HTMLStreamWriter.Element.*;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class LogServer implements HttpHandler {

    private static String dateKey(long timestamp) {
        return String.format("%1$Y-%1$m-%1$d", Long.valueOf(timestamp));
    }

    private final Map<String, Map<String, List<LogRecord>>> logs = new TreeMap<String, Map<String, List<LogRecord>>>();

    private static final Pattern PATTERN = Pattern.compile("/logs/([^/]+)/(\\d{4}-\\d{2}-\\d{2})");

    public void handle(final HttpExchange exchange) throws IOException {
        try {
            final InputStream requestBody = exchange.getRequestBody();
            while (requestBody.read()!=-1);
            requestBody.close();
            final String path = exchange.getRequestURI().getPath();
            final Matcher matcher = PATTERN.matcher(path);
            if (path.equals("/logs")) {
                writeLogsGreeting(exchange);
            } else if (path.equals("/logs/")) {
                exchange.getResponseHeaders().set("Location", "/logs");
                exchange.sendResponseHeaders(301, -1L);
                exchange.getResponseBody().close();
            } else if (path.equals("/irclogs.css")) {
                writeCss(exchange);
            } else if (matcher.matches()) {
                writeLogs(exchange, matcher.group(1), matcher.group(2));
            } else {
                exchange.sendResponseHeaders(404, -1L);
                exchange.getResponseBody().close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                exchange.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void writeLogs(final HttpExchange exchange, final String channel, final String date) throws IOException, XMLStreamException {
        final String path = exchange.getRequestURI().getPath();
        final Map<String, List<LogRecord>> channelMap = logs.get(channel);
        if (channelMap != null) {
            final List<LogRecord> records = channelMap.get(date);
            if (records != null) {
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, 0L);
                final OutputStream os = exchange.getResponseBody();
                final BufferedOutputStream bos = new BufferedOutputStream(os);
                final OutputStreamWriter writer = new OutputStreamWriter(bos, "UTF-8");
                final HTMLStreamWriter html = new HTMLStreamWriter(writer);
                html.writeStartElement(HTML);
                html.writeStartElement(HEAD);
                html.writeEmptyElement(LINK);
                html.writeAttribute("href", "irclogs.css");
                html.writeAttribute("rel","stylesheet");
                html.writeAttribute("type", "text/css");
                html.writeStartElement(TITLE);
                html.writeCharacters("JBossBot IRC Logs");
                html.writeEndElement(); // title
                html.writeEndElement(); // head
                html.writeStartElement(BODY);
                html.writeStartElement(H1);
                html.writeCharacters("JBossBot IRC Logs: ");
                html.writeCharacters(channel);
                html.writeCharacters(" on ");
                html.writeCharacters(date);
                html.writeEndElement(); // H1
                html.writeEmptyElement(P);
                return;
            }
        }
        exchange.sendResponseHeaders(404, -1L);
        return;
    }

    private void writeCss(final HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/css");
        exchange.sendResponseHeaders(200, 0L);
        final InputStream in = LogServer.class.getResourceAsStream("irclogs.css");
        try {
            final OutputStream out = exchange.getResponseBody();
            byte[] b = new byte[8192];
            for (;;) {
                final int cnt = in.read(b);
                if (cnt == -1) {
                    in.close();
                    out.close();
                    return;
                }
                out.write(b, 0, cnt);
            }
        } finally {
            in.close();
        }
    }

    private void writeLogsGreeting(final HttpExchange exchange) throws IOException, XMLStreamException {
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, 0L);
        final OutputStream os = exchange.getResponseBody();
        final BufferedOutputStream bos = new BufferedOutputStream(os);
        final OutputStreamWriter writer = new OutputStreamWriter(bos, "UTF-8");
        final HTMLStreamWriter html = new HTMLStreamWriter(writer);
        html.writeStartElement(HTML);
        html.writeStartElement(HEAD);
        html.writeEmptyElement(LINK);
        html.writeAttribute("href", "irclogs.css");
        html.writeAttribute("rel","stylesheet");
        html.writeAttribute("type", "text/css");
        html.writeStartElement(TITLE);
        html.writeCharacters("JBossBot IRC Logs");
        html.writeEndElement(); // title
        html.writeEndElement(); // head
        html.writeStartElement(BODY);
        html.writeStartElement(H1);
        html.writeCharacters("JBossBot IRC Logs");
        html.writeEndElement();
        html.writeEmptyElement(P);
        html.writeCharacters("Choose from the following list:");
        html.writeEmptyElement(P);
        html.writeStartElement(UL);
        for (String key : logs.keySet()) {
            html.writeStartElement(LI);
            html.writeCharacters("Channel " + key);
            html.writeStartElement(UL);
            for (String dateKey : logs.get(key).keySet()) {
                html.writeStartElement(LI);
                html.writeStartElement(A);
                try {
                    html.writeAttribute("href", new URI(null, null, "logs/" + key + "/" + dateKey, null).toASCIIString());
                } catch (URISyntaxException e) {
                    // ignore
                }
                html.writeCharacters(key);
                html.writeEndElement(); // a
                html.writeEndElement(); // li
            }
            html.writeEndElement(); // ul
            html.writeEndElement(); // li
        }
        html.writeEndElement(); // ul
        html.writeEndElement(); // body
        html.writeEndElement(); // html
    }

    private static final class LogRecord implements Serializable {
        private static final long serialVersionUID = 6368714665686170578L;

        private final long timestamp;
        private final String line;

        private LogRecord(final long timestamp, final String line) {
            this.timestamp = timestamp;
            this.line = line;
        }

        void format(XMLStreamWriter writer) throws XMLStreamException {
            final StringBuilder b = new StringBuilder();
            final String line = this.line;
            final int len = line.length();

            int color = -1;
            int backgroundColor = -1;

            boolean under = false;
            boolean bold = false;

            int ch;
            int i = 0;
            while (i < len) {
                b.setLength(0);
                if (backgroundColor > 0) {
                    b.append("bc");
                    b.append(backgroundColor % 16);
                }
                if (color > 0) {
                    b.append('c');
                    b.append(color % 16);
                }
                if (under) {
                    if (b.length() > 0) b.append(' ');
                    b.append("xb");
                }
                if (bold) {
                    if (b.length() > 0) b.append(' ');
                    b.append("xu");
                }
                writer.writeStartElement("span");
                if (b.length() > 0) {
                    writer.writeAttribute("class", b.toString());
                }
                b.setLength(0);
                OUT: while (i < len) {
                    ch = line.codePointAt(i);
                    i += Character.charCount(ch);
                    switch (ch) {
                        case 2: {
                            bold ^= bold;
                            break OUT;
                        }
                        case 31: {
                            under ^= under;
                            break OUT;
                        }
                        case 3: {
                            int fg = -1;
                            int bg = -1;
                            if (i >= len) {
                                break OUT;
                            }
                            ch = line.codePointAt(i);
                            i += Character.charCount(ch);
                            if (Character.isDigit(ch)) {
                                fg = Character.digit(ch, 10);
                            } else if (ch != ',') {
                                break OUT;
                            }
                            if (i >= len) {
                                break OUT;
                            }
                            ch = line.codePointAt(i);
                            i += Character.charCount(ch);
                            if (Character.isDigit(ch)) {
                                if (fg < 0) {
                                    bg = Character.digit(ch, 10);
                                } else {
                                    fg *= 10 + Character.digit(ch, 10);
                                }
                            } else if (ch != ',') {
                                if (fg > 0) color = fg;
                                if (bg > 0) backgroundColor = bg;
                                break OUT;
                            }
                            if (fg < 0) {
                                // two ,, in a row
                                break OUT;
                            }
                            ch = line.codePointAt(i);
                            i += Character.charCount(ch);
                            if (Character.isDigit(ch)) {
                                
                            }
                        }
                        default: {
                            b.appendCodePoint(ch);
                        }
                    }
                }
                writer.writeCharacters(b.toString());
                writer.writeEndElement(); // span
            }
        }
    }
}
