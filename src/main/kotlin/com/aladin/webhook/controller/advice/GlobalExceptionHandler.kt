package com.aladin.webhook.controller.advice

import com.aladin.webhook.domain.exception.NotFoundException
import com.aladin.webhook.domain.exception.SignatureVerificationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(SignatureVerificationException::class)
    fun handleUnauthorized(e: SignatureVerificationException) =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("code" to "UNAUTHORIZED", "message" to (e.message ?: "Unauthorized")))

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException) =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(mapOf("code" to "NOT_FOUND", "message" to (e.message ?: "Not found")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException) =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "BAD_REQUEST", "message" to (e.message ?: "Bad request")))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException) =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "MISSING_HEADER", "message" to "Required header: ${e.headerName}"))

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unexpected error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("code" to "INTERNAL_ERROR", "message" to "Internal server error"))
    }
}
