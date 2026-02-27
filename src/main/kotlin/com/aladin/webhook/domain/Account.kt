package com.aladin.webhook.domain

import com.aladin.webhook.domain.enum.AccountStatus

data class Account(
    val id: Long = 0,
    val accountKey: String,
    val email: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: String = "",
    val updatedAt: String = "",
)
