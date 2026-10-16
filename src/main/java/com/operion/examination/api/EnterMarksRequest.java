package com.operion.examination.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record EnterMarksRequest(@NotNull List<@Valid @NotNull MarkEntry> marks) {
}
