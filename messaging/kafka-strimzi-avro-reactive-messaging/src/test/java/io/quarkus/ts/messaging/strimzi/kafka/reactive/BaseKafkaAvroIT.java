package io.quarkus.ts.messaging.strimzi.kafka.reactive;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.SseEventSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.bootstrap.RestService;

public abstract class BaseKafkaAvroIT {

    private static final int TIMEOUT_SEC = 25;
    private static final int EVENTS_AMOUNT = 3;
    private static final int SINGLE = 1;

    private String endpoint;
    private Client client = ClientBuilder.newClient();
    private boolean completed;

    @Test
    public void testAlertMonitorEventStream() throws InterruptedException {
        givenAnApplicationEndpoint(getEndpoint() + "/stock-price/stream");
        whenRequestSomeEvents(EVENTS_AMOUNT, SINGLE);
        thenVerifyAllEventsArrived();
    }

    @Test
    public void batchMustBeGreaterThanSingleEvent() throws InterruptedException {
        givenAnApplicationEndpoint(getEndpoint() + "/stock-price/stream-batch");
        whenRequestSomeEvents(SINGLE, 2); // we expected that a single batch retrieve all events
        thenVerifyAllEventsArrived();
    }

    protected abstract RestService getApp();

    private void givenAnApplicationEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    private void whenRequestSomeEvents(int amount, int expectedItemsPerEvent) throws InterruptedException {
        WebTarget target = client.target(endpoint);
        CountDownLatch latch = new CountDownLatch(amount);

        SseEventSource source = SseEventSource.target(target).build();
        source.register(inboundSseEvent -> {
            if (expectedItemsPerEvent == SINGLE) {
                latch.countDown();
            } else {
                String[] items = inboundSseEvent.readData(String[].class, MediaType.APPLICATION_JSON_TYPE);
                if (items.length >= expectedItemsPerEvent) {
                    latch.countDown();
                }
            }
        });

        source.open();
        completed = latch.await(TIMEOUT_SEC, TimeUnit.SECONDS);
        source.close();
    }

    private void thenVerifyAllEventsArrived() {
        assertTrue(completed, "Not all expected kafka events has been consumed.");
    }

    private String getEndpoint() {
        return getApp().getHost() + ":" + getApp().getPort();
    }
}
