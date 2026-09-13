package com.operion.finance.api;

import java.util.List;

public record SendRemindersRequest(List<Long> invoiceIds) {
}
