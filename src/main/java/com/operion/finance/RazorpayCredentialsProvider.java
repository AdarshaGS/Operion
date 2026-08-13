package com.operion.finance;

import com.operion.organisation.Organisation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One shared platform Razorpay account for every organisation - a deliberate v1
 * simplification, confirmed over building per-org gateway credentials now. Fine for a
 * pilot/single-school stage; wrong once a second real paying school needs its fee
 * collections landing in its own bank account, not a shared one. Every caller already
 * passes "which organisation", so swapping resolveFor() to look up per-org stored
 * credentials later is a contained change to this one class, not a rewrite of
 * FeePaymentGatewayService or its controllers.
 */
@Component
public class RazorpayCredentialsProvider {

	private final RazorpayCredentials sharedCredentials;

	public RazorpayCredentialsProvider(@Value("${app.razorpay.key-id:}") String keyId,
			@Value("${app.razorpay.key-secret:}") String keySecret, @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
		this.sharedCredentials = new RazorpayCredentials(keyId, keySecret, webhookSecret);
	}

	public RazorpayCredentials resolveFor(Organisation organisation) {
		return sharedCredentials;
	}

	/** Used only where no organisation is resolvable yet - webhook signature verification
	 * happens before we know which org's order this even is (see
	 * PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId). Identical to
	 * resolveFor() today since there's only one shared account either way. */
	public RazorpayCredentials sharedCredentials() {
		return sharedCredentials;
	}
}
