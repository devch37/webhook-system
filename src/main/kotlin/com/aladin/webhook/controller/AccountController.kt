package com.aladin.webhook.controller

import com.aladin.webhook.controller.dto.response.AccountResponse
import com.aladin.webhook.controller.dto.response.toResponse
import com.aladin.webhook.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val accountService: AccountService,
) {
    @GetMapping("/{accountKey}")
    fun getAccount(
        @PathVariable accountKey: String,
    ): ResponseEntity<AccountResponse> {
        val account = accountService.findAccount(accountKey)
        return ResponseEntity.ok(account.toResponse())
    }
}
