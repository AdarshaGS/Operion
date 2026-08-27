package com.operion.email;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seam every module actually sending a transactional email (member invites, email
 * verification) calls into, instead of talking to a specific provider directly.
 * Best-effort: tries each configured {@link EmailSender} in order, falling back to the
 * next if one fails (missing config or a real send error) so a single provider
 * outage/misconfiguration never blocks the underlying action the email is attached to -
 * same trust tier as the manual-copy fallback this replaces (StaffInviteDialog still
 * shows the raw claim link regardless of whether the email actually went out).
 *
 * <p>Takes {@code List<EmailSender>} (the public interface) rather than concretely-typed
 * Brevo/Resend beans, both so a test in any package can substitute hand-written stubs
 * (this codebase's established pattern for external dependencies - see
 * com.operion.finance.RazorpayGateway) and so the fallback order is explicit and
 * inspectable (BrevoEmailSender/ResendEmailSender declare it via {@code @Order}) rather
 * than hardcoded here.
 */
@Service
public class EmailDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

	private final List<EmailSender> senders;
	private final EmailOutboxRepository emailOutboxRepository;

	public EmailDeliveryService(List<EmailSender> senders, EmailOutboxRepository emailOutboxRepository) {
		this.senders = senders;
		this.emailOutboxRepository = emailOutboxRepository;
	}

	/** Never throws - a caller mid-way through issuing an invite or a verification token
	 * must not have that action fail just because email delivery did. Returns whether
	 * some provider actually accepted it, in case the caller wants to reflect that back
	 * (e.g. StaffInviteResponse.emailSent). No-op (returns false, no outbox row) if there's
	 * no recipient address at all - not every member logs in with an email. */
	@Transactional
	public boolean sendBestEffort(String to, String subject, String htmlBody) {
		if (to == null || to.isBlank()) {
			return false;
		}

		EmailMessage message = new EmailMessage(to, subject, htmlBody);
		EmailOutbox outbox = emailOutboxRepository.save(new EmailOutbox(to, subject));

		for (EmailSender sender : senders) {
			try {
				sender.send(message);
				outbox.markSent(sender.providerName());
				emailOutboxRepository.save(outbox);
				return true;
			} catch (EmailSendException ex) {
				log.warn("Email delivery via {} failed for {}: {}", sender.providerName(), to, ex.getMessage());
			}
		}

		outbox.markFailed("All configured email providers failed or are unconfigured");
		emailOutboxRepository.save(outbox);
		return false;
	}
}
