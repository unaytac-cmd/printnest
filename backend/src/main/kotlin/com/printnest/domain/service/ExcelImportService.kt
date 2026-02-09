package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.CategoryRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.ProductRepository
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigDecimal

/**
 * Service for importing orders from Excel files.
 *
 * Excel format:
 * name | street1 | city | state | zip | country | product | category | option_1 | option_2 | quantity | modification_1 | modification_1_url
 */
class ExcelImportService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {
    private val logger = LoggerFactory.getLogger(ExcelImportService::class.java)

    companion object {
        private const val COL_NAME = 0
        private const val COL_STREET1 = 1
        private const val COL_CITY = 2
        private const val COL_STATE = 3
        private const val COL_ZIP = 4
        private const val COL_COUNTRY = 5
        private const val COL_PRODUCT = 6
        private const val COL_CATEGORY = 7
        private const val COL_OPTION1 = 8
        private const val COL_OPTION2 = 9
        private const val COL_QUANTITY = 10
        private const val COL_MODIFICATION1 = 11
        private const val COL_MODIFICATION1_URL = 12
    }

    /**
     * Import orders from an Excel file
     */
    fun importOrdersFromExcel(
        tenantId: Long,
        userId: Long,
        storeId: Long?,
        inputStream: InputStream
    ): ExcelImportResult {
        logger.info("Starting Excel import for tenant $tenantId, user $userId")

        val errors = mutableListOf<ExcelImportError>()
        var ordersCreated = 0

        try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Group rows by address
            val orderGroups = mutableMapOf<String, MutableList<ExcelOrderRow>>()

            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue

                try {
                    val orderRow = parseRow(row, rowIndex + 1, tenantId)
                    if (orderRow != null) {
                        val addressKey = generateAddressKey(orderRow.address)
                        orderGroups.getOrPut(addressKey) { mutableListOf() }.add(orderRow)
                    }
                } catch (e: Exception) {
                    val customerName = getCellValue(row.getCell(COL_NAME))
                    errors.add(ExcelImportError(
                        rowNumber = rowIndex + 1,
                        customerName = customerName,
                        errorMessage = e.message ?: "Failed to parse row"
                    ))
                }
            }

            // Create orders for each group
            for ((_, orderRows) in orderGroups) {
                try {
                    createOrderFromRows(tenantId, userId, storeId, orderRows)
                    ordersCreated++
                } catch (e: Exception) {
                    val firstRow = orderRows.firstOrNull()
                    errors.add(ExcelImportError(
                        rowNumber = firstRow?.rowNumber ?: 0,
                        customerName = firstRow?.address?.name,
                        errorMessage = e.message ?: "Failed to create order"
                    ))
                }
            }

            workbook.close()

            logger.info("Excel import completed: $ordersCreated orders created, ${errors.size} errors")

            return ExcelImportResult(
                success = ordersCreated > 0,
                ordersCreated = ordersCreated,
                ordersWithErrors = errors.size,
                errors = errors,
                message = if (errors.isEmpty()) "All orders imported successfully" else "Some orders had errors"
            )
        } catch (e: Exception) {
            logger.error("Failed to parse Excel file", e)
            return ExcelImportResult(
                success = false,
                ordersCreated = 0,
                ordersWithErrors = 0,
                errors = listOf(ExcelImportError(
                    rowNumber = 0,
                    errorMessage = "Failed to parse Excel file: ${e.message}"
                )),
                message = "Failed to parse Excel file"
            )
        }
    }

    /**
     * Parse a single row from the Excel file
     */
    private fun parseRow(row: Row, rowNumber: Int, tenantId: Long): ExcelOrderRow? {
        val name = getCellValue(row.getCell(COL_NAME))
        if (name.isBlank()) return null

        val street1 = getCellValue(row.getCell(COL_STREET1))
        val city = getCellValue(row.getCell(COL_CITY))
        val state = getCellValue(row.getCell(COL_STATE))
        val zip = getCellValue(row.getCell(COL_ZIP))
        val country = getCellValue(row.getCell(COL_COUNTRY)).ifBlank { "US" }
        val productName = getCellValue(row.getCell(COL_PRODUCT))
        val categoryName = getCellValue(row.getCell(COL_CATEGORY))
        val option1Name = getCellValue(row.getCell(COL_OPTION1))
        val option2Name = getCellValue(row.getCell(COL_OPTION2))
        val quantityStr = getCellValue(row.getCell(COL_QUANTITY))
        val modificationName = getCellValue(row.getCell(COL_MODIFICATION1))
        val modificationUrl = getCellValue(row.getCell(COL_MODIFICATION1_URL))

        val quantity = quantityStr.toIntOrNull() ?: 1

        // Find product and variant
        val (productId, variantId, categoryId) = findProductAndVariant(
            tenantId, productName, categoryName, option1Name, option2Name
        )

        return ExcelOrderRow(
            rowNumber = rowNumber,
            address = Address(
                name = name,
                street1 = street1,
                city = city,
                state = state,
                postalCode = zip,
                country = country
            ),
            productId = productId,
            variantId = variantId,
            categoryId = categoryId,
            productName = productName,
            option1Name = option1Name,
            option2Name = option2Name,
            quantity = quantity,
            modificationName = modificationName,
            modificationDesignUrl = modificationUrl
        )
    }

    /**
     * Find product and variant by names
     */
    private fun findProductAndVariant(
        tenantId: Long,
        productName: String,
        categoryName: String,
        option1Name: String,
        option2Name: String
    ): Triple<Long?, Long?, Long?> {
        // Find category first
        val categories = categoryRepository.findAll(tenantId)
        val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }

        // Find product
        val products = productRepository.findAll(tenantId, category?.id, false)
        val product = products.find { it.title.equals(productName, ignoreCase = true) }
            ?: return Triple(null, null, category?.id)

        // Find variant
        val variants = productRepository.findVariants(product.id, tenantId)
        val option1s = productRepository.findOption1s(product.id, tenantId)
        val option2s = productRepository.findOption2s(product.id, tenantId)

        val option1 = option1s.find { it.name.equals(option1Name, ignoreCase = true) }
        val option2 = option2s.find { it.name.equals(option2Name, ignoreCase = true) }

        val variant = variants.find { v ->
            (option1 == null || v.option1Id == option1.id) &&
            (option2 == null || v.option2Id == option2.id)
        }

        return Triple(product.id, variant?.id, category?.id)
    }

    /**
     * Create an order from grouped rows
     */
    private fun createOrderFromRows(
        tenantId: Long,
        userId: Long,
        storeId: Long?,
        rows: List<ExcelOrderRow>
    ) {
        val firstRow = rows.first()

        // Find modification for products
        val products = rows.map { row ->
            val modificationDetail = if (row.modificationName.isNotBlank() && row.categoryId != null) {
                val modifications = categoryRepository.findModifications(row.categoryId, tenantId)
                val modification = modifications.find { it.name.equals(row.modificationName, ignoreCase = true) }

                if (modification != null) {
                    listOf(ModificationDetailItem(
                        modificationId = modification.id,
                        modificationName = modification.name,
                        modificationDesign = row.modificationDesignUrl,
                        priceDifference = modification.priceDifference,
                        modificationUseWidth = modification.useWidth
                    ))
                } else emptyList()
            } else emptyList()

            CreateOrderProductRequest(
                productId = row.productId,
                variantId = row.variantId,
                quantity = row.quantity,
                modificationDetail = modificationDetail
            )
        }

        val request = CreateOrderRequest(
            storeId = storeId,
            customerName = firstRow.address.name,
            shippingAddress = firstRow.address,
            products = products
        )

        orderRepository.create(tenantId, userId, request)
    }

    /**
     * Generate a unique key for an address
     */
    private fun generateAddressKey(address: Address): String {
        return "${address.name}|${address.street1}|${address.city}|${address.state}|${address.postalCode}|${address.country}"
            .lowercase()
            .replace("\\s+".toRegex(), "")
    }

    /**
     * Get cell value as string
     */
    private fun getCellValue(cell: Cell?): String {
        if (cell == null) return ""

        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toString()
                } else {
                    // Handle numbers - avoid scientific notation
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue.trim()
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> ""
        }
    }

    /**
     * Internal data class for Excel row data
     */
    private data class ExcelOrderRow(
        val rowNumber: Int,
        val address: Address,
        val productId: Long?,
        val variantId: Long?,
        val categoryId: Long?,
        val productName: String,
        val option1Name: String,
        val option2Name: String,
        val quantity: Int,
        val modificationName: String,
        val modificationDesignUrl: String
    )
}
