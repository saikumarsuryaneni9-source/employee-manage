package com.interview.employees.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeRequest(@NotBlank String name, @NotBlank @Email String email,
                              @NotBlank String department, @NotNull @Positive BigDecimal salary,
                              @NotNull LocalDate dateOfJoining) { }
