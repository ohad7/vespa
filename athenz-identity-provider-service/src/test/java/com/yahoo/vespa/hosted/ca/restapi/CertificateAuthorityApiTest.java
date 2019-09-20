// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.ca.CertificateTester;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class CertificateAuthorityApiTest extends ContainerTester {

    @Before
    public void before() {
        setCaCertificateAndKey();
    }

    @Test
    public void register_instance() throws Exception {
        // POST instance registration
        var csr = CertificateTester.createCsr("node1.example.com");
        assertRegistration(new Request("http://localhost:12345/ca/v1/instance/",
                                       instanceRegistrationJson(csr),
                                       Request.Method.POST));

        // POST instance registration with ZTS client
        var ztsClient = new DefaultZtsClient(URI.create("http://localhost:12345/ca/v1/"), SSLContext.getDefault());
        var instanceIdentity = ztsClient.registerInstance(new AthenzService("vespa.external", "provider_prod_us-north-1"),
                                                          new AthenzService("vespa.external", "tenant"),
                                                          "identity document generated by config server",
                                                          csr);
        assertEquals("CN=Vespa CA", instanceIdentity.certificate().getIssuerX500Principal().getName());
    }

    @Test
    public void invalid_register_instance() {
        // POST instance registration with missing fields
        assertResponse(400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Missing required field 'provider'\"}",
                       new Request("http://localhost:12345/ca/v1/instance/",
                                   new byte[0],
                                   Request.Method.POST));

        // POST instance registration without DNS name in CSR
        var csr = CertificateTester.createCsr();
        var request = new Request("http://localhost:12345/ca/v1/instance/",
                                  instanceRegistrationJson(csr),
                                  Request.Method.POST);
        assertResponse(400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"DNS name not found in CSR\"}", request);
    }

    private void setCaCertificateAndKey() {
        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        var caCertificatePem = X509CertificateUtils.toPem(CertificateTester.createCertificate("Vespa CA", keyPair));
        var privateKeyPem = KeyUtils.toPem(keyPair.getPrivate());
        secretStore().setSecret("vespa.external.main.configserver.ca.cert.cert", caCertificatePem)
                     .setSecret("vespa.external.main.configserver.ca.key.key", privateKeyPem);
    }

    private void assertRegistration(Request request) {
        assertResponse(200, (body) -> {
            var slime = SlimeUtils.jsonToSlime(body);
            var root = slime.get();
            assertEquals("provider_prod_us-north-1", root.field("provider").asString());
            assertEquals("tenant", root.field("service").asString());
            assertEquals("node1.example.com", root.field("instanceId").asString());
            var pemEncodedCertificate = root.field("x509Certificate").asString();
            assertTrue("Response contains PEM certificate",
                       pemEncodedCertificate.startsWith("-----BEGIN CERTIFICATE-----") &&
                       pemEncodedCertificate.endsWith("-----END CERTIFICATE-----\n"));
        }, request);
    }

    private static byte[] instanceRegistrationJson(Pkcs10Csr csr) {
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        var json  = "{\n" +
               "  \"provider\": \"provider_prod_us-north-1\",\n" +
               "  \"domain\": \"vespa.external\",\n" +
               "  \"service\": \"tenant\",\n" +
               "  \"attestationData\": \"identity document generated by config server\",\n" +
               "  \"csr\": \"" + csrPem + "\"\n" +
               "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

}
