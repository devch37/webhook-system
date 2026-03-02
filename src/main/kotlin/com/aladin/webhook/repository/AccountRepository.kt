package com.aladin.webhook.repository

import com.aladin.webhook.domain.Account
import com.aladin.webhook.domain.enum.AccountStatus
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(
    private val accountJpaRepository: AccountJpaRepository,
) {
    fun findByAccountKey(key: String): Account? = accountJpaRepository.findByAccountKey(key)

    fun upsert(accountKey: String) {
        if (accountJpaRepository.findByAccountKey(accountKey) == null) {
            accountJpaRepository.save(Account(accountKey = accountKey))
        }
    }

    fun updateEmail(
        accountKey: String,
        email: String,
    ) {
        val account = accountJpaRepository.findByAccountKey(accountKey) ?: return
        accountJpaRepository.save(account.copy(email = email))
    }

    fun updateStatus(
        accountKey: String,
        status: AccountStatus,
    ) {
        val account = accountJpaRepository.findByAccountKey(accountKey) ?: return
        accountJpaRepository.save(account.copy(status = status))
    }
}
