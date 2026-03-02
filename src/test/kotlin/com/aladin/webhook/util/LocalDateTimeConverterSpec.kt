package com.aladin.webhook.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

class LocalDateTimeConverterSpec :
    DescribeSpec({
        val converter = LocalDateTimeConverter()

        describe("convertToDatabaseColumn") {
            it("null → null 반환") {
                converter.convertToDatabaseColumn(null) shouldBe null
            }

            it("LocalDateTime → 'yyyy-MM-dd HH:mm:ss.SSS' 포맷 문자열") {
                val dt = LocalDateTime.of(2024, 6, 15, 13, 30, 45, 123_000_000)
                converter.convertToDatabaseColumn(dt) shouldBe "2024-06-15 13:30:45.123"
            }
        }

        describe("convertToEntityAttribute") {
            it("null → null 반환") {
                converter.convertToEntityAttribute(null) shouldBe null
            }

            it("밀리초 포함 문자열 → LocalDateTime 파싱") {
                val result = converter.convertToEntityAttribute("2024-06-15 13:30:45.123")
                result shouldNotBe null
                result!!.year shouldBe 2024
                result.hour shouldBe 13
                result.nano shouldBe 123_000_000
            }

            it("밀리초 없는 문자열 → LocalDateTime 파싱 (기존 레코드 호환)") {
                val result = converter.convertToEntityAttribute("2024-06-15 13:30:45")
                result shouldNotBe null
                result!!.second shouldBe 45
                result.nano shouldBe 0
            }
        }
    })
