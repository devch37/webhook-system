package com.aladin.webhook.repository

import com.aladin.webhook.domain.Account
import com.aladin.webhook.domain.AccountStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

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
