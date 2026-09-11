package com.interview.employees.service;

import com.interview.employees.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.Map;

public interface EmployeeService {

    EmployeeResponse create(EmployeeRequest request);
    EmployeeResponse getById(Long id);
    PageResponse<EmployeeResponse> getAll(Pageable pageable);
    EmployeeResponse update(Long id, EmployeeRequest request);
    void delete(Long id);
    Map<String, BigDecimal> averageSalaryByDepartment();
}
