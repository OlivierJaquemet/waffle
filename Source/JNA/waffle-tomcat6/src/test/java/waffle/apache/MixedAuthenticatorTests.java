/**
 * Waffle (https://github.com/dblock/waffle)
 *
 * Copyright (c) 2010 - 2016 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors: Application Security, Inc.
 */
package waffle.apache;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.LoginConfig;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.BaseEncoding;
import com.sun.jna.platform.win32.Sspi;
import com.sun.jna.platform.win32.Sspi.SecBufferDesc;

import mockit.Mocked;
import waffle.apache.catalina.SimpleHttpRequest;
import waffle.apache.catalina.SimpleHttpResponse;
import waffle.mock.MockWindowsAuthProvider;
import waffle.windows.auth.IWindowsCredentialsHandle;
import waffle.windows.auth.impl.WindowsAccountImpl;
import waffle.windows.auth.impl.WindowsCredentialsHandleImpl;
import waffle.windows.auth.impl.WindowsSecurityContextImpl;

/**
 * Waffle Tomcat Mixed Authenticator Tests.
 * 
 * @author dblock[at]dblock[dot]org
 */
public class MixedAuthenticatorTests {

    /** The authenticator. */
    private MixedAuthenticator authenticator;

    @Mocked
    Context                    context;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        this.authenticator = new MixedAuthenticator();
        this.authenticator.setContainer(this.context);
        this.authenticator.start();
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        this.authenticator.stop();
    }

    /**
     * Test challenge get.
     */
    @Test
    public void testChallengeGET() {
        final SimpleHttpRequest request = new SimpleHttpRequest();
        request.setMethod("GET");
        request.setQueryString("j_negotiate_check");
        final SimpleHttpResponse response = new SimpleHttpResponse();
        this.authenticator.authenticate(request, response, null);
        final String[] wwwAuthenticates = response.getHeaderValues("WWW-Authenticate");
        Assert.assertNotNull(wwwAuthenticates);
        Assert.assertEquals(2, wwwAuthenticates.length);
        Assert.assertEquals("Negotiate", wwwAuthenticates[0]);
        Assert.assertEquals("NTLM", wwwAuthenticates[1]);
        Assert.assertEquals("close", response.getHeader("Connection"));
        Assert.assertEquals(2, response.getHeaderNames().length);
        Assert.assertEquals(401, response.getStatus());
    }

    /**
     * Test challenge post.
     */
    @Test
    public void testChallengePOST() {
        final String securityPackage = "Negotiate";
        IWindowsCredentialsHandle clientCredentials = null;
        WindowsSecurityContextImpl clientContext = null;
        try {
            // client credentials handle
            clientCredentials = WindowsCredentialsHandleImpl.getCurrent(securityPackage);
            clientCredentials.initialize();
            // initial client security context
            clientContext = new WindowsSecurityContextImpl();
            clientContext.setPrincipalName(WindowsAccountImpl.getCurrentUsername());
            clientContext.setCredentialsHandle(clientCredentials);
            clientContext.setSecurityPackage(securityPackage);
            clientContext.initialize(null, null, WindowsAccountImpl.getCurrentUsername());
            final SimpleHttpRequest request = new SimpleHttpRequest();
            request.setQueryString("j_negotiate_check");
            request.setMethod("POST");
            request.setContentLength(0);
            final String clientToken = BaseEncoding.base64().encode(clientContext.getToken());
            request.addHeader("Authorization", securityPackage + " " + clientToken);
            final SimpleHttpResponse response = new SimpleHttpResponse();
            this.authenticator.authenticate(request, response, null);
            Assert.assertTrue(response.getHeader("WWW-Authenticate").startsWith(securityPackage + " "));
            Assert.assertEquals("keep-alive", response.getHeader("Connection"));
            Assert.assertEquals(2, response.getHeaderNames().length);
            Assert.assertEquals(401, response.getStatus());
        } finally {
            if (clientContext != null) {
                clientContext.dispose();
            }
            if (clientCredentials != null) {
                clientCredentials.dispose();
            }
        }
    }

    /**
     * Test get.
     */
    @Test
    public void testGet() {
        final LoginConfig loginConfig = new LoginConfig();
        loginConfig.setErrorPage("error.html");
        loginConfig.setLoginPage("login.html");
        final SimpleHttpRequest request = new SimpleHttpRequest();
        final SimpleHttpResponse response = new SimpleHttpResponse();
        Assert.assertFalse(this.authenticator.authenticate(request, response, loginConfig));
    }

    /**
     * Test get info.
     */
    @Test
    public void testGetInfo() {
        Assertions.assertThat(this.authenticator.getInfo().length()).isGreaterThan(0);
    }

    /**
     * Test negotiate.
     */
    @Test
    public void testNegotiate() {
        final String securityPackage = "Negotiate";
        IWindowsCredentialsHandle clientCredentials = null;
        WindowsSecurityContextImpl clientContext = null;
        try {
            // client credentials handle
            clientCredentials = WindowsCredentialsHandleImpl.getCurrent(securityPackage);
            clientCredentials.initialize();
            // initial client security context
            clientContext = new WindowsSecurityContextImpl();
            clientContext.setPrincipalName(WindowsAccountImpl.getCurrentUsername());
            clientContext.setCredentialsHandle(clientCredentials);
            clientContext.setSecurityPackage(securityPackage);
            clientContext.initialize(null, null, WindowsAccountImpl.getCurrentUsername());
            // negotiate
            boolean authenticated = false;
            final SimpleHttpRequest request = new SimpleHttpRequest();
            request.setQueryString("j_negotiate_check");
            String clientToken;
            while (true) {
                clientToken = BaseEncoding.base64().encode(clientContext.getToken());
                request.addHeader("Authorization", securityPackage + " " + clientToken);

                final SimpleHttpResponse response = new SimpleHttpResponse();
                authenticated = this.authenticator.authenticate(request, response, null);

                if (authenticated) {
                    Assertions.assertThat(response.getHeaderNames().length).isGreaterThanOrEqualTo(0);
                    break;
                }

                Assert.assertTrue(response.getHeader("WWW-Authenticate").startsWith(securityPackage + " "));
                Assert.assertEquals("keep-alive", response.getHeader("Connection"));
                Assert.assertEquals(2, response.getHeaderNames().length);
                Assert.assertEquals(401, response.getStatus());
                final String continueToken = response.getHeader("WWW-Authenticate").substring(
                        securityPackage.length() + 1);
                final byte[] continueTokenBytes = BaseEncoding.base64().decode(continueToken);
                Assertions.assertThat(continueTokenBytes.length).isGreaterThan(0);
                final SecBufferDesc continueTokenBuffer = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, continueTokenBytes);
                clientContext.initialize(clientContext.getHandle(), continueTokenBuffer,
                        WindowsAccountImpl.getCurrentUsername());
            }
            Assert.assertTrue(authenticated);
        } finally {
            if (clientContext != null) {
                clientContext.dispose();
            }
            if (clientCredentials != null) {
                clientCredentials.dispose();
            }
        }
    }

    /**
     * Test post security check.
     */
    @Test
    public void testPostSecurityCheck() {
        final LoginConfig loginConfig = new LoginConfig();
        loginConfig.setErrorPage("error.html");
        loginConfig.setLoginPage("login.html");
        final SimpleHttpRequest request = new SimpleHttpRequest();
        request.setQueryString("j_security_check");
        request.addParameter("j_username", "username");
        request.addParameter("j_password", "password");
        final SimpleHttpResponse response = new SimpleHttpResponse();
        Assert.assertFalse(this.authenticator.authenticate(request, response, loginConfig));
    }

    /**
     * Test security check parameters.
     */
    @Test
    public void testSecurityCheckParameters() {
        this.authenticator.setAuth(new MockWindowsAuthProvider());
        final LoginConfig loginConfig = new LoginConfig();
        loginConfig.setErrorPage("error.html");
        loginConfig.setLoginPage("login.html");
        final SimpleHttpRequest request = new SimpleHttpRequest();
        request.addParameter("j_security_check", "");
        request.addParameter("j_username", WindowsAccountImpl.getCurrentUsername());
        request.addParameter("j_password", "");
        final SimpleHttpResponse response = new SimpleHttpResponse();
        Assert.assertTrue(this.authenticator.authenticate(request, response, loginConfig));
    }

    /**
     * Test security check query string.
     */
    @Test
    public void testSecurityCheckQueryString() {
        this.authenticator.setAuth(new MockWindowsAuthProvider());
        final LoginConfig loginConfig = new LoginConfig();
        loginConfig.setErrorPage("error.html");
        loginConfig.setLoginPage("login.html");
        final SimpleHttpRequest request = new SimpleHttpRequest();
        request.setQueryString("j_security_check");
        request.addParameter("j_username", WindowsAccountImpl.getCurrentUsername());
        request.addParameter("j_password", "");
        final SimpleHttpResponse response = new SimpleHttpResponse();
        Assert.assertTrue(this.authenticator.authenticate(request, response, loginConfig));
    }
}
