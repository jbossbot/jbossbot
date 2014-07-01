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

import static java.lang.Integer.signum;

import java.util.Comparator;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface JBossBotServiceProvider {

    /**
     * Register the provider.
     *
     * @param bot the bot
     * @param servlet the servlet
     */
    void register(JBossBot bot, JBossBotServlet servlet);

    /**
     * Get the priority.  Lower numbers come first, higher numbers come later.
     *
     * @return the priority
     */
    int getPriority();

    Comparator<JBossBotServiceProvider> COMPARATOR = new Comparator<JBossBotServiceProvider>() {
        public int compare(final JBossBotServiceProvider provider1, final JBossBotServiceProvider provider2) {
            int res = signum(provider1.getPriority() - provider2.getPriority());
            if (res == 0) {
                res = provider1.getClass().getName().compareTo(provider2.getClass().getName());
            }
            return res;
        }
    };
}
