package com.aladin.webhook.repository

import com.aladin.webhook.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<Account, Long> {
    fun findByAccountKey(accountKey: String): Account?
}
