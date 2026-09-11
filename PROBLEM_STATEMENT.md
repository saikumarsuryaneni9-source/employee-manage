# Problem Statement

Build a Spring Boot REST API for an Employee resource.

Endpoints:
- POST `/api/employees`
- GET `/api/employees/{id}`
- GET `/api/employees?page=0&size=10&sortBy=name`
- PUT `/api/employees/{id}`
- DELETE `/api/employees/{id}`
- GET `/api/employees/analytics/avg-salary-by-department`

Employee fields:
- id — auto-generated
- name — required
- email — required and valid
- department — required
- salary — required and greater than 0
- dateOfJoining — required

Bonus: implement a thread-safe in-memory TTL cache without Redis/Caffeine.
