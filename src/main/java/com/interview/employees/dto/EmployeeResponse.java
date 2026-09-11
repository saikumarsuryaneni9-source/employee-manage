package com.interview.employees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeResponse(Long id, String name, String email, String department,
                               BigDecimal salary, LocalDate dateOfJoining) { }
