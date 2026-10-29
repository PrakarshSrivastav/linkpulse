package com.example.linkpulse.link;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentAliasTest {

    @LocalServerPort
    int port;

    @Autowired
    LinkRepository repository;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAllInBatch();
        redis.delete(redis.keys(LinkCache.KEY_PREFIX + "*"));
    }
    @Test
    void tenParallelRequestsForSameAliasGiveOne201AndNine409s() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/links"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"url": "https://example.com", "alias": "race-alias"}
                        """))
                .build();

        CountDownLatch startSignal = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();

        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();
                    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            startSignal.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
        assertThat(repository.count()).isEqualTo(1);
    }
}