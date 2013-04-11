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

import com.zwitserloot.json.JSON;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.regex.Pattern;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.PircBot;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class JBossBot {

    private final PircBotImpl pircBot = new PircBotImpl();
    private final List<String> channels;
    private final Map<Object, MessageHandler> handlers = new LinkedHashMap<Object, MessageHandler>();
    private final Properties properties;
    private final HttpServer server;

    public JBossBot(final HttpServer server) {
        this.server = server;
        final Properties properties = new Properties();
        try {
            properties.load(new InputStreamReader(new FileInputStream("jbossbot.properties"), "UTF-8"));
        } catch (IOException e) {
            // no properties read, life goes on
        }
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            System.out.println("Property: " + entry.getKey() + " -> " + entry.getValue());
        }
        channels = new ArrayList<String>(Arrays.asList(properties.getProperty("channels", "#jboss-dev").split(",")));
        configure(properties);
        this.properties = properties;
    }

    private void configure(final Properties properties) {
        System.out.println("Configuring...");
        try {
            pircBot.setEncoding(properties.getProperty("encoding", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // unlikely; just ignore it in any case
        }
        pircBot.setMessageDelay(0L);
        pircBot.doSetLogin(properties.getProperty("login", "jbossbot"));
        pircBot.doSetName(properties.getProperty("nick", "jbossbot"));
        pircBot.doSetFinger(properties.getProperty("realname", "JBossBot"));
        pircBot.doSetVersion(properties.getProperty("version", "JBoss Bot, accept no substitute!"));
        final JiraMessageHandler jiraMessageHandler = new JiraMessageHandler(properties);
        registerMessageHandler(jiraMessageHandler);
        registerMessageHandler(new YouTrackMessageHandler(properties));
        final GitHubMessageHandler gitHubMessageHandler = new GitHubMessageHandler(properties);
        registerMessageHandler(gitHubMessageHandler);
        registerMessageHandler(new BugzillaMessageHandler(properties));
        registerMessageHandler(new AdminMessageHandler(properties));
        server.createContext("/jbossbot/JIRA", new HttpHandler() {
            public void handle(final HttpExchange httpExchange) throws IOException {
                System.out.println("Request: " + httpExchange.getRequestURI().toString());
                final Headers requestHeaders = httpExchange.getRequestHeaders();
                for (String key : requestHeaders.keySet()) {
                    final List<String> list = requestHeaders.get(key);
                    if (list != null) for (String value : list) {
                        System.out.println("Header: " + key + "=" + value);
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
                    jiraMessageHandler.createdNote(JBossBot.this, json.get("issue").get("key").asString());
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                } finally {
                    requestBody.close();
                    httpExchange.close();
                }
            }
        });
        server.createContext("/jbossbot", new HttpHandler() {
            public void handle(final HttpExchange httpExchange) throws IOException {
                System.out.println("Request: " + httpExchange.getRequestURI().toString());
                final Headers requestHeaders = httpExchange.getRequestHeaders();
                for (String key : requestHeaders.keySet()) {
                    final List<String> list = requestHeaders.get(key);
                    if (list != null) for (String value : list) {
                        System.out.println("Header: " + key + "=" + value);
                    }
                }
                boolean xlate =  "application/x-www-form-urlencoded".equals(requestHeaders.getFirst("Content-type"));
                final URI uri = httpExchange.getRequestURI();
                final String path = uri.getPath();
                if (path == null) {
                    System.err.println("Request with no path");
                    httpExchange.close();
                    return;
                }
                final String rawQuery = uri.getRawQuery();
                boolean simpleSingle = true;
                int limit = -1;
                if (rawQuery != null) {
                    final String[] parts = rawQuery.split("[&]");
                    if (parts != null) for (String part : parts) {
                        int idx = part.indexOf('=');
                        String key, value;
                        if (idx == -1) {
                            key = part;
                            value = null;
                        } else {
                            key = part.substring(0, idx);
                            value = part.substring(idx + 1);
                        }
                        if ("limit".equals(key)) {
                            try {
                                limit = Integer.parseInt(value, 10);
                            } catch (NumberFormatException ignored) {}
                        } else if ("simpleSingle".equals(key)) {
                            simpleSingle = Boolean.parseBoolean(value);
                        }
                    }
                }

                String channel = path.substring(10);
                while (channel.endsWith("/")) {
                    channel = channel.substring(0, channel.length() - 1);
                }
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
                    final String event = requestHeaders.getFirst("X-github-event");
                    if ("push".equals(event)) {
                        final String reposName = json.get("repository").get("name").asString();
                        final String ref = json.get("ref").asString();
                        final int refIdx = ref.lastIndexOf('/');
                        final String branch = refIdx == -1 ? ref : ref.substring(refIdx + 1);
                        final List<JSON> commitsList = json.get("commits").asList();
                        final List<JSON> commits = limit == -1 || limit > commitsList.size() ? commitsList : commitsList.subList(0, limit);
                        for (JSON commit : commits) {
                            b.setLength(0);
                            b.append((char) 2).append("git").append((char) 2).append((char) 15).append(' ');
                            b.append('[').append((char) 3).append("12").append(reposName).append((char) 15).append("]");
                            b.append(' ').append((char) 2).append("push ").append((char) 2).append((char) 15).append((char) 3).append("10").append(branch).append((char) 15);
                            String commitId = commit.get("id").asString();
                            commitId = " " + commitId.substring(0, 7) + "..";
                            b.append((char) 3).append('7').append(commitId).append((char) 15).append(' ');
                            b.append((char) 3).append('6').append(commit.get("author").get("name").asString()).append((char) 15).append(' ');
                            String msg = commit.get("message").asString();
                            if (msg.indexOf('\n') != -1) {
                                msg = msg.substring(0, msg.indexOf('\n')) + ((char) 3) + "14" + "..." + ((char) 15);
                            }
                            b.append(msg);
                            sendMessage(channel, b.toString());
                        }
                        if (commitsList.size() > commits.size()) {
                            final int diff = commitsList.size() - commits.size();
                            b.setLength(0);
                            b.append((char) 2).append("git").append((char) 2).append((char) 15).append(' ');
                            b.append('[').append((char) 3).append("12").append(reposName).append((char) 15).append("]");
                            b.append(' ').append((char) 2).append("push ").append((char) 2).append((char) 15).append((char) 3).append("10").append(branch).append((char) 15);
                            b.append(" (").append(diff).append(" additional commit");
                            if (diff != 1) {
                                b.append('s');
                            }
                            b.append(" not shown)");
                            sendMessage(channel, b.toString());
                        }
                        final String before = json.get("before").asString();
                        final String after = json.get("after").asString();
                        final String owner = json.get("repository").get("owner").get("name").asString();
                        b.setLength(0);
                        b.append((char)2).append("git").append((char)2).append((char)15).append(' ');
                        b.append('[').append((char)3).append("12").append(reposName).append((char)15).append("]");
                        b.append(' ').append((char)2).append("push ").append((char)2).append((char)15).append((char)3).append("10").append(branch).append((char) 15);
                        b.append(' ').append((char)2).append("URL: ").append((char)15);
                        if (commits.size() == 1 && simpleSingle) {
                            b.append("http://github.com/").append(owner).append('/').append(reposName).append("/commit/");
                            b.append(after.substring(0, 9));
                        } else {
                            b.append("http://github.com/").append(owner).append('/').append(reposName).append("/compare/");
                            b.append(before.substring(0, 7)).append("...").append(after.substring(0, 7));
                        }
                        gitHubMessageHandler.enter();
                        try {
                            gitHubMessageHandler.add(owner, reposName, after.substring(0, 9));
                            sendMessage(channel, b.toString());
                        } finally {
                            gitHubMessageHandler.exit();
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
                            b.setLength(0);
                            b.append((char) 2).append("new git pull req").append((char)2).append((char)15).append(' ');
                            b.append('[').append((char) 3).append("12").append(reposName).append((char)15).append("] ");
                            b.append('(').append((char)3).append("7").append(pullRequest.get("state").asString()).append((char) 15).append(") ");
                            b.append((char) 3).append('6').append(pullRequest.get("user").get("login").asString()).append((char) 15).append(' ');
                            String title = pullRequest.get("title").asString();
                            b.append(title);
                            b.append((char)3).append("11").append(' ').append(pullRequest.get("html_url").asString());
                            gitHubMessageHandler.enter();
                            try {
                                gitHubMessageHandler.addPR(owner, reposName, pullRequest.get("number").asString());
                                sendMessage(channel, b.toString());
                            } finally {
                                gitHubMessageHandler.exit();
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                } finally {
                    requestBody.close();
                    httpExchange.close();
                }
            }
        });
        Thread smtpThread = new Thread(new Runnable() {
            private final int smtpPort = Integer.parseInt(properties.getProperty("smtp.port", "25"));

            public void run() {
                try {
                    final ServerSocket serverSocket = new ServerSocket();
                    try {
                        serverSocket.setReuseAddress(true);
                        serverSocket.bind(new InetSocketAddress(smtpPort), 20);
                        for (;;) {
                            final Socket socket;
                            try {
                                socket = serverSocket.accept();
                                try {
                                    socket.setSoTimeout(10000);
                                    final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
                                    final Writer writer = new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1");
                                    String line;
                                    boolean valid1 = false, valid2 = false;
                                    writer.write("220 jbossbot.flurg.com SMTP\r\n");
                                    writer.flush();
                                    OUTER: for (;;) {
                                        line = reader.readLine();
                                        if (line == null) {
                                            break;
                                        } else if (line.startsWith("HELO")) {
                                            writer.write("250 jbossbot.flurg.com\r\n");
                                            writer.flush();
                                        } else if (line.toUpperCase(Locale.US).startsWith("MAIL FROM:")) {
                                            valid1 = line.contains("jira");
                                            writer.write(valid1 ? "250 OK kupopo!\r\n" : "250 OK kupo!\r\n");
                                            writer.flush();
                                        } else if (line.toUpperCase(Locale.US).startsWith("RCPT TO:")) {
                                            valid2 = line.contains("jbossbot@jbossbot.flurg.com");
                                            writer.write(valid2 ? "250 OK kupopo!\r\n" : "250 OK kupo!\r\n");
                                            writer.flush();
                                        } else if (line.toUpperCase(Locale.US).startsWith("QUIT")) {
                                            writer.write("221 Bye\r\n");
                                            writer.flush();
                                            socket.shutdownOutput();
                                            while (reader.readLine() != null);
                                            break;
                                        } else if (line.toUpperCase(Locale.US).startsWith("DATA")) {
                                            writer.write("354 End data with <CR><LF>.<CR><LF>\r\n");
                                            writer.flush();
                                            if (valid1 && valid2) {
                                                for (;;) {
                                                    line = reader.readLine();
                                                    if (line == null) {
                                                        break OUTER;
                                                    } else if (line.equals(".")) {
                                                        break;
                                                    } else if (line.equals("")) {
                                                        // end of headers, body time
                                                        for (;;) {
                                                            line = reader.readLine();
                                                            if (line == null) {
                                                                break OUTER;
                                                            } else if (line.equals(".")) {
                                                                break;
                                                            } else if (line.startsWith("----")) {
                                                                // not a "created" msg
                                                                for (;;) {
                                                                    line = reader.readLine();
                                                                    if (line == null) {
                                                                        break OUTER;
                                                                    } else if (line.equals(".")) {
                                                                        break;
                                                                    }
                                                                }
                                                                break;
                                                            } else if (line.contains("created")) {
                                                                // created msg, now find the key
                                                                int idx;
                                                                String key = null;
                                                                for (;;) {
                                                                    line = reader.readLine();
                                                                    if (line == null) {
                                                                        break OUTER;
                                                                    } else if (line.equals(".")) {
                                                                        break;
                                                                    } else if ((idx = line.indexOf("Key: ")) != -1) {
                                                                        key = line.substring(idx + 5).trim();
                                                                        jiraMessageHandler.createdNote(JBossBot.this, key);
                                                                        for (;;) {
                                                                            line = reader.readLine();
                                                                            if (line == null) {
                                                                                break OUTER;
                                                                            } else if (line.equals(".")) {
                                                                                break;
                                                                            }
                                                                        }
                                                                        break;
                                                                    }
                                                                }
                                                                break;
                                                            }
                                                        }
                                                        break;
                                                    }
                                                }
                                            } else {
                                                for (;;) {
                                                    line = reader.readLine();
                                                    if (line == null) {
                                                        break OUTER;
                                                    } else if (line.equals(".")) {
                                                        break;
                                                    }
                                                }
                                            }
                                            writer.write("250 OK, queued or whatever\r\n");
                                            writer.flush();
                                        } else {
                                            writer.write("502 No good kupo :(\r\n");
                                            writer.flush();
                                        }
                                    }
                                } finally {
                                    safeClose(socket);
                                }
                            } catch (Throwable e) {
                                e.printStackTrace(System.err);
                            }
                        }
                    } finally {
                        safeClose(serverSocket);
                    }
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            }
        });
        smtpThread.setName("SMTP Thread");
        if (false) smtpThread.start();
    }

    static void safeClose(Closeable c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    static void safeClose(Socket c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    static void safeClose(ServerSocket c) {
        try {
            c.close();
        } catch (Throwable ignored) {}
    }

    protected void onConnect() {
        final String password = properties.getProperty("nickserv.password");
        if (password != null) {
            sendMessage("NickServ", "identify " + password);
        }
    }

    protected void onDisconnect() {
        for (;;) {
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
                // xx
            }
            try {
                pircBot.connect("irc.freenode.net");
                return;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IrcException e) {
                e.printStackTrace();
            }
        }
    }

    protected void onNotice(final String sourceNick, final String sourceLogin, final String sourceHostName, final String target, final String notice) {
        if (sourceNick.equals("NickServ") && notice.contains("This nickname is registered.")) {
            final String password = properties.getProperty("nickserv.password");
            if (password != null) {
                sendMessage("NickServ", "identify " + password);
            }
        } else if (sourceNick.equals("NickServ") && notice.contains("You are now identified for")) {
            for (String channel : channels) {
                submit(new JoinChannelAction(channel));
            }
        }
    }

    public Object registerMessageHandler(MessageHandler handler) {
        final Object key = new Object();
        synchronized (handlers) {
            handlers.put(key, handler);
        }
        return key;
    }

    public void removeHandler(Object key) {
        synchronized (handlers) {
            handlers.remove(key);
        }
    }

    protected void onMessage(final String channel, final String sender, final String login, final String hostname, final String msg) {
        final List<MessageHandler> list;
        synchronized (handlers) {
            list = new ArrayList<MessageHandler>(handlers.values());
        }
        for (MessageHandler messageHandler : list) {
            if (messageHandler.onMessage(this, channel, sender, login, hostname, msg)) {
                return;
            }
        }
    }

    protected void onAction(final String sender, final String login, final String hostname, final String target, final String action) {
        final List<MessageHandler> list;
        synchronized (handlers) {
            list = new ArrayList<MessageHandler>(handlers.values());
        }
        for (MessageHandler messageHandler : list) {
            if (messageHandler.onAction(this, sender, login, hostname, target, action)) {
                return;
            }
        }
    }

    protected void onPrivateMessage(final String sender, final String login, final String hostname, final String msg) {
        final List<MessageHandler> list;
        synchronized (handlers) {
            list = new ArrayList<MessageHandler>(handlers.values());
        }
        for (MessageHandler messageHandler : list) {
            if (messageHandler.onPrivateMessage(this, sender, login, hostname, msg)) {
                return;
            }
        }
    }

    private static final int pingModulus = 2;
    private final Queue<Action> actions = new ArrayDeque<Action>();
    private int window = 4;

    public void submit(Action action) {
        synchronized (actions) {
            if (window == 0) {
                actions.add(action);
            } else {
                doAction(action);
            }
        }
    }

    private void doAction(final Action action) {
        final int i = --window;
        action.execute(pircBot);
        if (i % pingModulus == 0) {
            pircBot.sendRawLineViaQueue("PING sync" + (System.nanoTime() & 0x0fffffff));
        }
    }

    public void sendMessage(String target, String message) {
        submit(new MessageAction(target, message));
        final List<MessageHandler> list;
        synchronized (handlers) {
            list = new ArrayList<MessageHandler>(handlers.values());
        }
        for (MessageHandler messageHandler : list) {
            if (messageHandler.onSend(this, target, message)) {
                return;
            }
        }
    }

    public void setVerbose(final boolean verbose) {
        pircBot.setVerbose(verbose);
    }

    public void connect(final String host) throws IOException, IrcException {
        pircBot.connect(host);
    }

    public void partChannel(final String channel) {
        channels.remove(channel);
        submit(new PartChannelAction(channel, "leaving"));
    }

    public void joinChannel(final String channel) {
        channels.add(channel);
        submit(new JoinChannelAction(channel));
    }

    private static final Pattern PONG_PATTERN = Pattern.compile("PONG [^ ]+ :sync\\d+$");

    private final class PircBotImpl extends PircBot {

        protected void onConnect() {
            JBossBot.this.onConnect();
        }

        protected void onDisconnect() {
            JBossBot.this.onDisconnect();
        }

        protected void onMessage(final String s, final String s1, final String s2, final String s3, final String s4) {
            JBossBot.this.onMessage(s, s1, s2, s3, s4);
        }

        protected void onPrivateMessage(final String s, final String s1, final String s2, final String s3) {
            JBossBot.this.onPrivateMessage(s, s1, s2, s3);
        }

        protected void onAction(final String s, final String s1, final String s2, final String s3, final String s4) {
            JBossBot.this.onAction(s, s1, s2, s3, s4);
        }

        protected void onNotice(final String s, final String s1, final String s2, final String s3, final String s4) {
            JBossBot.this.onNotice(s, s1, s2, s3, s4);
        }

        protected void handleLine(final String s) {
            super.handleLine(s);
            if (PONG_PATTERN.matcher(s).find()) {
                synchronized (actions) {
                    window += pingModulus;
                    while (window > 0) {
                        final Action action = actions.poll();
                        if (action != null) {
                            doAction(action);
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        public void doSetLogin(final String login) {
            setLogin(login);
        }

        public void doSetName(final String name) {
            setName(name);
        }

        public void doSetFinger(final String finger) {
            setFinger(finger);
        }

        public void doSetVersion(final String version) {
            setVersion(version);
        }
    }
}
