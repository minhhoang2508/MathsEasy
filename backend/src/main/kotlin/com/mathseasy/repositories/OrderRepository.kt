package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.mathseasy.models.Order
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class OrderRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_ORDERS)

    suspend fun createOrder(order: Order): Order {
        return try {
            collection.document(order.id).set(order).get()
            logger.info { "Order created: ${order.id}" }
            order
        } catch (e: Exception) {
            logger.error(e) { "Failed to create order: ${e.message}" }
            throw e
        }
    }

    suspend fun getOrderById(id: String): Order? {
        return try {
            val document = collection.document(id).get().get()
            if (document.exists()) {
                document.toObject(Order::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get order: ${e.message}" }
            throw e
        }
    }

    suspend fun getOrderByTransactionId(transactionId: String): Order? {
        return try {
            val querySnapshot = collection
                .whereEqualTo("transactionId", transactionId)
                .limit(1)
                .get()
                .get()
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents[0].toObject(Order::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get order by transactionId: ${e.message}" }
            throw e
        }
    }

    suspend fun updateOrderStatus(
        id: String,
        status: String,
        transactionId: String? = null,
        paidAt: Long? = null
    ): Order? {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            if (transactionId != null) updates["transactionId"] = transactionId
            if (paidAt != null) updates["paidAt"] = paidAt

            collection.document(id).update(updates).get()
            logger.info { "Order $id status updated to $status" }
            getOrderById(id)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update order status: ${e.message}" }
            throw e
        }
    }

    suspend fun getOrdersByUser(uid: String): List<Order> {
        return try {
            val querySnapshot = collection
                .whereEqualTo("uid", uid)
                .get()
                .get()
            querySnapshot.documents.mapNotNull { it.toObject(Order::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get orders for user $uid: ${e.message}" }
            emptyList()
        }
    }
}
