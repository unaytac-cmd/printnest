package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.CategoryRepository

class CategoryService(
    private val categoryRepository: CategoryRepository
) {

    // =====================================================
    // CATEGORIES
    // =====================================================

    fun getCategories(tenantId: Long, includeInactive: Boolean = false): List<ProductCategory> {
        return categoryRepository.findAll(tenantId, includeInactive)
    }

    fun getCategoriesHierarchical(tenantId: Long): List<ProductCategory> {
        return categoryRepository.findHierarchical(tenantId)
    }

    fun getCategory(id: Long, tenantId: Long): ProductCategory? {
        return categoryRepository.findById(id, tenantId)
    }

    fun getCategoryWithModifications(id: Long, tenantId: Long): ProductCategory? {
        return categoryRepository.findByIdWithModifications(id, tenantId)
    }

    fun createCategory(tenantId: Long, request: CreateCategoryRequest): Result<ProductCategory> {
        // Validate parent category if provided
        request.parentCategoryId?.let { parentId ->
            val parent = categoryRepository.findById(parentId, tenantId)
            if (parent == null) {
                return Result.failure(IllegalArgumentException("Parent category not found"))
            }
        }

        val category = categoryRepository.create(tenantId, request)
        return Result.success(category)
    }

    fun updateCategory(id: Long, tenantId: Long, request: UpdateCategoryRequest): Result<ProductCategory> {
        // Check category exists
        val existing = categoryRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Category not found"))

        // Validate parent category if changing
        request.parentCategoryId?.let { parentId ->
            if (parentId == id) {
                return Result.failure(IllegalArgumentException("Category cannot be its own parent"))
            }
            val parent = categoryRepository.findById(parentId, tenantId)
            if (parent == null) {
                return Result.failure(IllegalArgumentException("Parent category not found"))
            }
        }

        val updated = categoryRepository.update(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update category"))

        return Result.success(updated)
    }

    fun deleteCategory(id: Long, tenantId: Long): Result<Boolean> {
        val existing = categoryRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Category not found"))

        // Check for child categories
        val children = categoryRepository.findAll(tenantId).filter { it.parentCategoryId == id }
        if (children.isNotEmpty()) {
            return Result.failure(IllegalStateException("Cannot delete category with child categories"))
        }

        val deleted = categoryRepository.delete(id, tenantId)
        return Result.success(deleted)
    }

    // =====================================================
    // MODIFICATIONS (Print Locations)
    // =====================================================

    fun getModifications(categoryId: Long, tenantId: Long): List<Modification> {
        return categoryRepository.findModifications(categoryId, tenantId)
    }

    fun getModification(id: Long, tenantId: Long): Modification? {
        return categoryRepository.findModificationById(id, tenantId)
    }

    fun createModification(categoryId: Long, tenantId: Long, request: CreateModificationRequest): Result<Modification> {
        // Validate category exists
        val category = categoryRepository.findById(categoryId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Category not found"))

        val modification = categoryRepository.createModification(categoryId, tenantId, request)
        return Result.success(modification)
    }

    fun updateModification(id: Long, tenantId: Long, request: UpdateModificationRequest): Result<Modification> {
        val existing = categoryRepository.findModificationById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Modification not found"))

        val updated = categoryRepository.updateModification(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update modification"))

        return Result.success(updated)
    }

    fun deleteModification(id: Long, tenantId: Long): Result<Boolean> {
        val existing = categoryRepository.findModificationById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Modification not found"))

        val deleted = categoryRepository.deleteModification(id, tenantId)
        return Result.success(deleted)
    }

    // =====================================================
    // UTILITIES
    // =====================================================

    fun getCategoryPath(id: Long, tenantId: Long): List<ProductCategory> {
        val path = mutableListOf<ProductCategory>()
        var currentId: Long? = id

        while (currentId != null) {
            val category = categoryRepository.findById(currentId, tenantId)
            if (category != null) {
                path.add(0, category)
                currentId = category.parentCategoryId
            } else {
                break
            }
        }

        return path
    }

    fun getModificationsForCategory(categoryId: Long, tenantId: Long, includeParent: Boolean = true): List<Modification> {
        val modifications = mutableListOf<Modification>()

        if (includeParent) {
            // Get modifications from parent categories as well
            val path = getCategoryPath(categoryId, tenantId)
            path.forEach { category ->
                modifications.addAll(categoryRepository.findModifications(category.id, tenantId))
            }
        } else {
            modifications.addAll(categoryRepository.findModifications(categoryId, tenantId))
        }

        return modifications.distinctBy { it.id }
    }
}
