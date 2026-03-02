package com.aladin.webhook.util

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@Converter(autoApply = true)
class LocalDateTimeConverter : AttributeConverter<LocalDateTime, String> {
    companion object {
        // 쓰기: "yyyy-MM-dd HH:mm:ss.SSS" — schema.sql strftime('%Y-%m-%d %H:%M:%f') 포맷과 통일
        private val WRITE_FORMATTER =
            DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
                .toFormatter()

        // 읽기: 밀리초 없는 기존 레코드("yyyy-MM-dd HH:mm:ss")도 파싱 가능
        private val READ_FORMATTER =
            DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.MILLI_OF_SECOND, 1, 3, true)
                .optionalEnd()
                .toFormatter()
    }

    override fun convertToDatabaseColumn(attribute: LocalDateTime?): String? = attribute?.format(WRITE_FORMATTER)

    override fun convertToEntityAttribute(dbData: String?): LocalDateTime? = dbData?.let { LocalDateTime.parse(it, READ_FORMATTER) }
}
