/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.ssh.authentication;

import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * Class providing authentication facility to SSH handler.
 */
public abstract class AuthenticationHandler {

    public abstract String getUsername();

    public abstract org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture authenticate(ClientSession session)
            throws IOException;
}
