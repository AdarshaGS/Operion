package com.operion.payroll.api;

import com.operion.payroll.SalaryComponentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeSalaryComponentStatusRequest(@NotNull SalaryComponentStatus status) {
}
