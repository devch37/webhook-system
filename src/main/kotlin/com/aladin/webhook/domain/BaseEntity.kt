package com.aladin.webhook.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
    @EntityListeners(AuditingEntityListener::class)
    abstract class BaseEntity {
        @Column(name = "created_at", nullable = false ,insertable = false, updatable = false)
        var createdAt: LocalDateTime = LocalDateTime.now()
            protected set

        @LastModifiedDate
        @Column(name = "updated_at", nullable = false)
        var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set
}
