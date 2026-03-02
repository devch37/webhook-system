package com.aladin.webhook.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

@Configuration
@EnableJpaAuditing
class DatabaseConfig {
    @Bean
    fun dataSource(
        @Value("\${spring.datasource.url}") url: String,
    ): DataSource =
        SQLiteDataSource().apply {
            val path = url.removePrefix("jdbc:sqlite:")
            val isFileDb = !path.startsWith(":") && !path.startsWith("file:")
            if (isFileDb) {
                java.io
                    .File(path)
                    .parentFile
                    ?.mkdirs()
            }
            this.url = url
            config.apply {
                transactionMode = SQLiteConfig.TransactionMode.IMMEDIATE
                busyTimeout = 5000
                if (isFileDb) {
                    setJournalMode("WAL")
                    setSynchronous("NORMAL")
                }
            }
        }
}
