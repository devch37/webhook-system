package com.aladin.webhook.repository

import com.aladin.webhook.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountJpaRepository : JpaRepository<Account, Long> {
    fun findByAccountKey(accountKey: String): Account?

    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE accounts SET email = :email, updated_at = strftime('%Y-%m-%d %H:%M:%S','now') WHERE account_key = :accountKey",
        nativeQuery = true,
    )
    fun updateEmail(
        @Param("accountKey") accountKey: String,
        @Param("email") email: String,
    )

    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE accounts SET status = :status, updated_at = strftime('%Y-%m-%d %H:%M:%S','now') WHERE account_key = :accountKey",
        nativeQuery = true,
    )
    fun updateStatus(
        @Param("accountKey") accountKey: String,
        @Param("status") status: String,
    )
}
