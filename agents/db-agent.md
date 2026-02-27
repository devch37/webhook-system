# 🗄️ DB Agent

## 역할
SQLite3 schema.sql DDL + JdbcTemplate 기반 Repository 구현.

---

## build.gradle.kts 전체

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/domain/**", "**/config/**", "**/*Application*") }
    }))
}

tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.80".toBigDecimal() } } }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
```

---

## schema.sql

```sql
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
PRAGMA busy_timeout=5000;

CREATE TABLE IF NOT EXISTS accounts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    account_key  TEXT    NOT NULL UNIQUE,
    email        TEXT,
    status       TEXT    NOT NULL DEFAULT 'ACTIVE'
                         CHECK(status IN ('ACTIVE','DELETED','APPLE_DELETED')),
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS webhook_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id      TEXT    NOT NULL UNIQUE,
    event_type    TEXT    NOT NULL,
    payload       TEXT    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'RECEIVED'
                          CHECK(status IN ('RECEIVED','PROCESSING','DONE','FAILED')),
    error_message TEXT,
    created_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status);
CREATE INDEX IF NOT EXISTS idx_accounts_key ON accounts(account_key);
```

---

## domain/WebhookEvent.kt

```kotlin
data class WebhookEvent(
    val id: Long = 0,
    val eventId: String,
    val eventType: String,
    val payload: String,
    val status: EventStatus = EventStatus.RECEIVED,
    val errorMessage: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)
```

## domain/Account.kt

```kotlin
data class Account(
    val id: Long = 0,
    val accountKey: String,
    val email: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: String = "",
    val updatedAt: String = "",
)
```

## domain/Enums.kt

```kotlin
enum class EventType {
    EMAIL_FORWARDING_CHANGED,
    ACCOUNT_DELETED,
    APPLE_ACCOUNT_DELETED;

    companion object {
        fun from(value: String) = entries.find { it.name == value }
            ?: throw IllegalArgumentException("Unknown event type: $value")
    }
}

enum class EventStatus { RECEIVED, PROCESSING, DONE, FAILED }

enum class AccountStatus { ACTIVE, DELETED, APPLE_DELETED }
```

## domain/dto/WebhookRequest.kt

```kotlin
data class WebhookRequest(
    val accountKey: String,
    val eventType: String,
    val data: Map<String, Any> = emptyMap(),
)
```

---

## config/DatabaseConfig.kt

```kotlin
@Configuration
class DatabaseConfig {
    @Bean
    fun dataSource(@Value("\${spring.datasource.url}") url: String): DataSource =
        SQLiteDataSource().apply {
            this.url = url
            config.apply {
                setBusyTimeout(5000)
                setJournalMode("WAL")
                setSynchronous("NORMAL")
            }
        }
}
```

---

## repository/WebhookEventRepository.kt

```kotlin
@Repository
class WebhookEventRepository(private val jdbc: JdbcTemplate) {

    /** true = 신규 저장, false = 중복 */
    fun insertIfNotExists(eventId: String, eventType: String, payload: String): Boolean =
        jdbc.update("""
            INSERT OR IGNORE INTO webhook_events (event_id, event_type, payload, status)
            VALUES (?, ?, ?, 'RECEIVED')
        """, eventId, eventType, payload) > 0

    fun findByEventId(eventId: String): WebhookEvent? =
        runCatching {
            jdbc.queryForObject(
                "SELECT * FROM webhook_events WHERE event_id = ?",
                rowMapper, eventId
            )
        }.getOrNull()

    fun updateStatus(eventId: String, status: EventStatus) {
        jdbc.update("""
            UPDATE webhook_events
            SET status = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE event_id = ?
        """, status.name, eventId)
    }

    fun updateFailed(eventId: String, reason: String) {
        jdbc.update("""
            UPDATE webhook_events
            SET status = 'FAILED',
                error_message = ?,
                updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE event_id = ?
        """, reason.take(1000), eventId)
    }

    private val rowMapper = RowMapper { rs, _ ->
        WebhookEvent(
            id           = rs.getLong("id"),
            eventId      = rs.getString("event_id"),
            eventType    = rs.getString("event_type"),
            payload      = rs.getString("payload"),
            status       = EventStatus.valueOf(rs.getString("status")),
            errorMessage = rs.getString("error_message"),
            createdAt    = rs.getString("created_at"),
            updatedAt    = rs.getString("updated_at"),
        )
    }
}
```

---

## repository/AccountRepository.kt

```kotlin
@Repository
class AccountRepository(private val jdbc: JdbcTemplate) {

    fun findByAccountKey(key: String): Account? =
        runCatching {
            jdbc.queryForObject(
                "SELECT * FROM accounts WHERE account_key = ?",
                rowMapper, key
            )
        }.getOrNull()

    fun upsert(accountKey: String) {
        jdbc.update("""
            INSERT INTO accounts (account_key) VALUES (?)
            ON CONFLICT(account_key) DO NOTHING
        """, accountKey)
    }

    fun updateEmail(accountKey: String, email: String) {
        jdbc.update("""
            UPDATE accounts
            SET email = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE account_key = ?
        """, email, accountKey)
    }

    fun updateStatus(accountKey: String, status: AccountStatus) {
        jdbc.update("""
            UPDATE accounts
            SET status = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE account_key = ?
        """, status.name, accountKey)
    }

    private val rowMapper = RowMapper { rs, _ ->
        Account(
            id         = rs.getLong("id"),
            accountKey = rs.getString("account_key"),
            email      = rs.getString("email"),
            status     = AccountStatus.valueOf(rs.getString("status")),
            createdAt  = rs.getString("created_at"),
            updatedAt  = rs.getString("updated_at"),
        )
    }
}
```

---

## 호출 커맨드

```bash
claude "agents/db-agent.md 파일을 읽고 지시대로 구현해줘.
build.gradle.kts 전체, schema.sql, DatabaseConfig.kt,
domain 모델 (WebhookEvent, Account, Enum, DTO),
WebhookEventRepository.kt, AccountRepository.kt 생성.
완료 후 prompts/used_prompts.md 에 #2번으로 기록해줘."
```