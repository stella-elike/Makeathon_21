package com.example.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    // Fake "database" so this demo has no external dependencies
    private static final Map<String, Map<String, Object>> CUSTOMERS = Map.of(
            "1001", Map.of("id", "1001", "name", "Asha Rao", "tier", "GOLD"),
            "1002", Map.of("id", "1002", "name", "Vikram Shah", "tier", "SILVER"),
            "1003", Map.of("id", "1003", "name", "Priya Menon", "tier", "STANDARD")
    );

    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) {
        log.info("Fetching customer {}", id);

        // Simulate occasional slowness so you have something interesting on a latency chart
        simulateLatency();

        Map<String, Object> customer = CUSTOMERS.get(id);
        if (customer == null) {
            log.warn("Customer {} not found", id);
            return ResponseEntity.status(404).body(Map.of("error", "customer not found", "id", id));
        }
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    private void simulateLatency() {
        try {
            int delayMs = ThreadLocalRandom.current().nextInt(0, 300);
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
