/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.InternalServerErrorException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dk.dbc.updateservice.update.HoldingsItemsConnector.METHOD_TAG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HoldingsItemsConnectorTest {
    private static final String HOLDINGS_PATH = "/api/agencies-with-holdings";
    MetricsHandlerBean mockedMetricsHandlerBean = mock(MetricsHandlerBean.class);
    protected static WireMockServer wireMockServer = makeWireMockServer();
    private final HoldingsItemsConnector holdingsItemsConnector = new HoldingsItemsConnector(mockedMetricsHandlerBean, getHoldingsUrl(wireMockServer.port()));

    @Test
    void simpleAgencyTest() {
        Set<Integer> agencies = holdingsItemsConnector.getAgenciesThatHasHoldingsForId("51116291");
        Assertions.assertEquals(Set.of(131170, 133190, 133020), agencies);
        verify(mockedMetricsHandlerBean, times(1)).update(any(), any(Duration.class), eq(METHOD_TAG));
    }

    @Test
    void simpleAgencyTestIdNotFound() {
        Set<Integer> agencies = holdingsItemsConnector.getAgenciesThatHasHoldingsForId("0");
        Assertions.assertTrue(agencies.isEmpty());
        verify(mockedMetricsHandlerBean, times(1)).update(any(), any(Duration.class), eq(METHOD_TAG));

    }

    @Test
    void simpleAgencyTestInternalServerError() {
        Assertions.assertThrows(InternalServerErrorException.class, () -> holdingsItemsConnector.getAgenciesThatHasHoldingsForId("-1"));
        verify(mockedMetricsHandlerBean, times(1)).update(any(), any(Duration.class), eq(METHOD_TAG));
        verify(mockedMetricsHandlerBean, times(1)).increment(any(), any());
    }

    @Test
    void agenciesThatHasHoldingsFor_Alias() {
        Set<Integer> resultLibs = Set.of(131170,133190,133020,765700);

        MarcRecord record = new MarcRecord();
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "00136417"));
        record.getFields().add(field);
        MarcField field002 = new MarcField("002", "00");
        field002.getSubfields().add(new MarcSubField("a", "51116291"));
        record.getFields().add(field002);

        assertThat(holdingsItemsConnector.getAgenciesThatHasHoldingsFor(record), is(resultLibs));
        verify(mockedMetricsHandlerBean, times(2)).update(any(), any(Duration.class), eq(METHOD_TAG));
    }

    private static WireMockServer makeWireMockServer() {
        WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        URL holdingItems = HoldingsItemsConnectorTest.class.getClassLoader().getResource("holdingitems");
        try {
            @SuppressWarnings("ConstantConditions")
            Path path = Path.of(holdingItems.toURI());
            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> addStub(wireMockServer, p));
            }
            wireMockServer.stubFor(WireMock.get(HOLDINGS_PATH + "/" + -1).willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
        return wireMockServer;
    }

    private static void addStub(WireMockServer wireMockServer, Path path) {
        try {
            String id = path.toFile().getName().replaceAll("\\.json", "");
            String body = Files.readString(path);
            String req = HOLDINGS_PATH + "/" + id;
            wireMockServer.stubFor(WireMock.get(req).willReturn(
                    ResponseDefinitionBuilder.responseDefinition().withStatus(200).withHeader("content-type", "application/json").withBody(body))
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getHoldingsUrl(int port) {
        return "http://localhost:" + port + HOLDINGS_PATH;
    }
}
