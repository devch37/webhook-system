package com.aladin.webhook.config

import org.sqlite.SQLiteDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    @Bean
    fun dataSource(@Value("\${spring.datasource.url}") url: String): DataSource =
        SQLiteDataSource().apply {
            this.url = url
            config.apply {
                setBusyTimeout(5000)
                setJournalMode("WAL")
                setSynchronous("NORMAL")
            }
        }
}
