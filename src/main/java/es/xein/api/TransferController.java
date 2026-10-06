package es.xein.api;

import es.xein.Service.PaymentClient;
import es.xein.Service.PaymentClient.PaymentException;
import es.xein.Service.PaymentClient.PaymentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TransferController {
    private final PaymentClient paymentClient;

    public TransferController(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @PostMapping(value = "/api/payments/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> transfer(@RequestBody Map<String, Object> request) {
        try {
            PaymentResponse response = paymentClient.submitTransfer(request);
            return ResponseEntity.status(response.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (PaymentException exception) {
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"The simulated payment service is unavailable\"}");
        }
    }
}
