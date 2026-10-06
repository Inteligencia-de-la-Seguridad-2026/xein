package es.xein.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PaymentClient(ObjectMapper objectMapper,
                         @Value("${XEIN_PAYMENT_URL:http://payment:9090}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String approveCheckout(Long userId, Long orderId, double total,
                                  String cardNumber, String expiry, String cvv) {
        long amountCents = BigDecimal.valueOf(total)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (amountCents <= 0) {
            throw new PaymentException("The order total must be positive");
        }

        String orderKey = orderId == null ? UUID.randomUUID().toString().replace("-", "") : orderId.toString();
        String reference = "checkout_" + orderKey + "_a" + amountCents;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", reference);
        payload.put("payer", "user_" + userId);
        payload.put("payee", "xein_store");
        payload.put("amount_cents", amountCents);
        payload.put("currency", "LAB");
        payload.put("card_number", cardNumber);
        payload.put("expiry", expiry);
        payload.put("cvv", cvv);

        PaymentResponse response = post("/transactions", payload);
        if (response.status() != 200 && response.status() != 201) {
            throw new PaymentException("The simulated payment was declined");
        }
        try {
            JsonNode body = objectMapper.readTree(response.body());
            String approvedReference = body.path("reference").asText("");
            if (!reference.equals(approvedReference)) {
                throw new PaymentException("The payment response did not match the order");
            }
            return approvedReference;
        } catch (IOException exception) {
            throw new PaymentException("The payment response was invalid", exception);
        }
    }

    public PaymentResponse submitTransfer(Map<String, Object> payload) {
        return post("/transfers", payload);
    }

    private PaymentResponse post(String path, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new PaymentResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentException("The payment service was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new PaymentException("The payment service is unavailable", exception);
        }
    }

    public record PaymentResponse(int status, String body) {}

    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }

        public PaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
