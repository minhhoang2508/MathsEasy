package com.mathseasy.models

import kotlinx.serialization.Serializable

/**
 * Order model for payment processing
 */
@Serializable
data class Order(
    val id: String = "",              // e.g. "MATH-8F3K2L"
    val uid: String = "",             // Firebase user ID
    val amount: Long = 0,             // Amount in VND
    val status: String = "pending",   // pending | paid | failed
    val createdAt: Long = 0L,
    val paidAt: Long? = null,
    val transactionId: String? = null,
    val description: String = ""      // Product description
)

/**
 * Request/Response DTOs
 */
@Serializable
data class CreateOrderRequest(
    val amount: Long,
    val description: String = "Unlock Premium"
)

@Serializable
data class CreateOrderResponse(
    val orderId: String,
    val amount: Long,
    val qrUrl: String,
    val description: String
)

@Serializable
data class OrderStatusResponse(
    val orderId: String,
    val status: String,
    val amount: Long,
    val paidAt: Long? = null
)

/**
 * SePay webhook payload
 */
@Serializable
data class SepayWebhookPayload(
    val id: Long = 0,
    val gateway: String = "",
    val transactionDate: String = "",
    val accountNumber: String = "",
    val code: String? = null,
    val content: String = "",
    val transferType: String = "",
    val transferAmount: Long = 0,
    val accumulated: Long = 0,
    val subAccount: String? = null,
    val referenceCode: String = "",
    val description: String = ""
)

/**
 * SePay webhook response
 */
@Serializable
data class SepayWebhookResponse(
    val success: Boolean,
    val message: String
)
