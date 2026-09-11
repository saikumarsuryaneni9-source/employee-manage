# Employee Management API Walkthrough

## 1. Project purpose

This project implements a REST API for managing employees. It supports employee creation, retrieval, paginated listing, full updates, deletion, and average salary analytics grouped by department.

The application is intentionally focused on the interview requirements. It uses an in-memory H2 database and does not require authentication, Redis, Docker, a frontend, or any external service.

## 2. Technology and dependencies

The project is defined in `pom.xml` and uses:

- Java 17 - language and runtime target configured by Maven.
- Spring Boot 3.3.5 - application bootstrapping and dependency management.
- Spring Web - REST controllers, request mapping, JSON serialization, and HTTP responses.
- Spring Boot Validation - Bean Validation annotations such as `@NotBlank`, `@Email`, `@NotNull`, and `@Positive`.
- Spring Data JPA - repository abstraction, pagination, sorting, and persistence integration.
- Hibernate - JPA implementation used by Spring Boot.
- H2 - in-memory relational database for local execution.
- Spring Boot Test and JUnit 5 - automated testing support.
- Mockito - mocking dependencies in unit tests where useful.

No third-party caching library is used. The TTL cache is implemented with `ConcurrentHashMap` and Java synchronization primitives from `java.util.concurrent`.

## 3. Application startup flow

1. `EmployeeManagementApplication` is the entry point.
2. `@SpringBootApplication` enables component scanning, auto-configuration, and Spring Boot configuration.
3. Spring Boot starts an embedded Tomcat server on port `8080`.
4. Spring Data discovers `EmployeeRepository` and creates its implementation.
5. Hibernate creates the `employees` table in the H2 in-memory database using the entity metadata.
6. `CacheConfig` creates the analytics `TtlCache` bean using the configured `analytics.cache.ttl` value.
7. Spring creates the controller, service, repository, exception handler, and cache components using constructor injection.

The database is recreated each time the application starts because `spring.jpa.hibernate.ddl-auto=create-drop` is configured.

## 4. Package responsibilities

### `model`

`Employee` is the JPA entity. It contains:

- `id` - generated primary key.
- `name` - required employee name.
- `email` - required and validated email address.
- `department` - required department name.
- `salary` - required positive `BigDecimal` value.
- `dateOfJoining` - required `LocalDate`.

`BigDecimal` is used instead of `Double` so salary calculations do not introduce floating-point rounding errors.

### `dto`

- `EmployeeRequest` represents create and full-update input.
- `EmployeeResponse` represents data returned to API clients.
- `PageResponse` provides a clean pagination response instead of exposing Spring's internal `Page` object directly.

The API does not bind request bodies directly to JPA entities and does not expose entity objects as its public contract.

### `repository`

`EmployeeRepository` extends `JpaRepository<Employee, Long>`. This supplies CRUD methods and `findAll(Pageable)` without manually writing SQL.

### `service`

`EmployeeService` defines the application operations, while `EmployeeServiceImpl` coordinates repository access, DTO mapping, analytics, and cache invalidation.

The service is the only layer that performs business orchestration. Controllers do not access the repository directly.

### `controller`

`EmployeeController` maps HTTP requests to service calls. It handles request validation, supported sort fields, pagination limits, and HTTP status codes.

### `cache`

`TtlCache` is a reusable in-memory cache with per-key locking. The service uses one key, `department-average`, for the analytics result.

### `config`

`CacheConfig` constructs the typed analytics cache and injects its TTL from application configuration.

### `exception`

- `ResourceNotFoundException` represents missing employees.
- `ApiError` is the consistent error response format.
- `GlobalExceptionHandler` converts validation, invalid parameter, missing-resource, and unexpected errors into safe JSON responses.

## 5. Request and response flow

The normal flow is:

```text
HTTP request
    -> EmployeeController
    -> Bean Validation / parameter checks
    -> EmployeeServiceImpl
    -> EmployeeRepository
    -> Hibernate
    -> H2 database
    -> DTO response
    -> HTTP response
```

For analytics, the service checks the cache before reading employee data:

```text
Analytics request
    -> EmployeeController
    -> EmployeeServiceImpl
    -> TtlCache
       -> cached result, or
       -> EmployeeRepository.findAll()
       -> Java Streams grouping and averaging
       -> cache result
    -> HTTP response
```

## 6. API details

### Create employee

`POST /api/employees`

Example request:

```json
{
  "name": "Ava Singh",
  "email": "ava@example.com",
  "department": "Engineering",
  "salary": 100000.00,
  "dateOfJoining": "2024-01-15"
}
```

The request is validated before the service is called. The service creates the entity, saves it, invalidates the analytics cache, and returns an `EmployeeResponse`.

Successful response: `201 Created`.

### Get one employee

`GET /api/employees/{id}`

The service searches by ID. If found, it maps the entity to `EmployeeResponse`. If not found, `ResourceNotFoundException` is handled as `404 Not Found`.

### List employees

`GET /api/employees?page=0&size=10&sortBy=name`

Defaults:

- `page=0`
- `size=10`
- `sortBy=id`

Supported sort fields are `id`, `name`, `email`, `department`, `salary`, and `dateOfJoining`. Page numbers cannot be negative, and page size must be between 1 and 100.

The controller creates a Spring Data `PageRequest`. The repository performs pagination and sorting in the database rather than loading all employees and slicing them in memory.

Example response shape:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

### Full update

`PUT /api/employees/{id}`

The request uses the same complete fields as creation. The service first finds the employee, replaces every mutable field, saves it, invalidates analytics, and returns the updated representation.

This is a full update, not a partial update. Missing or invalid request fields produce `400 Bad Request`; an unknown ID produces `404 Not Found`.

### Delete employee

`DELETE /api/employees/{id}`

The service confirms that the employee exists before deleting it. After a successful delete, the analytics cache is invalidated.

Successful response: `204 No Content`.

### Average salary by department

`GET /api/employees/analytics/avg-salary-by-department`

The service reads employees and performs aggregation with Java Streams:

1. Group employees by department.
2. Map each employee to its `BigDecimal` salary.
3. Sum salaries within each department.
4. Divide by the number of employees in that department.
5. Round the result to two decimal places using `RoundingMode.HALF_UP`.

Example response:

```json
{
  "Engineering": 110000.00,
  "Finance": 85000.00
}
```

An empty employee table returns `{}`.

## 7. Validation and error handling

`EmployeeRequest` applies the following validation rules:

- `name`: not null, not empty, and not blank.
- `email`: not null, not blank, and valid email format.
- `department`: not null, not empty, and not blank.
- `salary`: not null and greater than zero.
- `dateOfJoining`: not null.

`GlobalExceptionHandler` returns an `ApiError` containing:

- timestamp
- HTTP status
- error name
- one or more messages
- request path

Validation errors include field-specific messages. Internal exception details, stack traces, SQL, and Java class names are not exposed to API clients.

## 8. TTL cache internals

The cache stores entries in a `ConcurrentHashMap`. Each key also has a lock in a second `ConcurrentHashMap`.

For `get(key, loader)`:

1. The cache checks for a non-expired value without locking.
2. If the value is absent or expired, it obtains the lock associated with that key.
3. Inside the lock, it checks the cache again. This avoids duplicate work when another thread refreshed the value just before the current thread acquired the lock.
4. Only the lock owner calls `loader.get()`.
5. The new value is stored with an expiry timestamp based on `System.nanoTime()`.
6. Waiting threads return the refreshed value after the lock is released.

Because the lock is associated with the cache key, different keys can be refreshed independently. The analytics use case has one key, but the implementation still has correct per-key behavior.

The TTL is configured externally:

```properties
analytics.cache.ttl=60s
```

## 9. Cache invalidation

The analytics result depends on employee data. Therefore, successful create, update, and delete operations call:

```text
analyticsCache.invalidate("department-average")
```

This prevents stale salary averages from being served after a mutation. TTL expiration remains a fallback for values that were not explicitly invalidated.

## 10. Database configuration

The application uses:

```properties
spring.datasource.url=jdbc:h2:mem:employees
spring.jpa.hibernate.ddl-auto=create-drop
```

The database exists only for the running application process. Restarting the application starts with an empty employee table.

The entity uses JPA annotations including `@Entity`, `@Id`, `@GeneratedValue`, and non-null column constraints for important fields.

## 11. Testing

Run all tests from the project directory:

```bash
mvn clean test
```

The current test suite covers:

- Returning cached values without reloading.
- Reloading after TTL expiration.
- Preventing a cache stampede when multiple threads request the same missing key.

The concurrency test uses an `ExecutorService`, `CountDownLatch`, and `AtomicInteger`. The executor is shut down in a `finally` block.

## 12. Running and manually checking the API

Start the application:

```bash
mvn spring-boot:run
```

Create an employee:

```bash
curl -i -X POST http://localhost:8080/api/employees \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ava Singh","email":"ava@example.com","department":"Engineering","salary":100000.00,"dateOfJoining":"2024-01-15"}'
```

Then check:

```bash
curl -i http://localhost:8080/api/employees/1
curl -i 'http://localhost:8080/api/employees?page=0&size=10&sortBy=name'
curl -i http://localhost:8080/api/employees/analytics/avg-salary-by-department
curl -i -X PUT http://localhost:8080/api/employees/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ava Updated","email":"ava.updated@example.com","department":"Engineering","salary":120000.00,"dateOfJoining":"2024-02-01"}'
curl -i -X DELETE http://localhost:8080/api/employees/1
```

Useful negative checks include an invalid email, a blank name, `page=-1`, `size=0`, an unsupported `sortBy`, and an unknown employee ID.

## 13. Important design decisions

- DTOs keep the public API separate from the persistence model.
- `BigDecimal` preserves monetary precision.
- Spring Data pagination prevents in-memory slicing of the full employee table.
- Java Streams satisfy the analytics requirement in the service layer.
- Per-key locking prevents duplicate cache computation without introducing an external cache.
- Immediate invalidation keeps analytics fresh after mutations.
- Constructor injection makes dependencies explicit and keeps classes testable.
