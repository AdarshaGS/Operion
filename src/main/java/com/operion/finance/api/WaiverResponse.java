package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Waiver;

public record WaiverResponse(Long id, Long invoiceId, BigDecimal amount, String reason, Long approvedBy, LocalDate waiverDate) {

	static WaiverResponse from(Waiver waiver) {
		return new WaiverResponse(waiver.getId(), waiver.getInvoice().getId(),
				waiver.getAmount(), waiver.getReason(), waiver.getApprovedBy(), waiver.getWaiverDate());
	}
}
