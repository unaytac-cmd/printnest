import { useState } from 'react';
import {
  Plus,
  Trash2,
  Loader2,
  FolderTree,
  Edit2,
  X,
  Save,
  ChevronRight,
  ChevronDown,
  Layers,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useCategories,
  useCategoryModifications,
  useCreateCategory,
  useUpdateCategory,
  useDeleteCategory,
  useCreateModification,
  useDeleteModification,
} from '@/api/hooks';
import type { Category } from '@/api/hooks';

export default function Categories() {
  const { data, isLoading, error } = useCategories();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();

  const [showForm, setShowForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [expandedCategories, setExpandedCategories] = useState<Set<number>>(new Set());
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    parentCategoryId: 0,
    isHeavy: false,
  });

  const categories = data?.categories || [];

  // Build hierarchy
  const rootCategories = categories.filter((c) => !c.parentCategoryId);
  const getChildren = (parentId: number) => categories.filter((c) => c.parentCategoryId === parentId);

  const toggleExpand = (id: number) => {
    setExpandedCategories((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  const openCreateForm = (parentId?: number) => {
    setEditingCategory(null);
    setFormData({
      name: '',
      description: '',
      parentCategoryId: parentId || 0,
      isHeavy: false,
    });
    setShowForm(true);
  };

  const openEditForm = (category: Category) => {
    setEditingCategory(category);
    setFormData({
      name: category.name,
      description: category.description || '',
      parentCategoryId: category.parentCategoryId || 0,
      isHeavy: category.isHeavy,
    });
    setShowForm(true);
  };

  const handleSubmit = async () => {
    if (!formData.name.trim()) {
      alert('Category name is required');
      return;
    }

    try {
      if (editingCategory) {
        await updateCategory.mutateAsync({
          id: editingCategory.id,
          name: formData.name,
          description: formData.description || undefined,
          parentCategoryId: formData.parentCategoryId || undefined,
          isHeavy: formData.isHeavy,
        });
      } else {
        await createCategory.mutateAsync({
          name: formData.name,
          description: formData.description || undefined,
          parentCategoryId: formData.parentCategoryId || undefined,
          isHeavy: formData.isHeavy,
        });
      }
      setShowForm(false);
      setEditingCategory(null);
    } catch {
      alert('Failed to save category');
    }
  };

  const handleDelete = (id: number) => {
    if (confirm('Delete this category? Products in this category will be unassigned.')) {
      deleteCategory.mutate(id);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return <div className="text-destructive text-center py-12">Failed to load categories</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Categories</h1>
          <p className="text-muted-foreground">
            Organize products and manage print locations (modifications)
          </p>
        </div>
        <button
          onClick={() => openCreateForm()}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Category
        </button>
      </div>

      {/* Category Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">
                {editingCategory ? 'Edit Category' : 'Add Category'}
              </h3>
              <button onClick={() => setShowForm(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">Name *</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g., T-Shirts"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  rows={3}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg resize-none"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Parent Category</label>
                <select
                  value={formData.parentCategoryId}
                  onChange={(e) => setFormData({ ...formData, parentCategoryId: Number(e.target.value) })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                >
                  <option value={0}>None (Root Category)</option>
                  {categories
                    .filter((c) => c.id !== editingCategory?.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isHeavy"
                  checked={formData.isHeavy}
                  onChange={(e) => setFormData({ ...formData, isHeavy: e.target.checked })}
                  className="rounded border-border"
                />
                <label htmlFor="isHeavy" className="text-sm">
                  Heavy item (affects shipping calculation)
                </label>
              </div>

              <button
                onClick={handleSubmit}
                disabled={createCategory.isPending || updateCategory.isPending}
                className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-lg disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {createCategory.isPending || updateCategory.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                {editingCategory ? 'Update Category' : 'Create Category'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Tree */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
            <FolderTree className="w-5 h-5" />
            Category Tree
          </h2>

          {categories.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              No categories yet. Create your first category.
            </div>
          ) : (
            <div className="space-y-1">
              {rootCategories.map((category) => (
                <CategoryTreeItem
                  key={category.id}
                  category={category}
                  level={0}
                  getChildren={getChildren}
                  expandedCategories={expandedCategories}
                  toggleExpand={toggleExpand}
                  selectedCategoryId={selectedCategoryId}
                  setSelectedCategoryId={setSelectedCategoryId}
                  onEdit={openEditForm}
                  onDelete={handleDelete}
                  onAddChild={openCreateForm}
                />
              ))}
            </div>
          )}
        </div>

        {/* Modifications Panel */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
            <Layers className="w-5 h-5" />
            Print Locations (Modifications)
          </h2>

          {selectedCategoryId ? (
            <ModificationsPanel categoryId={selectedCategoryId} />
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              Select a category to manage its print locations
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// Category Tree Item Component
function CategoryTreeItem({
  category,
  level,
  getChildren,
  expandedCategories,
  toggleExpand,
  selectedCategoryId,
  setSelectedCategoryId,
  onEdit,
  onDelete,
  onAddChild,
}: {
  category: Category;
  level: number;
  getChildren: (parentId: number) => Category[];
  expandedCategories: Set<number>;
  toggleExpand: (id: number) => void;
  selectedCategoryId: number | null;
  setSelectedCategoryId: (id: number) => void;
  onEdit: (category: Category) => void;
  onDelete: (id: number) => void;
  onAddChild: (parentId: number) => void;
}) {
  const children = getChildren(category.id);
  const hasChildren = children.length > 0;
  const isExpanded = expandedCategories.has(category.id);
  const isSelected = selectedCategoryId === category.id;

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer transition-colors',
          isSelected ? 'bg-primary/10 border border-primary' : 'hover:bg-muted'
        )}
        style={{ marginLeft: level * 20 }}
        onClick={() => setSelectedCategoryId(category.id)}
      >
        {hasChildren ? (
          <button
            onClick={(e) => {
              e.stopPropagation();
              toggleExpand(category.id);
            }}
            className="p-0.5"
          >
            {isExpanded ? (
              <ChevronDown className="w-4 h-4" />
            ) : (
              <ChevronRight className="w-4 h-4" />
            )}
          </button>
        ) : (
          <div className="w-5" />
        )}

        <span className="flex-1 font-medium">{category.name}</span>

        {category.isHeavy && (
          <span className="text-xs px-1.5 py-0.5 bg-yellow-100 text-yellow-800 rounded">
            Heavy
          </span>
        )}

        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onAddChild(category.id);
            }}
            className="p-1 hover:bg-muted rounded"
            title="Add subcategory"
          >
            <Plus className="w-3 h-3" />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onEdit(category);
            }}
            className="p-1 hover:bg-muted rounded"
            title="Edit"
          >
            <Edit2 className="w-3 h-3" />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onDelete(category.id);
            }}
            className="p-1 hover:bg-muted rounded text-destructive"
            title="Delete"
          >
            <Trash2 className="w-3 h-3" />
          </button>
        </div>
      </div>

      {hasChildren && isExpanded && (
        <div>
          {children.map((child) => (
            <CategoryTreeItem
              key={child.id}
              category={child}
              level={level + 1}
              getChildren={getChildren}
              expandedCategories={expandedCategories}
              toggleExpand={toggleExpand}
              selectedCategoryId={selectedCategoryId}
              setSelectedCategoryId={setSelectedCategoryId}
              onEdit={onEdit}
              onDelete={onDelete}
              onAddChild={onAddChild}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// Modifications Panel Component
function ModificationsPanel({ categoryId }: { categoryId: number }) {
  const { data, isLoading } = useCategoryModifications(categoryId);
  const createModification = useCreateModification(categoryId);
  const deleteModification = useDeleteModification(categoryId);

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    priceDifference: 0,
    useWidth: 1,
  });

  const modifications = data?.modifications || [];

  const handleSubmit = async () => {
    if (!formData.name.trim()) {
      alert('Name is required');
      return;
    }

    try {
      await createModification.mutateAsync({
        name: formData.name,
        description: formData.description || undefined,
        priceDifference: formData.priceDifference,
        useWidth: formData.useWidth,
      });
      setShowForm(false);
      setFormData({ name: '', description: '', priceDifference: 0, useWidth: 1 });
    } catch {
      alert('Failed to create modification');
    }
  };

  const handleDelete = (id: number) => {
    if (confirm('Delete this print location?')) {
      deleteModification.mutate(id);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-32">
        <Loader2 className="w-6 h-6 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-lg hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" />
          Add Print Location
        </button>
      </div>

      {/* Add Form */}
      {showForm && (
        <div className="p-4 border border-border rounded-lg space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-muted-foreground">Name *</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., Front, Back, Sleeve"
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Price Difference ($)</label>
              <input
                type="number"
                step="0.01"
                value={formData.priceDifference}
                onChange={(e) => setFormData({ ...formData, priceDifference: Number(e.target.value) })}
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg text-sm"
              />
            </div>
          </div>

          <div>
            <label className="text-sm text-muted-foreground">Use Width</label>
            <select
              value={formData.useWidth}
              onChange={(e) => setFormData({ ...formData, useWidth: Number(e.target.value) })}
              className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg text-sm"
            >
              <option value={1}>Width 1 (Primary)</option>
              <option value={2}>Width 2 (Secondary)</option>
            </select>
          </div>

          <div className="flex justify-end gap-2">
            <button
              onClick={() => setShowForm(false)}
              className="px-3 py-1.5 text-sm border border-border rounded-lg hover:bg-muted"
            >
              Cancel
            </button>
            <button
              onClick={handleSubmit}
              disabled={createModification.isPending}
              className="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-lg disabled:opacity-50"
            >
              {createModification.isPending ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      )}

      {/* Modifications List */}
      {modifications.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground text-sm">
          No print locations defined for this category
        </div>
      ) : (
        <div className="space-y-2">
          {modifications.map((mod) => (
            <div
              key={mod.id}
              className="flex items-center justify-between p-3 bg-muted/50 rounded-lg"
            >
              <div>
                <p className="font-medium">{mod.name}</p>
                <p className="text-sm text-muted-foreground">
                  {mod.priceDifference > 0 ? `+$${mod.priceDifference}` : 'No extra cost'}
                  {' · '}
                  Width {mod.useWidth}
                </p>
              </div>
              <button
                onClick={() => handleDelete(mod.id)}
                className="p-2 hover:bg-muted rounded-lg text-destructive"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
