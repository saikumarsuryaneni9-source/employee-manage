# Employee Management API

A Spring Boot REST API for employee CRUD operations, paginated listing, and average salary analytics by department. The implementation follows the supplied project structure and keeps controllers, services, persistence, exception handling, and caching separate.

## Technology

Java 17, Spring Boot 3.3.5, Spring Web, Bean Validation, Spring Data JPA, H2, Maven, JUnit 5, and Mockito. H2 runs in memory, so no external database is needed.

## Run

```bash
mvn clean test
mvn spring-boot:run
```

The cache duration is configurable with `analytics.cache.ttl` in `src/main/resources/application.properties` and defaults to 60 seconds.

## Design

The application uses a layered design so HTTP concerns, business rules, persistence, and caching remain independently testable:

```text
HTTP client
	|
	v
EmployeeController
	|
	v
EmployeeService
	|                    +------------------+
	+------------------->| EmployeeRepository |----> H2 database
	|                    +------------------+
	|
	+-------------------> TtlCache
							  |
							  v
					 Average salary by department
```

- **Controller:** Exposes the `/api/employees` REST endpoints, binds request bodies and query parameters, and applies Bean Validation.
- **DTOs:** Keep the HTTP contract separate from the JPA entity. `EmployeeRequest` is used for create and update operations, while `EmployeeResponse` is returned to clients.
- **Service:** Owns employee operations, maps entities to responses, handles missing employees, and calculates department salary averages rounded to two decimal places.
- **Repository:** Uses Spring Data JPA through `EmployeeRepository`, with H2 as the in-memory persistence store.
- **Error handling:** Invalid request data and invalid pagination parameters are rejected at the API boundary; missing employees result in `404 Not Found`.
- **Analytics cache:** Only the aggregate analytics endpoint is cached. Reads use a per-key lock to prevent duplicate loads under concurrency, while create, update, and delete operations invalidate the aggregate immediately.

## Endpoints

- `POST /api/employees` - creates an employee and returns `201 Created`.
- `GET /api/employees/{id}` - returns one employee or `404 Not Found`.
- `GET /api/employees?page=0&size=10&sortBy=name` - returns pagination metadata. Defaults are page 0, size 10, and sort by id; size is limited to 1-100 and sort fields are validated.
- `PUT /api/employees/{id}` - performs a full replacement of mutable fields.
- `DELETE /api/employees/{id}` - deletes an employee and returns `204 No Content`.
- `GET /api/employees/analytics/avg-salary-by-department` - returns department-to-average-salary data.

Example create body:

```json
{"name":"Ava Singh","email":"ava@example.com","department":"Engineering","salary":125000.00,"dateOfJoining":"2024-01-15"}
```

Salary uses `BigDecimal`. Analytics is calculated in the service layer with Java Streams and averages are rounded to two decimal places.

## Cache design

`TtlCache` stores entries in a `ConcurrentHashMap` and uses a separate per-key lock map. Missing or expired entries are rechecked after acquiring their key lock, ensuring only one thread runs the loader while other callers wait. Create, update, and delete invalidate the analytics key immediately; TTL remains the fallback expiry mechanism. No third-party cache library is used.

## Tests

Run `mvn test`. Tests cover cache hits, expiration, invalidation, concurrent cache access, service behavior, validation, missing resources, creation, and invalid pagination.

The API intentionally does not add authentication, future-date restrictions, or other features outside the challenge requirements.
