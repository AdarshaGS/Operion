package com.operion.library.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.library.Fine;

public record FineResponse(Long id, Long borrowRecordId, BigDecimal amount, String reason, String status,
		LocalDate paidDate, Long waivedBy, String waivedReason) {

	public static FineResponse from(Fine fine) {
		return new FineResponse(fine.getId(), fine.getBorrowRecord().getId(), fine.getAmount(), fine.getReason().name(),
				fine.getStatus().name(), fine.getPaidDate(), fine.getWaivedBy(), fine.getWaivedReason());
	}
}
