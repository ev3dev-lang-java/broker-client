package io.github.jabrena.broker;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;

public class BrokerClientMultiThreadTests extends BaseTestContainersTest {

    @Tag("complex")
    @Test
    public void given_Client_when_produceAndConsumeInParallelForEvent_then_Ok() {

        var futureRequests = List.of(new Client1(), new Client2()).stream()
            .map(Client::runAsync)
            .collect(toList());

        var results = futureRequests.stream()
            .map(CompletableFuture::join)
            .collect(toList());

        then(results.stream().count()).isEqualTo(2);

        //Check that the last commits is from Client 2 (The consumer)
    }

    private interface Client {

        Logger LOGGER = LoggerFactory.getLogger(Client.class);

        Integer run();

        default CompletableFuture<Integer> runAsync() {

            LOGGER.info("Thread: {}", Thread.currentThread().getName());
            CompletableFuture<Integer> future = CompletableFuture
                .supplyAsync(() -> run())
                .exceptionally(ex -> {
                    LOGGER.error(ex.getLocalizedMessage(), ex);
                    return 0;
                })
                .completeOnTimeout(0, 50, TimeUnit.SECONDS);

            return future;
        }
    }

    private static class Client1 implements Client {

        private BrokerClient defaultBrokerClient;
        private String EVENT = "PING";

        public Client1() {

            //TODO Review how to add dynamic fields in the Config Object
            defaultBrokerClient = new BrokerClient(
                BROKER_TEST_ADDRESS,
                "PINGPONG",
                "PING-NODE",
                "Full Name",
                "email@gmail.com",
                "XXX",
                "YYY");
        }

        public Integer run() {
            LOGGER.info("CLIENT 1");

            sleep(2);
            IntStream.rangeClosed(1, 3)
                .forEach(x -> {
                    sleep(3);
                    defaultBrokerClient.produce(EVENT, "");
                });
            return 1;
        }

        @SneakyThrows
        private void sleep(int seconds) {
            Thread.sleep(seconds * 1000);
        }

    }

    @Slf4j
    private static class Client2 implements Client {

        final int poolingPeriod = 1;

        private BrokerClient defaultBrokerClient;
        private String EVENT = "PING";

        public Client2() {

            //TODO Review how to add dynamic fields in the Config Object
            defaultBrokerClient = new BrokerClient(
                BROKER_TEST_ADDRESS,
                "PINGPONG",
                "PONG-NODE",
                "Full Name",
                "email@gmail.com",
                "XXX",
                "YYY");
        }

        public Integer run() {
            LOGGER.info("CLIENT 2");
            IntStream.rangeClosed(1, 3)
                .forEach(x -> defaultBrokerClient.consume(EVENT, poolingPeriod));
            return 1;
        }

    }
}
