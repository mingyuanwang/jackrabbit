/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.j2ee;

import org.apache.log4j.Logger;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.observation.*;
import org.apache.jackrabbit.webdav.jcr.*;
import org.apache.jackrabbit.webdav.jcr.observation.SubscriptionManagerImpl;
import org.apache.jackrabbit.webdav.jcr.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.server.jcr.JCRWebdavServer;
import org.apache.jackrabbit.server.CredentialsProvider;
import org.apache.jackrabbit.server.SessionProviderImpl;
import org.apache.jackrabbit.server.AbstractWebdavServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.jcr.Repository;
import javax.jcr.Credentials;
import javax.jcr.LoginException;

/**
 * JCRWebdavServerServlet provides request/response handling for the JCRWebdavServer.
 */
public class JCRWebdavServerServlet extends AbstractWebdavServlet implements DavConstants {

    /**
     * the default logger
     */
    private static Logger log = Logger.getLogger(JCRWebdavServerServlet.class);

    /**
     * Init parameter specifying the prefix used with the resource path.
     */
    public static final String INIT_PARAM_PREFIX = "resource-path-prefix";

    private String pathPrefix;
    private JCRWebdavServer server;
    private DavResourceFactory resourceFactory;
    private DavLocatorFactory locatorFactory;
    private TxLockManagerImpl txMgr;
    private SubscriptionManager subscriptionMgr;

    /**
     * Initializes the servlet set reads the following parameter from the
     * servlet configuration:
     * <ul>
     * <li>resource-path-prefix: optional prefix for all resources.</li>
     * </ul>
     *
     * @throws ServletException
     */
    public void init() throws ServletException {
        super.init();

	// set resource path prefix
	pathPrefix = getInitParameter(INIT_PARAM_PREFIX);
	log.debug(INIT_PARAM_PREFIX + " = " + pathPrefix);

	Repository repository = RepositoryAccessServlet.getRepository();
	if (repository == null) {
	    throw new ServletException("Repository could not be retrieved. Check config of 'RepositoryAccessServlet'.");
	}
        CredentialsProvider cp = new CredentialsProvider() {
            public Credentials getCredentials(HttpServletRequest request) throws LoginException, ServletException {
                return RepositoryAccessServlet.getCredentialsFromHeader(request.getHeader(DavConstants.HEADER_AUTHORIZATION));
            }
        };

	server = new JCRWebdavServer(repository, new SessionProviderImpl(cp));
        txMgr = new TxLockManagerImpl();
        subscriptionMgr = new SubscriptionManagerImpl();

        // todo: ev. make configurable
        resourceFactory = new DavResourceFactoryImpl(txMgr, subscriptionMgr);
        locatorFactory = new DavLocatorFactoryImpl(pathPrefix);
    }

    /**
     * Returns true if the preconditions are met. This includes validation of
     * {@link WebdavRequest#matchesIfHeader(DavResource) If header} and validation
     * of {@link org.apache.jackrabbit.webdav.transaction.TransactionConstants#HEADER_TRANSACTIONID
     * TransactionId header}. This method will also return false if the requested
     * resource lays within a differenct workspace as is assigned to the repository
     * session attached to the given request.
     *
     * @see AbstractWebdavServlet#isPreconditionValid(WebdavRequest, DavResource)
     */
    protected boolean isPreconditionValid(WebdavRequest request, DavResource resource) {
        // first check matching If header
        if (!request.matchesIfHeader(resource)) {
            return false;
        }

        // test if the requested path matches to the existing session
        // this may occur if the session was retrieved from the cache.
        String wsName = request.getDavSession().getRepositorySession().getWorkspace().getName();
        if (!resource.getLocator().isSameWorkspace(wsName)) {
            return false;
        }

        // make sure, the TransactionId header is valid
        String txId = request.getTransactionId();
        if (txId != null && !txMgr.hasLock(txId, resource)) {
           return false;
        }

        return true;
    }

    /**
     * Returns the <code>DavSessionProvider</code>
     *
     * @return server
     * @see AbstractWebdavServlet#getDavSessionProvider()
     */
    public DavSessionProvider getDavSessionProvider() {
        return server;
    }

    /**
     * Throws <code>UnsupportedOperationException</code>.
     *
     * @see AbstractWebdavServlet#setDavSessionProvider(DavSessionProvider)
     */
    public void setDavSessionProvider(DavSessionProvider davSessionProvider) {
        throw new UnsupportedOperationException("Not implemented. DavSession(s) are provided by the 'JCRWebdavServer'");
    }

    /**
     * Returns the <code>DavLocatorFactory</code>
     *
     * @see AbstractWebdavServlet#getLocatorFactory()
     */
    public DavLocatorFactory getLocatorFactory() {
        if (locatorFactory == null) {
            locatorFactory = new DavLocatorFactoryImpl(pathPrefix);
        }
        return locatorFactory;
    }

    /**
     * Sets the <code>DavLocatorFactory</code>
     *
     * @see AbstractWebdavServlet#setLocatorFactory(DavLocatorFactory)
     */
    public void setLocatorFactory(DavLocatorFactory locatorFactory) {
        this.locatorFactory = locatorFactory;
    }

    /**
     * Returns the <code>DavResourceFactory</code>. 
     *
     * @see AbstractWebdavServlet#getResourceFactory()
     */
    public DavResourceFactory getResourceFactory() {
        if (resourceFactory == null) {
            resourceFactory = new DavResourceFactoryImpl(txMgr, subscriptionMgr);
        }
        return resourceFactory;
    }

    /**
     * Sets the <code>DavResourceFactory</code>.
     *
     * @see AbstractWebdavServlet#setResourceFactory(org.apache.jackrabbit.webdav.DavResourceFactory)
     */
    public void setResourceFactory(DavResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
    }

    /**
     * Returns {@link #DEFAULT_AUTHENTICATE_HEADER}.
     *
     * @return {@link #DEFAULT_AUTHENTICATE_HEADER}.
     */
    public String getAuthenticateHeaderValue() {
        return DEFAULT_AUTHENTICATE_HEADER;
    }
}
