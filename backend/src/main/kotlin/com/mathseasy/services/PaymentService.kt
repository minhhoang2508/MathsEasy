package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.OrderRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.Constants
import com.mathseasy.utils.getCurrentTimestamp
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class PaymentService {
    private val orderRepository = OrderRepository()
    private val userRepository = UserRepository()

    /**
     * Generate a random order ID in format: MATHXXXXXX
     */
    private fun generateOrderId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomPart = (1..6).map { chars.random() }.joinToString("")
        return "MATH$randomPart"
    }

    /**
     * Build SePay QR URL for payment
     */
    private fun buildQrUrl(orderId: String, amount: Long): String {
        return "https://qr.sepay.vn/img?acc=${Constants.SEPAY_ACCOUNT}&bank=${Constants.SEPAY_BANK}&amount=$amount&des=$orderId"
    }

    /**
     * Create a new payment order
     */
    suspend fun createOrder(uid: String, amount: Long, description: String): CreateOrderResponse {
        val orderId = generateOrderId()

        val order = Order(
            id = orderId,
            uid = uid,
            amount = amount,
            status = Constants.ORDER_STATUS_PENDING,
            createdAt = getCurrentTimestamp(),
            description = description
        )

        orderRepository.createOrder(order)

        val qrUrl = buildQrUrl(orderId, amount)

        logger.info { "Order created: $orderId for user $uid, amount: $amount VND" }

        return CreateOrderResponse(
            orderId = orderId,
            amount = amount,
            qrUrl = qrUrl,
            description = description
        )
    }

    /**
     * Process SePay webhook payload
     */
    suspend fun processWebhook(payload: SepayWebhookPayload): Boolean {
        logger.info { "Processing webhook - transactionId: ${payload.id}, content: ${payload.content}, amount: ${payload.transferAmount}" }

        logger.info { "Full webhook payload: gateway=${payload.gateway}, accountNumber=${payload.accountNumber}, transferType=${payload.transferType}, referenceCode=${payload.referenceCode}" }

        val transactionId = payload.id.toString()

        val existingOrder = orderRepository.getOrderByTransactionId(transactionId)
        if (existingOrder != null) {
            logger.info { "Transaction $transactionId already processed for order ${existingOrder.id}, skipping" }
            return true
        }

        val orderIdPattern = Regex("MATH[A-Z0-9]{6}")
        val orderIdMatch = orderIdPattern.find(payload.content.uppercase())

        if (orderIdMatch == null) {
            logger.warn { "No order ID found in webhook content: '${payload.content}'" }
            return false
        }

        val orderId = orderIdMatch.value

        val order = orderRepository.getOrderById(orderId)
        if (order == null) {
            logger.warn { "Order not found: $orderId" }
            return false
        }

        if (order.status != Constants.ORDER_STATUS_PENDING) {
            logger.info { "Order $orderId is not pending (status: ${order.status}), skipping" }
            return true
        }

        if (payload.transferAmount < order.amount) {
            logger.warn { "Amount mismatch for order $orderId: expected ${order.amount}, received ${payload.transferAmount}" }
            return false
        }

        orderRepository.updateOrderStatus(
            id = orderId,
            status = Constants.ORDER_STATUS_PAID,
            transactionId = transactionId,
            paidAt = getCurrentTimestamp()
        )

        try {
            val allPremiumTopics = listOf("exponents_logarithms", "sequences")
            unlockPremiumTopics(order.uid, allPremiumTopics)
            logger.info { "Premium topics '$allPremiumTopics' unlocked for user ${order.uid}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to unlock premium topics for user ${order.uid}: ${e.message}" }
        }

        logger.info { "Payment confirmed for order $orderId, transactionId: $transactionId" }
        return true
    }

    /**
     * Unlock a list of premium topics for a user
     */
    private suspend fun unlockPremiumTopics(uid: String, topics: List<String>) {
        val user = userRepository.getUserById(uid)
            ?: throw IllegalStateException("User not found: $uid")

        val currentTopics = user.premiumTopics.toMutableSet()
        val originalSize = currentTopics.size
        currentTopics.addAll(topics)
        
        if (currentTopics.size > originalSize) {
            userRepository.updateUser(uid, mapOf("premiumTopics" to currentTopics.toList()))
            logger.info { "Added premium topics $topics for user $uid. Total premium topics: $currentTopics" }
        } else {
            logger.info { "User $uid already has premium topics $topics" }
        }
    }

    /**
     * Get order status
     */
    suspend fun getOrderStatus(orderId: String): OrderStatusResponse? {
        val order = orderRepository.getOrderById(orderId) ?: return null

        return OrderStatusResponse(
            orderId = order.id,
            status = order.status,
            amount = order.amount,
            paidAt = order.paidAt
        )
    }

    /**
     * Get orders for a user
     */
    suspend fun getUserOrders(uid: String): List<Order> {
        return orderRepository.getOrdersByUser(uid)
    }
}
