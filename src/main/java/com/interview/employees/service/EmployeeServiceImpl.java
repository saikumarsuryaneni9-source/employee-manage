package com.interview.employees.service;

import com.interview.employees.cache.TtlCache;
import com.interview.employees.dto.*;
import com.interview.employees.exception.ResourceNotFoundException;
import com.interview.employees.model.Employee;
import com.interview.employees.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.math.*;
import java.util.stream.Collectors;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository repository;
    private final TtlCache<String, Map<String, BigDecimal>> analyticsCache;

    public EmployeeServiceImpl(EmployeeRepository repository, TtlCache<String, Map<String, BigDecimal>> analyticsCache) {
        this.repository = repository; this.analyticsCache = analyticsCache;
    }

    @Override public EmployeeResponse create(EmployeeRequest r) {
        Employee e = repository.save(new Employee(r.name(), r.email(), r.department(), r.salary(), r.dateOfJoining()));
        analyticsCache.invalidate("department-average"); return response(e);
    }
    @Override public EmployeeResponse getById(Long id) { return response(find(id)); }
    @Override public PageResponse<EmployeeResponse> getAll(Pageable pageable) {
        var page = repository.findAll(pageable).map(this::response);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
    @Override public EmployeeResponse update(Long id, EmployeeRequest r) {
        Employee e = find(id); e.replaceWith(r.name(), r.email(), r.department(), r.salary(), r.dateOfJoining());
        Employee updated = repository.save(e); analyticsCache.invalidate("department-average"); return response(updated);
    }
    @Override public void delete(Long id) { repository.delete(find(id)); analyticsCache.invalidate("department-average"); }
    @Override public Map<String, BigDecimal> averageSalaryByDepartment() {
        return analyticsCache.get("department-average", () -> repository.findAll().stream().collect(Collectors.groupingBy(
                Employee::getDepartment, Collectors.collectingAndThen(Collectors.mapping(Employee::getSalary, Collectors.toList()), this::average))));
    }
    private BigDecimal average(java.util.List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP); }
    private Employee find(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee with id " + id + " was not found")); }
    private EmployeeResponse response(Employee e) { return new EmployeeResponse(e.getId(), e.getName(), e.getEmail(), e.getDepartment(), e.getSalary(), e.getDateOfJoining()); }
}
