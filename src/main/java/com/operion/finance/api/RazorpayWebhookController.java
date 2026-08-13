package com.operion.finance.api;

import com.operion.finance.FeePaymentGatewayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Public, unauthenticated, and trusted only after FeePaymentGatewayService.handleWebhook
 * verifies the HMAC signature - see that method's own note on why this is the sole path
 * allowed to actually record a payment. rawBody is bound as a plain String (not parsed
 * into a DTO) specifically so the exact bytes Razorpay signed are what get hashed for
 * verification - re-serializing a parsed object first would produce a different byte
 * sequence and always fail signature verification.
 */
@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
public class RazorpayWebhookController {

	private final FeePaymentGatewayService gatewayService;
	private final ObjectMapper objectMapper;

	public RazorpayWebhookController(FeePaymentGatewayService gatewayService, ObjectMapper objectMapper) {
		this.gatewayService = gatewayService;
		this.objectMapper = objectMapper;
	}

	@PostMapping
	public void receive(@RequestBody String rawBody, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
		JsonNode root = objectMapper.readTree(rawBody);
		String event = root.path("event").asString(null);
		JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
		String gatewayOrderId = paymentEntity.path("order_id").asString(null);
		String gatewayPaymentId = paymentEntity.path("id").asString(null);
		gatewayService.handleWebhook(rawBody, signature, event, gatewayOrderId, gatewayPaymentId);
	}
}
