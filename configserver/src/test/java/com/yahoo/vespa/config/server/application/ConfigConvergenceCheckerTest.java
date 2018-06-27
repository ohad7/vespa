// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;
import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ConfigConvergenceCheckerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final TenantName tenant = TenantName.from("mytenant");
    private final ApplicationId appId = ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));
    private Application application;
    private ConfigConvergenceChecker checker;
    private Map<URI, Long> currentGeneration;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);

    @Before
    public void setup() {
        Model mockModel = MockModel.createContainer("localhost", 1337);
        application = new Application(mockModel,
                                      new ServerCache(),
                                      3,
                                      false,
                                      Version.fromIntValues(0, 0, 0),
                                      MetricUpdater.createTestUpdater(), appId);
        currentGeneration = new HashMap<>();
        checker = new ConfigConvergenceChecker(
                (client, serviceUri) -> () -> asJson("{\"config\":{\"generation\":"
                                                     + currentGeneration.getOrDefault(serviceUri, 3L)
                                                     + "}}"));
    }

    @Test
    public void service_convergence() throws Exception {
        ServiceResponse serviceResponse = checker.checkService(application,
                                                               "localhost:1337",
                                                               URI.create("http://foo:234/serviceconverge/localhost:1337"), Duration.ofSeconds(5));
        assertEquals(200, serviceResponse.getStatus());
        assertJsonEquals("{\n" +
                         "  \"url\": \"http://foo:234/serviceconverge/localhost:1337\",\n" +
                         "  \"host\": \"localhost:1337\",\n" +
                         "  \"wantedGeneration\": 3,\n" +
                         "  \"converged\": true,\n" +
                         "  \"currentGeneration\": 3\n" +
                         "}",
                         SessionHandlerTest.getRenderedString(serviceResponse));

        ServiceResponse hostMissingResponse = checker.checkService(application,
                                                                   "notPresent:1337",
                                                                   URI.create("http://foo:234/serviceconverge/notPresent:1337"), Duration.ofSeconds(5));
        assertEquals(410, hostMissingResponse.getStatus());
        assertJsonEquals("{\n" +
                         "  \"url\": \"http://foo:234/serviceconverge/notPresent:1337\",\n" +
                         "  \"host\": \"notPresent:1337\",\n" +
                         "  \"wantedGeneration\": 3,\n" +
                         "  \"problem\": \"Host:port (service) no longer part of application, refetch list of services.\"\n" +
                         "}",
                     SessionHandlerTest.getRenderedString(hostMissingResponse));
    }

    @Test
    public void service_list_convergence() throws Exception {
        HttpResponse serviceListResponse = checker.servicesToCheck(application, URI.create("http://foo:234/serviceconverge"), Duration.ofSeconds(5));
        assertEquals(200, serviceListResponse.getStatus());
        assertJsonEquals("{\n" +
                         "  \"services\": [\n" +
                         "    {\n" +
                         "      \"host\": \"localhost\",\n" +
                         "      \"port\": 1337,\n" +
                         "      \"type\": \"container\",\n" +
                         "      \"url\": \"http://foo:234/serviceconverge/localhost:1337\"\n" +
                         "    }\n" +
                         "  ],\n" +
                         "  \"url\": \"http://foo:234/serviceconverge\",\n" +
                         "  \"currentGeneration\": 3,\n" +
                         "  \"wantedGeneration\": 3,\n" +
                         "  \"converged\": true\n" +
                         "}",
                         SessionHandlerTest.getRenderedString(serviceListResponse));

        // Model with two hosts on different generations
        MockModel model = new MockModel(Arrays.asList(
                MockModel.createContainerHost("host1", 1234),
                MockModel.createContainerHost("host2", 1234))
        );
        Application application = new Application(model, new ServerCache(), 4,
                                                  false,
                                                  Version.fromIntValues(0, 0, 0),
                                                  MetricUpdater.createTestUpdater(), appId);
        currentGeneration.put(URI.create("http://host2:1234"), 4L);
        serviceListResponse = checker.servicesToCheck(application, URI.create("http://foo:234/serviceconverge"), Duration.ofSeconds(5));
        assertEquals(200, serviceListResponse.getStatus());
        assertJsonEquals("{\n" +
                         "  \"services\": [\n" +
                         "    {\n" +
                         "      \"host\": \"host1\",\n" +
                         "      \"port\": 1234,\n" +
                         "      \"type\": \"container\",\n" +
                         "      \"url\": \"http://foo:234/serviceconverge/host1:1234\"\n" +
                         "    },\n" +
                         "    {\n" +
                         "      \"host\": \"host2\",\n" +
                         "      \"port\": 1234,\n" +
                         "      \"type\": \"container\",\n" +
                         "      \"url\": \"http://foo:234/serviceconverge/host2:1234\"\n" +
                         "    }\n" +
                         "  ],\n" +
                         "  \"url\": \"http://foo:234/serviceconverge\",\n" +
                         "  \"currentGeneration\": 3,\n" +
                         "  \"wantedGeneration\": 4,\n" +
                         "  \"converged\": false\n" +
                         "}",
                     SessionHandlerTest.getRenderedString(serviceListResponse));
    }

    @Test
    public void service_convergence_timeout() throws Exception {
        URI service = testServer();
        Model mockModel = MockModel.createContainer(service.getHost(), service.getPort());
        application = new Application(mockModel,
                                      new ServerCache(),
                                      3,
                                      false,
                                      Version.fromIntValues(0, 0, 0),
                                      MetricUpdater.createTestUpdater(), appId);
        currentGeneration = new HashMap<>();
        ConfigConvergenceChecker checker = new ConfigConvergenceChecker();

        URI api = testServer().resolve("/serviceconverge");

        wireMock.stubFor(get(urlEqualTo("/state/v1/config"))
                                 .willReturn(aResponse().withFixedDelay((int) Duration.ofSeconds(10).toMillis())));
        ServiceResponse serviceResponse = checker.checkService(application, hostAndPort(service), api, Duration.ofMillis(1));

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        serviceResponse.render(response);

        assertEquals("{\"url\":\"" + api.toString() + "\",\"host\":\"" + hostAndPort(api) +
                     "\",\"wantedGeneration\":3,\"error\":\"java.net.SocketTimeoutException: Read timed out\"}",
                     response.toString());
        assertEquals(404, serviceResponse.getStatus());
    }

    private URI testServer() {
        return URI.create("http://127.0.0.1:" + wireMock.port());
    }

    private static String hostAndPort(URI uri) {
        return uri.getHost() + ":" + uri.getPort();
    }

    private static void assertJsonEquals(String expected, String actual) {
        assertEquals(asJson(expected), asJson(actual));
    }

    private static JsonNode asJson(String data) {
        try {
            return mapper.readTree(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
