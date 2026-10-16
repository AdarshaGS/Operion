package com.operion.student.api;

import jakarta.validation.constraints.NotNull;


public record RaiseTransferRequestRequest(@NotNull Long toCampusId, String reason) {
}
