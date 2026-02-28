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
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "accounts")
data class Account(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "account_key", unique = true, nullable = false)
    val accountKey: String,
    @Column
    val email: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AccountStatus = AccountStatus.ACTIVE,
    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
