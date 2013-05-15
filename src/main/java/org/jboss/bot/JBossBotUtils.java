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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class JBossBotUtils {

    private static final SSLSocketFactory SSL_SOCKET_FACTORY;
    private static final SocketFactory SOCKET_FACTORY;

    static {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }}, new java.security.SecureRandom());
            final SSLSocketFactory socketFactory = new OpenShiftSSLSocketFactory(sc.getSocketFactory());
            SSL_SOCKET_FACTORY = socketFactory;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
        SOCKET_FACTORY = new OpenShiftSocketFactory(SocketFactory.getDefault());
    }

    private static final HostnameVerifier HOSTNAME_VERIFIER = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private JBossBotUtils() {
    }

    public static URLConnection connectTo(URL url) throws IOException {
        final URLConnection connection = url.openConnection();
        if (connection instanceof HttpURLConnection) {
            final HttpURLConnection httpURLConnection = (HttpURLConnection) connection;
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setConnectTimeout(4000);
            if (connection instanceof HttpsURLConnection) {
                final HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;
                httpsURLConnection.setHostnameVerifier(HOSTNAME_VERIFIER);
                httpsURLConnection.setSSLSocketFactory(SSL_SOCKET_FACTORY);
            }
        }
        return connection;
    }

    public static SSLSocketFactory getSSLSocketFactory() {
        return SSL_SOCKET_FACTORY;
    }

    public static SocketFactory getSocketFactory() {
        return SOCKET_FACTORY;
    }

    static final class OpenShiftSocketFactory extends SocketFactory {
        private final SocketFactory original;

        OpenShiftSocketFactory(final SocketFactory original) {
            this.original = original;
        }

        public Socket createSocket(final String host, final int port) throws IOException {
            return original.createSocket(host, port);
        }

        public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            if (localPort == 0) {
                return original.createSocket(host, port);
            } else {
                return original.createSocket(host, port, localHost, localPort);
            }
        }

        public Socket createSocket(final InetAddress host, final int port) throws IOException {
            return original.createSocket(host, port);
        }

        public Socket createSocket(final InetAddress host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            if (localPort == 0) {
                return original.createSocket(host, port);
            } else {
                return original.createSocket(host, port, localHost, localPort);
            }
        }
    }

    static final class OpenShiftSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory original;

        OpenShiftSSLSocketFactory(final SSLSocketFactory original) {
            this.original = original;
        }

        public String[] getDefaultCipherSuites() {
            return original.getDefaultCipherSuites();
        }

        public String[] getSupportedCipherSuites() {
            return original.getSupportedCipherSuites();
        }

        public Socket createSocket(final String host, final int port) throws IOException {
            return original.createSocket(host, port);
        }

        public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            if (localPort == 0) {
                return original.createSocket(host, port);
            } else {
                return original.createSocket(host, port, localHost, localPort);
            }
        }

        public Socket createSocket(final InetAddress host, final int port) throws IOException {
            return original.createSocket(host, port);
        }

        public Socket createSocket(final InetAddress host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            if (localPort == 0) {
                return original.createSocket(host, port);
            } else {
                return original.createSocket(host, port, localHost, localPort);
            }
        }

        public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose) throws IOException {
            return original.createSocket(s, host, port, autoClose);
        }
    }
}
