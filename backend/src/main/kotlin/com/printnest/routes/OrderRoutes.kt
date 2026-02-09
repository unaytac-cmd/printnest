package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.OrderService
import com.printnest.domain.service.FetchOrderService
import com.printnest.domain.service.OrderExportService
import com.printnest.domain.service.LabelService
import com.printnest.domain.service.ExcelImportService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.orderRoutes() {
    val orderService: OrderService = GlobalContext.get().get()
    val fetchOrderService: FetchOrderService = GlobalContext.get().get()
    val orderExportService: OrderExportService = GlobalContext.get().get()
    val labelService: LabelService = GlobalContext.get().get()
    val excelImportService: ExcelImportService = GlobalContext.get().get()

    // =====================================================
    // ORDERS - LISTING & DETAIL
    // =====================================================

    route("/orders") {
        // GET /api/v1/orders/product-selection-data - Product selection data for manual orders
        get("/product-selection-data") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val data = orderService.getProductSelectionData(tenantId)
            call.respond(data)
        }

        // POST /api/v1/orders/import-excel - Import orders from Excel file
        post("/import-excel") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val storeId = call.request.queryParameters["storeId"]?.toLongOrNull()

            val multipart = call.receiveMultipart()
            var result: ExcelImportResult? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: "upload.xlsx"
                        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                            result = excelImportService.importOrdersFromExcel(
                                tenantId = tenantId,
                                userId = userId,
                                storeId = storeId,
                                inputStream = part.streamProvider()
                            )
                        } else {
                            result = ExcelImportResult(
                                success = false,
                                message = "Invalid file format. Please upload an Excel file (.xlsx or .xls)"
                            )
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            call.respond(result ?: ExcelImportResult(
                success = false,
                message = "No file provided"
            ))
        }

        // GET /api/v1/orders - List all orders
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val filters = OrderFiltersExtended(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                statuses = call.request.queryParameters["statuses"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() },
                mapStatus = call.request.queryParameters["mapStatus"]?.toIntOrNull(),
                storeId = call.request.queryParameters["storeId"]?.toLongOrNull(),
                shipstationStoreId = call.request.queryParameters["shipstationStoreId"]?.toLongOrNull(),
                userId = call.request.queryParameters["userId"]?.toLongOrNull(),
                search = call.request.queryParameters["search"],
                sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = orderService.getOrders(tenantId, filters)
            call.respond(response)
        }

        // POST /api/v1/orders - Create new order (Step 1)
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CreateOrderRequest>()

            orderService.createOrder(tenantId, userId, request)
                .onSuccess { order ->
                    call.respond(HttpStatusCode.Created, order)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/orders/{id} - Get order by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val withProducts = call.request.queryParameters["withProducts"]?.toBoolean() ?: true
            val order = orderService.getOrder(id, tenantId, withProducts)

            if (order != null) {
                call.respond(order)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))
            }
        }

        // GET /api/v1/orders/{id}/history - Get order history
        get("/{id}/history") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            // Verify order belongs to tenant
            val order = orderService.getOrder(id, tenantId, false)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))

            val history = orderService.getOrderHistory(id)
            call.respond(mapOf("history" to history))
        }

        // =====================================================
        // STEP 2 - EDIT ORDER
        // =====================================================

        // PUT /api/v1/orders/{id}/step2 - Update order products and address
        put("/{id}/step2") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<UpdateOrderStep2Request>()

            orderService.updateOrderStep2(id, tenantId, userId, request)
                .onSuccess { order ->
                    call.respond(order)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // STEP 3 - PRICE CALCULATION & PAYMENT
        // =====================================================

        // GET /api/v1/orders/{id}/step3 - Calculate prices
        get("/{id}/step3") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            orderService.calculateStep3Price(id, tenantId, userId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/step3/shipping - Select shipping method
        post("/{id}/step3/shipping") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<SelectShippingRequest>()

            orderService.selectShipping(id, tenantId, request)
                .onSuccess { summary ->
                    call.respond(summary)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/step3/pay - Process payment
        post("/{id}/step3/pay") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<PaymentRequest>()

            orderService.processPayment(id, tenantId, userId, request)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // STEP 4 - CONFIRMATION
        // =====================================================

        // GET /api/v1/orders/{id}/step4 - Get confirmation view
        get("/{id}/step4") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            orderService.getStep4(id, tenantId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/step4/confirm - Confirm order for production
        post("/{id}/step4/confirm") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<ConfirmOrderRequest>()

            orderService.confirmOrder(id, tenantId, userId, request)
                .onSuccess { order ->
                    call.respond(order)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // ORDER ACTIONS
        // =====================================================

        // PUT /api/v1/orders/{id}/status - Change order status
        put("/{id}/status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<ChangeStatusRequest>()

            orderService.changeOrderStatus(id, tenantId, userId, request)
                .onSuccess { order ->
                    call.respond(order)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/ship - Mark order as shipped
        post("/{id}/ship") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<ShipOrderRequest>()

            orderService.markAsShipped(id, tenantId, userId, request.trackingNumber, request.trackingUrl)
                .onSuccess { order ->
                    call.respond(order)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/orders/{id} - Delete order (soft delete)
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            orderService.deleteOrder(id, tenantId, userId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // BULK ACTIONS
        // =====================================================

        // POST /api/v1/orders/bulk-status - Bulk update order status
        post("/bulk-status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<BulkStatusUpdateRequest>()

            if (request.orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No orders provided"))
            }

            orderService.bulkUpdateStatus(tenantId, userId, request.orderIds, request.status)
                .onSuccess { count ->
                    call.respond(mapOf("success" to true, "message" to "Order status updated successfully", "updatedCount" to count))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // POST /api/v1/orders/combine - Combine multiple orders
        post("/combine") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CombineOrdersRequest>()

            orderService.combineOrders(tenantId, userId, request.orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/cancel - Cancel order
        post("/{id}/cancel") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val refundLabel = call.request.queryParameters["refundLabel"]?.toBoolean() ?: false

            orderService.cancelOrder(tenantId, userId, id, refundLabel)
                .onSuccess {
                    call.respond(mapOf("success" to true, "message" to "Order cancelled successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // =====================================================
        // TRACKING & SHIPPING ACTIONS
        // =====================================================

        // PUT /api/v1/orders/{id}/tracking - Update tracking code
        put("/{id}/tracking") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<UpdateTrackingRequest>()

            orderService.updateTrackingCode(tenantId, id, request.trackingCode)
                .onSuccess {
                    call.respond(mapOf("success" to true, "message" to "Tracking code updated successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // GET /api/v1/orders/{id}/shipping-methods - Get available shipping methods
        get("/{id}/shipping-methods") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            orderService.getShippingMethods(tenantId, id)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // PUT /api/v1/orders/{id}/shipping-method - Set shipping method
        put("/{id}/shipping-method") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<SetShippingMethodRequest>()

            orderService.setShippingMethod(tenantId, id, request.rateId, request.methodName)
                .onSuccess {
                    call.respond(mapOf("success" to true, "message" to "Shipping method updated"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // POST /api/v1/orders/{id}/upload-label - Upload custom label
        post("/{id}/upload-label") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<UploadLabelRequest>()

            orderService.uploadCustomLabel(tenantId, id, request.labelUrl)
                .onSuccess {
                    call.respond(mapOf("success" to true, "message" to "Label uploaded successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // =====================================================
        // PACKING SLIPS & EXPORTS
        // =====================================================

        // POST /api/v1/orders/packing-slips - Create packing slips
        post("/packing-slips") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CreatePackingSlipsRequest>()

            val orderIds = orderExportService.resolveOrderIds(tenantId, request.orderIds, request.batchId)

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No order IDs provided"))
            }

            orderExportService.createPackingSlips(
                tenantId = tenantId,
                userId = userId,
                orderIds = orderIds,
                includeLabels = request.includeLabels,
                groupByModification = request.groupByModification
            ).onSuccess { response ->
                call.respond(response)
            }.onFailure { error ->
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "success" to false,
                    "message" to (error.message ?: "Failed to create packing slips")
                ))
            }
        }

        // POST /api/v1/orders/gangsheet-products - Get products for gangsheet
        post("/gangsheet-products") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderIds = call.receive<List<Long>>()

            val products = orderService.getProductsForGangsheet(tenantId, orderIds)
            call.respond(mapOf("products" to products))
        }

        // =====================================================
        // REFUNDS
        // =====================================================

        // POST /api/v1/orders/{id}/refund - Process partial refund
        post("/{id}/refund") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<UpdateRefundRequest>()

            orderService.updateRefundAmount(tenantId, userId, id, request.refundAmount)
                .onSuccess {
                    call.respond(mapOf("success" to true, "message" to "Refund processed successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // =====================================================
        // LABEL REFUND & TRACKING
        // =====================================================

        /**
         * POST /api/v1/orders/{id}/request-refund
         *
         * Request a refund for the shipping label on this order.
         * Checks the label provider (EasyPost or USPS) and calls the appropriate refund API.
         */
        post("/{id}/request-refund") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            labelService.requestLabelRefund(tenantId, id)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to request refund")
                    ))
                }
        }

        /**
         * POST /api/v1/orders/{id}/send-tracking
         *
         * Send tracking information to the marketplace where the order originated.
         * Updates order_info with sent_to_marketplace = true upon success.
         */
        post("/{id}/send-tracking") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            labelService.sendTrackingToMarketplace(tenantId, id)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to send tracking")
                    ))
                }
        }

        // =====================================================
        // ANALYTICS
        // =====================================================

        // POST /api/v1/orders/analytics - Get order analytics
        post("/analytics") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<OrderAnalyticsRequest>()

            val analytics = orderService.getOrderAnalytics(
                tenantId,
                request.userId,
                request.storeId,
                request.startDate,
                request.endDate
            )

            call.respond(OrderAnalyticsResponse(success = true, orders = analytics))
        }

        // =====================================================
        // FETCH & SYNC ORDERS
        // =====================================================

        // POST /api/v1/orders/fetch - Fetch single order from marketplace
        post("/fetch") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<FetchOrderRequest>()

            fetchOrderService.fetchOrderFromShipStation(tenantId, userId, request.storeId, request.intOrderId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to error.message))
                }
        }

        // POST /api/v1/orders/sync - Sync orders from marketplace
        post("/sync") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<SyncOrdersRequest>()

            val response = fetchOrderService.syncOrders(
                tenantId,
                userId,
                request.storeId,
                request.lastDays
            )

            call.respond(response)
        }

        // =====================================================
        // EXPORT ORDERS
        // =====================================================

        // POST /api/v1/orders/export - Export orders to Excel
        post("/export") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportOrdersRequest>()

            val orderIds = orderExportService.resolveOrderIds(tenantId, request.orderIds, request.batchId)

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No order IDs provided"))
            }

            orderExportService.exportOrdersToExcel(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to export orders")
                    ))
                }
        }

        // POST /api/v1/orders/export-labels - Export shipping labels
        post("/export-labels") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportLabelsRequest>()

            val orderIds = orderExportService.resolveOrderIds(tenantId, request.orderIds, request.batchId)

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No order IDs provided"))
            }

            orderExportService.exportShippingLabels(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to export labels")
                    ))
                }
        }

        // POST /api/v1/orders/download-designs - Download designs as ZIP
        post("/download-designs") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<DownloadDesignsRequest>()

            val orderIds = orderExportService.resolveOrderIds(tenantId, request.orderIds, request.batchId)

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No order IDs provided"))
            }

            orderExportService.downloadDesigns(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to download designs")
                    ))
                }
        }

        // POST /api/v1/orders/export-oos - Export out of stock products
        post("/export-oos") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportOutOfStockRequest>()

            val orderIds = orderExportService.resolveOrderIds(tenantId, request.orderIds, request.batchId)

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No order IDs provided"))
            }

            orderExportService.exportOutOfStockProducts(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Failed to export out of stock products")
                    ))
                }
        }
    }
}
