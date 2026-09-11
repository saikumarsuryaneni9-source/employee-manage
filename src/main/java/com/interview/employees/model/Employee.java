package com.interview.employees.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String department;

    @NotNull @Positive
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal salary;

    @NotNull
    private LocalDate dateOfJoining;

    protected Employee() { }
    public Employee(String name, String email, String department, BigDecimal salary, LocalDate dateOfJoining) {
        this.name = name; this.email = email; this.department = department; this.salary = salary; this.dateOfJoining = dateOfJoining;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public BigDecimal getSalary() { return salary; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void replaceWith(String name, String email, String department, BigDecimal salary, LocalDate dateOfJoining) {
        this.name = name; this.email = email; this.department = department; this.salary = salary; this.dateOfJoining = dateOfJoining;
    }
}
