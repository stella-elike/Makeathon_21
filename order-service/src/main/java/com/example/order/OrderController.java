package com.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final RestTemplate restTemplate;

    @Value("${services.customer.url}")
    private String customerServiceUrl;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    public OrderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record OrderRequest(String customerId, String item, double amount) {}

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        log.info("Creating order {} for customer {}", orderId, request.customerId());

        // 1. Validate the customer — call customer-service
        Map<?, ?> customer;
        try {
            customer = restTemplate.getForObject(
                    customerServiceUrl + "/customers/" + request.customerId(), Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Order {} rejected: unknown customer {}", orderId, request.customerId());
            return ResponseEntity.status(404).body(Map.of("error", "unknown customer"));
        }

        // 2. Charge the customer — call payment-service
        Map<String, Object> paymentBody = new HashMap<>();
        paymentBody.put("customerId", request.customerId());
        paymentBody.put("amount", request.amount());

        Map<?, ?> paymentResult;
        try {
            paymentResult = restTemplate.postForObject(
                    paymentServiceUrl + "/payments", paymentBody, Map.class);
        } catch (HttpClientErrorException.PaymentRequired e) {
            log.error("Order {} failed: payment declined for customer {}", orderId, request.customerId());
            return ResponseEntity.status(402).body(Map.of(
                    "orderId", orderId,
                    "status", "PAYMENT_DECLINED"
            ));
        }

        log.info("Order {} completed successfully for customer {}", orderId, request.customerId());

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "CONFIRMED",
                "customer", customer,
                "payment", paymentResult
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
