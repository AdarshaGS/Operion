package com.operion.purchase.api;

import java.time.LocalDate;
import java.util.List;

public record ReceiveGoodsRequest(LocalDate entryDate, List<ReceiveGoodsLineRequest> lines) {
}
