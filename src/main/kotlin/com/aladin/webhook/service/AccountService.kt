package com.aladin.webhook.service

import com.aladin.webhook.domain.Account
import com.aladin.webhook.domain.dto.WebhookRequest
import com.aladin.webhook.domain.enum.AccountStatus
import com.aladin.webhook.domain.enum.EventType
import com.aladin.webhook.domain.exception.NotFoundException
import com.aladin.webhook.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(
    private val accountRepository: AccountRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findAccount(accountKey: String): Account =
        accountRepository.findByAccountKey(accountKey)
            ?: throw NotFoundException("Account not found: $accountKey")

    @Transactional
    fun process(request: WebhookRequest) {
        require(request.accountKey.length <= 255) { "accountKey too long" }
        accountRepository.upsert(request.accountKey) // 계정 없으면 생성

        when (EventType.from(request.eventType)) {
            EventType.EMAIL_FORWARDING_CHANGED -> {
                val email =
                    request.data["email"] as? String
                        ?: throw IllegalArgumentException("data.email required")
                require(email.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) { "Invalid email format" }
                accountRepository.updateEmail(request.accountKey, email)
                log.info("Email updated. accountKey={}", request.accountKey)
            }
            EventType.ACCOUNT_DELETED -> {
                accountRepository.updateStatus(request.accountKey, AccountStatus.DELETED)
                log.info("Account deleted. accountKey={}", request.accountKey)
            }
            EventType.APPLE_ACCOUNT_DELETED -> {
                accountRepository.updateStatus(request.accountKey, AccountStatus.APPLE_DELETED)
                log.info("Apple account deleted. accountKey={}", request.accountKey)
            }
        }
    }
}
