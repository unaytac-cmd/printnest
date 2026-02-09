package com.printnest.integrations.shopify

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP Client for Shopify Admin API
 *
 * Supports both REST API and GraphQL Admin API
 * - REST API: Products, Webhooks
 * - GraphQL API: Orders, Fulfillments (more efficient for complex queries)
 */
class ShopifyClient(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShopifyClient::class.java)

    companion object {
        const val API_VERSION = "2024-01"
        private const val MAX_ORDERS_PER_PAGE = 100
    }

    /**
     * Get the base URL for REST API calls
     */
    private fun getRestBaseUrl(shop: String): String {
        return "https://$shop/admin/api/$API_VERSION"
    }

    /**
     * Get the GraphQL endpoint URL
     */
    private fun getGraphQLUrl(shop: String): String {
        return "https://$shop/admin/api/$API_VERSION/graphql.json"
    }

    // =====================================================
    // ORDER OPERATIONS (GraphQL)
    // =====================================================

    /**
     * Fetch orders from Shopify using GraphQL
     *
     * @param shop Shop domain
     * @param accessToken Store access token
     * @param filterQuery GraphQL filter query (e.g., "financial_status:PAID AND fulfillment_status:UNFULFILLED")
     * @param cursor Pagination cursor for next page
     * @return Result with orders data
     */
    suspend fun getOrders(
        shop: String,
        accessToken: String,
        filterQuery: String? = null,
        cursor: String? = null
    ): Result<ShopifyGraphQLResponse<ShopifyOrdersData>> {
        val query = """
            query getOrders(${"$"}cursor: String, ${"$"}filterQuery: String) {
              shop {
                id
                email
              }
              orders(first: $MAX_ORDERS_PER_PAGE, after: ${"$"}cursor, query: ${"$"}filterQuery) {
                edges {
                  node {
                    id
                    name
                    displayFinancialStatus
                    displayFulfillmentStatus
                    fulfillmentOrders(first: 1) {
                      edges {
                        node {
                          id
                          status
                        }
                      }
                    }
                    customer {
                      id
                      email
                    }
                    shippingAddress {
                      address1
                      address2
                      city
                      country
                      countryCode
                      countryCodeV2
                      name
                      phone
                      province
                      provinceCode
                      zip
                      company
                    }
                    billingAddress {
                      address1
                      address2
                      city
                      country
                      countryCode
                      countryCodeV2
                      name
                      phone
                      province
                      provinceCode
                      zip
                      company
                    }
                    billingAddressMatchesShippingAddress
                    lineItems(first: 100) {
                      edges {
                        node {
                          id
                          title
                          quantity
                          sku
                          variant {
                            displayName
                            title
                            selectedOptions {
                              name
                              value
                            }
                          }
                          variantTitle
                          product {
                            id
                            title
                          }
                          image {
                            src
                          }
                        }
                      }
                    }
                    metafields(first: 100) {
                      nodes {
                        id
                        namespace
                        key
                        updatedAt
                        jsonValue
                      }
                    }
                    currentShippingPriceSet {
                      presentmentMoney {
                        amount
                        currencyCode
                      }
                      shopMoney {
                        amount
                        currencyCode
                      }
                    }
                    createdAt
                    confirmationNumber
                  }
                  cursor
                }
                pageInfo {
                  hasNextPage
                  hasPreviousPage
                  startCursor
                  endCursor
                }
              }
            }
        """.trimIndent()

        val variables = buildMap {
            cursor?.let { put("cursor", it) }
            filterQuery?.let { put("filterQuery", it) }
        }

        return executeGraphQL(shop, accessToken, query, variables)
    }

    /**
     * Get a single order by ID using GraphQL
     */
    suspend fun getOrder(
        shop: String,
        accessToken: String,
        orderId: String
    ): Result<ShopifyGraphQLResponse<ShopifyOrdersData>> {
        // Format order ID as GraphQL ID if numeric
        val graphQLId = if (orderId.startsWith("gid://")) {
            orderId
        } else {
            "gid://shopify/Order/$orderId"
        }

        val query = """
            query getOrder(${"$"}id: ID!) {
              order(id: ${"$"}id) {
                id
                name
                displayFinancialStatus
                displayFulfillmentStatus
                fulfillmentOrders(first: 10) {
                  edges {
                    node {
                      id
                      status
                    }
                  }
                }
                customer {
                  id
                  email
                }
                shippingAddress {
                  address1
                  address2
                  city
                  country
                  countryCode
                  name
                  phone
                  province
                  provinceCode
                  zip
                  company
                }
                lineItems(first: 100) {
                  edges {
                    node {
                      id
                      title
                      quantity
                      sku
                      variant {
                        displayName
                        title
                        selectedOptions {
                          name
                          value
                        }
                      }
                      image {
                        src
                      }
                    }
                  }
                }
                createdAt
              }
            }
        """.trimIndent()

        return executeGraphQL(shop, accessToken, query, mapOf("id" to graphQLId))
    }

    // =====================================================
    // FULFILLMENT OPERATIONS (GraphQL)
    // =====================================================

    /**
     * Create a fulfillment for an order
     *
     * @param shop Shop domain
     * @param accessToken Store access token
     * @param fulfillmentOrderId The fulfillment order ID (from order.fulfillmentOrders)
     * @param trackingNumber Tracking number
     * @param trackingCompany Carrier name (e.g., "USPS", "UPS", "FEDEX")
     * @return Result with fulfillment ID
     */
    suspend fun createFulfillment(
        shop: String,
        accessToken: String,
        fulfillmentOrderId: String,
        trackingNumber: String,
        trackingCompany: String
    ): Result<ShopifyGraphQLResponse<ShopifyFulfillmentResponse>> {
        val mutation = """
            mutation fulfillmentCreate {
              fulfillmentCreateV2(
                fulfillment: {
                  lineItemsByFulfillmentOrder: [
                    {
                      fulfillmentOrderId: "$fulfillmentOrderId"
                    }
                  ]
                  trackingInfo: {
                    number: "$trackingNumber"
                    company: "$trackingCompany"
                  }
                }
              ) {
                fulfillment {
                  id
                }
                userErrors {
                  field
                  message
                }
              }
            }
        """.trimIndent()

        return executeGraphQL(shop, accessToken, mutation, emptyMap())
    }

    /**
     * Update tracking information for an existing fulfillment
     */
    suspend fun updateFulfillmentTracking(
        shop: String,
        accessToken: String,
        fulfillmentId: String,
        trackingNumber: String,
        trackingCompany: String,
        trackingUrl: String? = null
    ): Result<ShopifyGraphQLResponse<ShopifyFulfillmentResponse>> {
        val urlPart = trackingUrl?.let { "url: \"$it\"" } ?: ""

        val mutation = """
            mutation updateTracking {
              fulfillmentTrackingInfoUpdate(
                fulfillmentId: "$fulfillmentId"
                trackingInfoInput: {
                  number: "$trackingNumber"
                  company: "$trackingCompany"
                  $urlPart
                }
              ) {
                fulfillment {
                  id
                  trackingInfo {
                    number
                    company
                    url
                  }
                }
                userErrors {
                  field
                  message
                }
              }
            }
        """.trimIndent()

        return executeGraphQL(shop, accessToken, mutation, emptyMap())
    }

    // =====================================================
    // PRODUCT OPERATIONS (REST API)
    // =====================================================

    /**
     * Get all products from the store
     */
    suspend fun getProducts(
        shop: String,
        accessToken: String,
        limit: Int = 250,
        sinceId: Long? = null
    ): Result<ShopifyProductsResponse> {
        return try {
            val url = buildString {
                append(getRestBaseUrl(shop))
                append("/products.json?limit=$limit")
                sinceId?.let { append("&since_id=$it") }
            }

            val response = httpClient.get(url) {
                header("X-Shopify-Access-Token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val products: ShopifyProductsResponse = response.body()
                logger.info("Fetched ${products.products.size} products from $shop")
                Result.success(products)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to fetch products: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to fetch products", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception fetching products", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get a single product by ID
     */
    suspend fun getProduct(
        shop: String,
        accessToken: String,
        productId: Long
    ): Result<ShopifyProductResponse> {
        return try {
            val url = "${getRestBaseUrl(shop)}/products/$productId.json"

            val response = httpClient.get(url) {
                header("X-Shopify-Access-Token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val product: ShopifyProductResponse = response.body()
                Result.success(product)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to fetch product: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to fetch product", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception fetching product", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // WEBHOOK OPERATIONS (REST API)
    // =====================================================

    /**
     * Register a webhook for a specific topic
     *
     * @param shop Shop domain
     * @param accessToken Store access token
     * @param topic Webhook topic (e.g., "orders/create", "orders/fulfilled")
     * @param address Webhook callback URL
     * @return Result with webhook info
     */
    suspend fun registerWebhook(
        shop: String,
        accessToken: String,
        topic: String,
        address: String
    ): Result<ShopifyWebhookResponse> {
        return try {
            val url = "${getRestBaseUrl(shop)}/webhooks.json"

            val response = httpClient.post(url) {
                header("X-Shopify-Access-Token", accessToken)
                contentType(ContentType.Application.Json)
                setBody(ShopifyWebhookRegistration(
                    webhook = ShopifyWebhookConfig(
                        topic = topic,
                        address = address,
                        format = "json"
                    )
                ))
            }

            if (response.status.isSuccess()) {
                val webhookResponse: ShopifyWebhookResponse = response.body()
                logger.info("Registered webhook for topic: $topic on $shop")
                Result.success(webhookResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to register webhook: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to register webhook", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception registering webhook", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get all registered webhooks
     */
    suspend fun getWebhooks(
        shop: String,
        accessToken: String
    ): Result<ShopifyWebhooksResponse> {
        return try {
            val url = "${getRestBaseUrl(shop)}/webhooks.json"

            val response = httpClient.get(url) {
                header("X-Shopify-Access-Token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val webhooks: ShopifyWebhooksResponse = response.body()
                Result.success(webhooks)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to fetch webhooks: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to fetch webhooks", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception fetching webhooks", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Delete a webhook by ID
     */
    suspend fun deleteWebhook(
        shop: String,
        accessToken: String,
        webhookId: Long
    ): Result<Unit> {
        return try {
            val url = "${getRestBaseUrl(shop)}/webhooks/$webhookId.json"

            val response = httpClient.delete(url) {
                header("X-Shopify-Access-Token", accessToken)
            }

            if (response.status.isSuccess()) {
                logger.info("Deleted webhook $webhookId from $shop")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to delete webhook: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to delete webhook", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception deleting webhook", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // SHOP INFO (REST API)
    // =====================================================

    /**
     * Get shop information
     */
    suspend fun getShopInfo(
        shop: String,
        accessToken: String
    ): Result<Map<String, Any>> {
        return try {
            val url = "${getRestBaseUrl(shop)}/shop.json"

            val response = httpClient.get(url) {
                header("X-Shopify-Access-Token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val shopInfo: Map<String, Any> = response.body()
                Result.success(shopInfo)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to fetch shop info: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("Failed to fetch shop info", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception fetching shop info", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // GRAPHQL HELPER
    // =====================================================

    /**
     * Execute a GraphQL query/mutation
     */
    private suspend inline fun <reified T> executeGraphQL(
        shop: String,
        accessToken: String,
        query: String,
        variables: Map<String, Any>
    ): Result<ShopifyGraphQLResponse<T>> {
        return try {
            val url = getGraphQLUrl(shop)

            val response = httpClient.post(url) {
                header("X-Shopify-Access-Token", accessToken)
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "query" to query,
                    "variables" to variables
                ))
            }

            if (response.status.isSuccess()) {
                val graphQLResponse: ShopifyGraphQLResponse<T> = response.body()

                // Check for GraphQL errors
                if (!graphQLResponse.errors.isNullOrEmpty()) {
                    val errorMessages = graphQLResponse.errors.joinToString(", ") { it.message }
                    logger.error("GraphQL errors: $errorMessages")
                }

                Result.success(graphQLResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("GraphQL request failed: ${response.status} - $errorBody")
                Result.failure(ShopifyClientException("GraphQL request failed", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception executing GraphQL", e)
            Result.failure(ShopifyClientException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Validate access token by making a simple API call
     */
    suspend fun validateAccessToken(shop: String, accessToken: String): Boolean {
        return try {
            val result = getShopInfo(shop, accessToken)
            result.isSuccess
        } catch (e: Exception) {
            logger.error("Token validation failed", e)
            false
        }
    }
}

class ShopifyClientException(
    message: String,
    val statusCode: Int
) : Exception(message)
