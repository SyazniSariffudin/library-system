package dev.syaz.library.acceptance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Synchronizes independent HTTP requests so acceptance tests exercise actual competing transactions.
 */
public final class ConcurrentBorrow {
    private static final Duration LOAN_DURATION = Duration.ofDays(14);

    private ConcurrentBorrow() {
    }

    public static List<Integer> run(
            String baseUrl, long firstBorrowerId, long secondBorrowerId, long bookId) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try {
            var first = executor.submit(() -> send(client, barrier, baseUrl, firstBorrowerId, bookId));
            var second = executor.submit(() -> send(client, barrier, baseUrl, secondBorrowerId, bookId));
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            return statuses.stream().sorted().toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static int send(
            HttpClient client, CyclicBarrier barrier, String baseUrl, long borrowerId, long bookId) throws Exception {
        URI endpoint = URI.create(baseUrl + "/api/v1/borrowers/" + borrowerId + "/loans");
        Instant dueAt = Instant.now().plus(LOAN_DURATION);
        String requestBody = """
                {"bookId":%d,"dueAt":"%s"}
                """.formatted(bookId, dueAt);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        barrier.await(10, TimeUnit.SECONDS);
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
