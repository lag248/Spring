# Hotel Booking Platform
Учебная микросервисная REST-система бронирования отелей на Java 17 и Spring Boot 3.5.

## Состав
- eureka-server — реестр сервисов, порт 8761.
- api-gateway — API Gateway, порт 8080.
- booking-service — пользователи, JWT, бронирования и Saga, порт 8081.
- hotel-service — отели, номера, блокировки и статистика, порт 8082.

Схема:  
Клиент -> API Gateway -> Booking Service  
                    -> Hotel Service  
Booking Service -> Hotel Service  
Все сервисы -> Eureka

## Запуск
### Docker Compose
Требуются Docker Engine и Docker Compose:

```bash
docker compose up --build
```

Адреса:
- Gateway: http://localhost:8080
- Eureka: http://localhost:8761
- Swagger Booking: http://localhost:8081/swagger-ui/index.html
- Swagger Hotel: http://localhost:8082/swagger-ui/index.html
- H2 Booking: http://localhost:8081/h2-console
- H2 Hotel: http://localhost:8082/h2-console

После перезапуска сервисов данные H2 очищаются.

### Локально
Нужны Java 17+ и Maven 3.6.3+.

```bash
mvn -pl eureka-server spring-boot:run
mvn -pl hotel-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Для другой среды задайте одинаковые `EUREKA_URL`, `JWT_SECRET` и `INTERNAL_SERVICE_TOKEN` в соответствующих сервисах.

## Демонстрационные пользователи
| Роль | Логин | Пароль |
|---|---|---|
| ADMIN | admin | Admin123! |
| USER | demo | Password123! |

Самостоятельная регистрация создаёт только USER. JWT действует 3600 секунд, пароли хранятся в BCrypt.

## Аутентификация
```bash
curl -X POST http://localhost:8080/api/user/auth \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"Password123!"}'
```

Передавайте полученный `accessToken` как `Authorization: Bearer <токен>`.

## Основные endpoints
### Booking Service
| Метод | URL | Доступ |
|---|---|---|
| POST | /api/user/register | публичный |
| POST | /api/user/auth | публичный |
| GET/POST/PATCH/DELETE | /api/user[/{id}] | ADMIN |
| POST | /api/booking или /api/bookings | USER/ADMIN |
| GET | /api/bookings?page=0&size=20 | USER/ADMIN |
| GET/DELETE | /api/booking/{id} | владелец/ADMIN |

Пример бронирования:

```json
{
  "requestId": "demo-booking-001",
  "roomId": 1,
  "startDate": "2030-05-10",
  "endDate": "2030-05-13",
  "autoSelect": false
}
```

`requestId` используется как ключ идемпотентности. При `autoSelect=true` поле `roomId` игнорируется. Даты используют полуинтервалы `[startDate, endDate)`.

### Hotel Service
| Метод | URL | Доступ |
|---|---|---|
| GET | /api/hotels, /api/hotels/{id} | USER/ADMIN |
| POST/PATCH/DELETE | /api/hotels[/{id}] | ADMIN |
| GET | /api/rooms, /api/rooms/{id} | USER/ADMIN |
| GET | /api/rooms/recommend | USER/ADMIN |
| POST/PATCH/DELETE | /api/rooms[/{id}] | ADMIN |
| GET | /api/hotels/{id}/occupancy | ADMIN |

Рекомендации сортируются по возрастанию `timesBooked`, затем по ID. Внутренние `confirm-availability`, `commit` и `release` не публикуются через Gateway и требуют `X-Internal-Token`.

## Saga бронирования
Booking Service создаёт локальную запись в PENDING.
Hotel Service блокирует свободный номер на нужные даты.
После подтверждения бронь переходит в CONFIRMED.
При ошибке, timeout или исчерпании retry бронь становится CANCELLED, затем выполняется release.
Удалённые вызовы имеют timeout, ограниченные повторы с backoff и circuit breaker. Повтор с тем же `requestId` не создаёт дубль и не увеличивает счётчик повторно.

## Используемые технологии и установка
- Java 17+;
- Spring Boot 3.5.x;
- Spring Cloud Gateway и Eureka;
- Spring Security OAuth2 Resource Server и JWT HS256;
- Spring Data JPA/Hibernate и H2;
- Resilience4j Retry/Circuit Breaker;
- Springdoc OpenAPI/Swagger;
- JUnit 5, MockMvc и Spring Security Test.

Зависимости скачиваются Maven из `pom.xml`. Для локального запуска нужны Java 17+ и Maven 3.6.3+, для Docker-варианта — Docker Engine и Docker Compose.

## База данных
Booking Service хранит `users` и `bookings`. Hotel Service хранит `hotels`, `rooms` и `room_holds`. Используются связи, индексы, уникальные ограничения для `requestId`, уникальность номера внутри отеля, optimistic `@Version` для комнат и pessimistic lock при создании hold.

Поле `available` показывает эксплуатационную доступность номера и не заменяет проверку занятости по датам. Занятость определяется активными HELD/CONFIRMED hold и пересечением интервалов `[startDate, endDate)`.

## Логирование и обработка ошибок
Каждый запрос получает или принимает `X-Correlation-Id`. Он передаётся между сервисами и используется вместе с `bookingId` и `requestId` в логах. Ошибки возвращаются в едином JSON-формате ApiError.

Основные статусы: 400 — ошибка валидации, 401 — нет JWT или токен неверен, 403 — недостаточно прав, 404 — объект не найден, 409 — конфликт бронирования или идемпотентности, 503 — Hotel Service недоступен.

## Ручная проверка
### Быстрый smoke-test
1. Запустите Compose и убедитесь, что все три сервиса зарегистрированы в Eureka.
2. Откройте Swagger обоих бизнес-сервисов и health endpoints.
3. Получите USER и ADMIN JWT.
4. Проверьте: запрос без токена даёт 401, USER на административном endpoint даёт 403.
5. Получите отели, комнаты и рекомендации; проверьте сортировку.
6. Создайте бронь с уникальным `requestId`; ожидайте CONFIRMED.
7. Повторите тот же запрос; ID и `timesBooked` не должны измениться.
8. Проверьте историю, пагинацию, отмену и запрет чтения чужой брони.
9. Проверьте две пересекающиеся брони одного номера: одновременно подтверждается только одна.
10. Остановите Hotel Service, проверьте timeout/retry, CANCELLED, release и записи в логах.

## Команды ручной проверки
```bash
curl http://localhost:8080/actuator/health
curl -H "Authorization: Bearer USER_TOKEN" http://localhost:8080/api/hotels
curl -H "Authorization: Bearer USER_TOKEN" "http://localhost:8080/api/rooms/recommend?startDate=2030-05-10&endDate=2030-05-13"
curl -H "Authorization: Bearer USER_TOKEN" "http://localhost:8080/api/bookings?page=0&size=20"
curl -i -H "Authorization: Bearer USER_TOKEN" -H "X-Correlation-Id: manual-trace-001" http://localhost:8080/api/hotels
```

В H2 Console используются JDBC URL `jdbc:h2:mem:bookingdb` и `jdbc:h2:mem:hoteldb`, пользователь `sa`, пароль пустой. Проверьте таблицы `BOOKINGS`, `ROOM_HOLDS`, `ROOMS`, `TIMES_BOOKED` и уникальные ограничения.

## Соответствие ТЗ и критериям
| Критерий | Реализация |
|---|---|
| Планирование | RoomPlanner, сортировка timesBooked/ID, блокировки и интервалы дат |
| Согласованность | PENDING -> reserve/commit -> CONFIRMED, компенсация CANCELLED + release |
| Booking CRUD | CRUD, пагинация, фильтр статуса, валидация дат, idempotency key |
| Hotel CRUD | CRUD отелей/комнат, фильтры, рекомендации и occupancy |
| Gateway/Eureka | lb:// маршрутизация, discovery, edge JWT и запрет внутренних маршрутов |
| Security | JWT в Gateway и backend, @PreAuthorize, роли USER/ADMIN, 401/403 |
| Тестирование | JUnit, MockMvc, конкурентность, Saga, идемпотентность и негативные кейсы |
| БД | H2, связи, индексы, unique constraints, @Version, room_holds |
| Данные | Dev-seeder с ADMIN, USER, отелями и комнатами |
| Структура кода | DTO/controller/service/repository/security |
| Ошибки | Единый ApiError, @RestControllerAdvice, статусы 400/404/409/503 |
| Логи | X-Correlation-Id, MDC, bookingId, requestId, traceId |

## Автоматическое тестирование

Перейдите в каталог проекта и проверьте инструменты:

```powershell
java -version
mvn -version
```

Запустите полный набор:

```powershell
mvn clean test
```

Успешный результат: `BUILD SUCCESS`, `Failures: 0`, `Errors: 0`. Отдельные модули:

```powershell
mvn -pl hotel-service test
mvn -pl booking-service test
mvn -pl api-gateway test
mvn -pl hotel-service -Dtest=RoomManagementConcurrencyIntegrationTest test
mvn -pl booking-service -Dtest=RoomPlannerTest test
mvn -pl booking-service -Dtest=BookingOrchestratorTest test
```

Проверяемые сценарии: JWT и login, роли, correlation ID, сортировка рекомендаций, блокировка пересекающихся дат, идемпотентность, переходы `PENDING → CONFIRMED`, компенсация `PENDING → CANCELLED`, retry/timeout и release. Отчёты находятся в `*/target/surefire-reports/`.

## Ручное тестирование

Все шаги выполняйте через Gateway `http://localhost:8080`. Для повторного прогона используйте `docker compose down`, затем `docker compose up --build`.

### 1. Запуск

```powershell
docker compose up --build
```

Окно с логами оставьте открытым. Во втором окне PowerShell проверьте:

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object StatusCode,Content
Invoke-WebRequest http://localhost:8081/actuator/health | Select-Object StatusCode,Content
Invoke-WebRequest http://localhost:8082/actuator/health | Select-Object StatusCode,Content
```

В каждом ответе должны быть `200` и `UP`. В браузере откройте http://localhost:8761 и проверьте `API-GATEWAY`, `BOOKING-SERVICE`, `HOTEL-SERVICE` со статусом `UP`.

### 2. Swagger

Откройте http://localhost:8081/swagger-ui/index.html и http://localhost:8082/swagger-ui/index.html. Проверьте наличие групп пользователей/бронирований и отелей/комнат. Нажмите `Authorize` и укажите JWT в формате `Bearer <токен>`.

### 3. Получение токенов

```powershell
$userLogin = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/user/auth -ContentType 'application/json' -Body '{"username":"demo","password":"Password123!"}'
$USER_TOKEN = $userLogin.accessToken
$adminLogin = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/user/auth -ContentType 'application/json' -Body '{"username":"admin","password":"Admin123!"}'
$ADMIN_TOKEN = $adminLogin.accessToken
$userHeaders = @{ Authorization = "Bearer $USER_TOKEN" }
$adminHeaders = @{ Authorization = "Bearer $ADMIN_TOKEN" }
```

Убедитесь, что оба токена непустые. Зарегистрируйте нового пользователя и проверьте, что ему назначена только роль `USER`:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/user/register -ContentType 'application/json' -Body (@{ username='manual-user'; password='Password123!' } | ConvertTo-Json)
```

### 4. 401 и 403

```powershell
try { Invoke-WebRequest http://localhost:8080/api/bookings -ErrorAction Stop } catch { $_.Exception.Response.StatusCode.value__ }
try { Invoke-WebRequest http://localhost:8080/api/user -Headers $userHeaders -ErrorAction Stop } catch { $_.Exception.Response.StatusCode.value__ }
```

Ожидаются соответственно `401` и `403`. ADMIN-токеном `GET /api/user` должен быть успешным.

### 5. Отели, комнаты и CRUD

```powershell
Invoke-RestMethod http://localhost:8080/api/hotels -Headers $userHeaders
Invoke-RestMethod 'http://localhost:8080/api/rooms?startDate=2030-05-10&endDate=2030-05-13' -Headers $userHeaders
Invoke-RestMethod 'http://localhost:8080/api/rooms/recommend?startDate=2030-05-10&endDate=2030-05-13' -Headers $userHeaders
Invoke-RestMethod 'http://localhost:8080/api/hotels?name=central' -Headers $userHeaders
```

Проверьте отели, свободные комнаты и сортировку рекомендаций по `timesBooked`, затем по ID. ADMIN создаёт отель через `POST /api/hotels`, получает его через GET, меняет через PATCH и удаляет через DELETE. Аналогично проверьте комнату через `POST/PATCH/DELETE /api/rooms`.

### 6. Успешная бронь

Выберите свободный `roomId` из ответа `/api/rooms` и выполните:

```powershell
$body = @{ requestId='manual-booking-001'; roomId=1; startDate='2030-05-10'; endDate='2030-05-13'; autoSelect=$false } | ConvertTo-Json
$booking = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/booking -Headers $userHeaders -ContentType 'application/json' -Body $body
$booking
```

Проверьте `status=CONFIRMED`, даты, пользователя и комнату. Повторите тот же запрос с заголовком `Idempotency-Key: manual-booking-001`: ID должен совпасть, а `timesBooked` не должен увеличиться.

### 7. Автоподбор, история и даты

Отправьте бронь с `autoSelect=true` и фиктивным `roomId`: должен быть выбран рекомендованный номер. Запросите `GET /api/bookings?page=0&size=1` и проверьте `content`, `totalElements`, `totalPages`, `number`, `size`.

По очереди отправьте даты с `endDate=startDate`, `endDate` раньше начала и началом в прошлом. Для каждого случая ожидается `400` с JSON `ApiError`. Бронь, начинающаяся в дату выезда предыдущей, должна быть разрешена: используется `[startDate, endDate)`.

### 8. Чужая бронь и отмена

Создайте второго USER, получите его JWT и запросите по нему бронь первого пользователя. GET и DELETE должны вернуть `403`. Владелец выполняет `DELETE /api/booking/{id}` и получает `CANCELLED`. Повторный DELETE не должен дважды уменьшить `timesBooked`.

### 9. Конкурентность

Подготовьте два разных `requestId` для одной комнаты и пересекающегося интервала. Отправьте запросы одновременно из двух окон PowerShell. Только один должен получить `CONFIRMED`; второй — `409` либо другой номер при `autoSelect=true`. Проверьте в `/api/rooms` и H2, что нет двух активных пересекающихся hold.

### 10. Saga и компенсация

В окне Docker выполните `docker compose stop hotel-service`. Создайте новую бронь с новым `requestId`, дождитесь timeout и retry, затем проверьте `503`/ошибку недоступности. В логах найдите `PENDING`, retry, `CANCELLED` и release. Выполните `docker compose start hotel-service` и повторите тот же `requestId`: дубль не должен появиться.

### 11. H2 и логи

Откройте H2 Console: http://localhost:8081/h2-console и http://localhost:8082/h2-console. Используйте JDBC URL `jdbc:h2:mem:bookingdb` и `jdbc:h2:mem:hoteldb`, пользователя `sa`, пустой пароль. Проверьте таблицы `USERS`, `BOOKINGS`, `HOTELS`, `ROOMS`, `ROOM_HOLDS`, статусы, `request_id`, даты и `TIMES_BOOKED`.

Отправьте запрос с `X-Correlation-Id`:

```powershell
$traceHeaders = @{ Authorization="Bearer $USER_TOKEN"; 'X-Correlation-Id'='manual-trace-001' }
Invoke-WebRequest http://localhost:8080/api/hotels -Headers $traceHeaders
```

Найдите `manual-trace-001` в логах Gateway и backend. Для брони проверьте один и тот же `bookingId`, `requestId` и `traceId` в событиях создания, reserve, commit и compensation.

### 12. Проверка закрытия внутренних endpoints

```powershell
try { Invoke-WebRequest -Method Post -Uri http://localhost:8080/api/rooms/1/release -Headers $userHeaders -ContentType 'application/json' -Body '{"requestId":"manual-internal-001"}' -ErrorAction Stop } catch { $_.Exception.Response.StatusCode.value__ }
```

Запрос через Gateway должен быть запрещён. `confirm-availability`, `commit` и `release` доступны только для Booking Service с `X-Internal-Token`.

### 13. Итоговый чек-лист

- Eureka показывает три приложения со статусом `UP`.
- Health и Swagger открываются.
- Без JWT возвращается `401`, USER на ADMIN endpoint получает `403`.
- CRUD отелей и комнат работает по ролям.
- Рекомендации сортируются по `timesBooked`, затем по ID.
- Успешная бронь проходит `PENDING → CONFIRMED`.
- Ошибка сервиса приводит к `PENDING → CANCELLED` и compensation release.
- Повтор `requestId` не создаёт дубль.
- Пересекающиеся параллельные брони не подтверждаются одновременно.
- История поддерживает пагинацию и защищает чужие данные.
- H2 содержит ожидаемые таблицы, связи и ограничения.
- В логах видны `bookingId`, `requestId`, `traceId` и переходы состояний.
- `mvn clean test` завершается с кодом 0.
