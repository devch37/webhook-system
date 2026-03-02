package com.aladin.webhook.domain

import com.aladin.webhook.domain.enum.AccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "accounts")
class Account(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "account_key", unique = true, nullable = false)
    val accountKey: String,
    @Column
    var email: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AccountStatus = AccountStatus.ACTIVE,
) : BaseEntity()
