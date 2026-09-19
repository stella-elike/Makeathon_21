package com.example.payment;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    // ---- Example of a MANUAL custom metric, sent alongside the javaagent's
    // automatic HTTP/JVM metrics. Useful for business-level numbers that the
    // agent can't infer on its own, e.g. "payments processed" or "revenue".
    private LongCounter paymentsCounter;

    @PostConstruct
    void initMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter("payment-service");
        paymentsCounter = meter
                .counterBuilder("payments.processed")
                .setDescription("Number of payments processed, tagged by result")
                .setUnit("1")
                .build();
    }

    public record PaymentRequest(String customerId, double amount) {}

    @PostMapping
    public ResponseEntity<?> processPayment(@RequestBody PaymentRequest request) {
        log.info("Processing payment of {} for customer {}", request.amount(), request.customerId());

        // Simulate a payment gateway call
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 250));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Simulate ~10% failure rate so error dashboards have something to show
        boolean success = ThreadLocalRandom.current().nextInt(100) >= 10;

        if (!success) {
            paymentsCounter.add(1, Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("result"), "failed"));
            log.error("Payment declined for customer {} amount {}", request.customerId(), request.amount());
            return ResponseEntity.status(402).body(Map.of(
                    "status", "DECLINED",
                    "customerId", request.customerId()
            ));
        }

        paymentsCounter.add(1, Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("result"), "success"));

        String transactionId = UUID.randomUUID().toString();
        log.info("Payment {} approved for customer {}", transactionId, request.customerId());

        return ResponseEntity.ok(Map.of(
                "status", "APPROVED",
                "transactionId", transactionId,
                "customerId", request.customerId(),
                "amount", request.amount()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
