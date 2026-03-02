package com.aladin.webhook.controller.dto.response

import com.aladin.webhook.domain.Account
import java.time.LocalDateTime

data class AccountResponse(
    val accountKey: String,
    val email: String?,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun Account.toResponse() = AccountResponse(accountKey, email, status.name, createdAt, updatedAt)
