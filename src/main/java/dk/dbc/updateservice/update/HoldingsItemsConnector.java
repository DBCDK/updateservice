/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.httpclient.FailSafeHttpClient;
import dk.dbc.httpclient.HttpClient;
import dk.dbc.httpclient.HttpGet;
import net.jodah.failsafe.RetryPolicy;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * EJB to provide access to the HoldingsItems database.
 */
@Stateless
public class HoldingsItemsConnector {
    public static final Tag METHOD_TAG = new Tag("method", "getAgenciesThatHasHoldingsForId");
    private static final Set<Integer> NO_RETRY_RESPONSES = Stream.of(INTERNAL_SERVER_ERROR, BAD_REQUEST).map(Response.Status::getStatusCode).collect(Collectors.toSet());
    private static final RetryPolicy<Response> RETRY_POLICY = new RetryPolicy<Response>()
            .handle(ProcessingException.class)
            .handleResultIf(response -> NO_RETRY_RESPONSES.contains(response.getStatus()))
            .withDelay(Duration.ofSeconds(1))
            .withMaxRetries(2);

    @Inject
    MetricsHandlerBean metricsHandlerBean;

    @Inject
    @ConfigProperty(name = "HOLDINGS_URL")
    private String holdingsServiceUrl;

    @Inject
    @ConfigProperty(name = "HOLDINGS_CONNECT_TIMEOUT", defaultValue = "PT1S")
    private Duration connectTimeout;

    @Inject
    @ConfigProperty(name = "HOLDINGS_READ_TIMEOUT", defaultValue = "PT1S")
    private Duration readTimeout;

    private HttpClient httpClient;

    private static final HoldingsItemsErrorCounterMetrics holdingsItemsErrorCounterMetrics =
            new HoldingsItemsErrorCounterMetrics(Metadata.builder()
                    .withName("update_holdingsitems_error_counter")
                    .withDescription("Number of errors caught in various holdingsitems calls")
                    .withType(MetricType.COUNTER)
                    .withUnit("requests").build());

    private static final HoldingsItemsTimingMetrics holdingsItemsTimingMetrics =
            new HoldingsItemsTimingMetrics(Metadata.builder()
                    .withName("update_holdingsitems_timer")
                    .withDescription("Duration of various various holdingsitems calls")
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withType(MetricType.SIMPLE_TIMER).build());

    protected static final String ERROR_TYPE = "errortype";

    public HoldingsItemsConnector() {
    }

    public HoldingsItemsConnector(MetricsHandlerBean metricsHandlerBean, String holdingsServiceUrl) {
        this.metricsHandlerBean = metricsHandlerBean;
        this.holdingsServiceUrl = holdingsServiceUrl;
        connectTimeout = Duration.ofSeconds(1);
        readTimeout = Duration.ofSeconds(1);
        init();
    }

    @PostConstruct
    public void init() {
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        httpClient = FailSafeHttpClient.create(client, RETRY_POLICY);
    }

    public Set<Integer> getAgenciesThatHasHoldingsFor(MarcRecord marcRecord) {
        MarcRecordReader mm = new MarcRecordReader(marcRecord);
        Stream<String> ids = Stream.concat(Stream.of(mm.getRecordId()), mm.getCentralAliasIds().stream());
        return ids.map(this::getAgenciesThatHasHoldingsForId).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public Set<Integer> getAgenciesThatHasHoldingsForId(String id) {
        Instant start = Instant.now();
        try (Response response = new HttpGet(httpClient).withBaseUrl(holdingsServiceUrl + "/" + id).execute()) {
            if (response.getStatusInfo().toEnum() == Response.Status.NOT_FOUND) {
                return Set.of();
            } else if (response.getStatus() >= 400) {
                throw new InternalServerErrorException("Failed to fetch agencies for record : " + id + ", reason: " + response.getStatusInfo().toEnum());
            }
            HoldingsResponse holdingsResponse = response.readEntity(HoldingsResponse.class);
            return holdingsResponse.agencies;
        } catch (RuntimeException e) {
            metricsHandlerBean.increment(holdingsItemsErrorCounterMetrics, METHOD_TAG, new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
            throw e;
        } finally {
            metricsHandlerBean.update(holdingsItemsTimingMetrics, Duration.between(start, Instant.now()), METHOD_TAG);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HoldingsResponse {
        private Set<Integer> agencies;
        private String trackingId;

        public Set<Integer> getAgencies() {
            return agencies;
        }

        public void setAgencies(Set<Integer> agencies) {
            this.agencies = agencies;
        }

        public String getTrackingId() {
            return trackingId;
        }

        public void setTrackingId(String trackingId) {
            this.trackingId = trackingId;
        }
    }

    private static final class HoldingsItemsErrorCounterMetrics implements CounterMetric {
        private final Metadata metadata;

        public HoldingsItemsErrorCounterMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }


    private static final class HoldingsItemsTimingMetrics implements SimpleTimerMetric {
        private final Metadata metadata;

        public HoldingsItemsTimingMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }
}
