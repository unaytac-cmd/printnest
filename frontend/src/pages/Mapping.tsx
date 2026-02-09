import { useState } from 'react';
import {
  Plus,
  Trash2,
  Loader2,
  Link2,
  Image,
  X,
  Save,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useMapValues,
  useMapListings,
  useCreateMapValue,
  useDeleteMapValue,
  useCreateMapListing,
  useDeleteMapListing,
} from '@/api/hooks';
import { useProductSelectionData } from '@/api/hooks';

type ActiveTab = 'values' | 'listings';

export default function Mapping() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('values');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">Mapping</h1>
        <p className="text-muted-foreground">
          Map external product variations to your internal variants and designs
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-border">
        <button
          onClick={() => setActiveTab('values')}
          className={cn(
            'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'values'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          )}
        >
          <Link2 className="w-4 h-4 inline mr-2" />
          Variant Mapping
        </button>
        <button
          onClick={() => setActiveTab('listings')}
          className={cn(
            'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'listings'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          )}
        >
          <Image className="w-4 h-4 inline mr-2" />
          Design Mapping
        </button>
      </div>

      {/* Content */}
      {activeTab === 'values' ? <MapValuesTab /> : <MapListingsTab />}
    </div>
  );
}

// Map Values Tab - Variant Mapping
function MapValuesTab() {
  const { data, isLoading, error } = useMapValues();
  const { data: productData } = useProductSelectionData();
  const createMapValue = useCreateMapValue();
  const deleteMapValue = useDeleteMapValue();

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    valueId1: '',
    valueId2: '',
    variantId: 0,
    isDark: false,
  });

  const mapValues = data?.mapValues || [];
  const variants = productData?.variants || [];

  const handleSubmit = async () => {
    if (!formData.valueId1 || !formData.variantId) {
      alert('Please fill in required fields');
      return;
    }

    try {
      await createMapValue.mutateAsync({
        valueId1: formData.valueId1,
        valueId2: formData.valueId2 || undefined,
        variantId: formData.variantId,
        isDark: formData.isDark,
      });
      setShowForm(false);
      setFormData({ valueId1: '', valueId2: '', variantId: 0, isDark: false });
    } catch {
      alert('Failed to create mapping');
    }
  };

  const handleDelete = (id: number) => {
    if (confirm('Delete this mapping?')) {
      deleteMapValue.mutate(id);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-32">
        <Loader2 className="w-6 h-6 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return <div className="text-destructive">Failed to load mappings</div>;
  }

  return (
    <div className="space-y-4">
      {/* Add Button */}
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Variant Mapping
        </button>
      </div>

      {/* Add Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">Add Variant Mapping</h3>
              <button onClick={() => setShowForm(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">
                  External Value ID 1 *
                </label>
                <input
                  type="text"
                  value={formData.valueId1}
                  onChange={(e) => setFormData({ ...formData, valueId1: e.target.value })}
                  placeholder="e.g., etsy_variation_123"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">
                  External Value ID 2
                </label>
                <input
                  type="text"
                  value={formData.valueId2}
                  onChange={(e) => setFormData({ ...formData, valueId2: e.target.value })}
                  placeholder="e.g., etsy_variation_456"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Target Variant *</label>
                <select
                  value={formData.variantId}
                  onChange={(e) => setFormData({ ...formData, variantId: Number(e.target.value) })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                >
                  <option value={0}>Select variant</option>
                  {variants.map((v) => (
                    <option key={v.id} value={v.id}>
                      Variant #{v.id} - ${v.price}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isDark"
                  checked={formData.isDark}
                  onChange={(e) => setFormData({ ...formData, isDark: e.target.checked })}
                  className="rounded border-border"
                />
                <label htmlFor="isDark" className="text-sm">Dark color variant</label>
              </div>

              <button
                onClick={handleSubmit}
                disabled={createMapValue.isPending}
                className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-lg disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {createMapValue.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                Save Mapping
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mappings Table */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        {mapValues.length === 0 ? (
          <div className="text-center py-12">
            <Link2 className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No variant mappings yet</p>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left p-4 font-medium text-sm">External Value 1</th>
                <th className="text-left p-4 font-medium text-sm">External Value 2</th>
                <th className="text-left p-4 font-medium text-sm">Target Variant</th>
                <th className="text-left p-4 font-medium text-sm">Dark</th>
                <th className="text-left p-4 font-medium text-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              {mapValues.map((mapping) => (
                <tr key={mapping.id} className="border-b border-border">
                  <td className="p-4 font-mono text-sm">{mapping.valueId1}</td>
                  <td className="p-4 font-mono text-sm">{mapping.valueId2 || '-'}</td>
                  <td className="p-4">
                    {mapping.variantName || `Variant #${mapping.variantId}`}
                    {mapping.productName && (
                      <span className="text-muted-foreground text-sm ml-2">
                        ({mapping.productName})
                      </span>
                    )}
                  </td>
                  <td className="p-4">
                    {mapping.isDark ? (
                      <span className="px-2 py-0.5 bg-gray-800 text-white text-xs rounded">
                        Dark
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 bg-gray-100 text-gray-800 text-xs rounded">
                        Light
                      </span>
                    )}
                  </td>
                  <td className="p-4">
                    <button
                      onClick={() => handleDelete(mapping.id)}
                      className="p-2 hover:bg-muted rounded-lg text-destructive"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

// Map Listings Tab - Design Mapping
function MapListingsTab() {
  const { data, isLoading, error } = useMapListings();
  const { data: productData } = useProductSelectionData();
  const createMapListing = useCreateMapListing();
  const deleteMapListing = useDeleteMapListing();

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    listingId: '',
    modificationId: 0,
    lightDesignId: 0,
    darkDesignId: 0,
  });

  const mapListings = data?.mapListings || [];
  const modifications = productData?.modifications || [];

  const handleSubmit = async () => {
    if (!formData.listingId || !formData.modificationId) {
      alert('Please fill in required fields');
      return;
    }

    try {
      await createMapListing.mutateAsync({
        listingId: formData.listingId,
        modificationId: formData.modificationId,
        lightDesignId: formData.lightDesignId || undefined,
        darkDesignId: formData.darkDesignId || undefined,
      });
      setShowForm(false);
      setFormData({ listingId: '', modificationId: 0, lightDesignId: 0, darkDesignId: 0 });
    } catch {
      alert('Failed to create mapping');
    }
  };

  const handleDelete = (id: number) => {
    if (confirm('Delete this mapping?')) {
      deleteMapListing.mutate(id);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-32">
        <Loader2 className="w-6 h-6 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return <div className="text-destructive">Failed to load mappings</div>;
  }

  return (
    <div className="space-y-4">
      {/* Add Button */}
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Design Mapping
        </button>
      </div>

      {/* Add Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">Add Design Mapping</h3>
              <button onClick={() => setShowForm(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">Listing ID *</label>
                <input
                  type="text"
                  value={formData.listingId}
                  onChange={(e) => setFormData({ ...formData, listingId: e.target.value })}
                  placeholder="e.g., etsy_listing_12345"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Print Location *</label>
                <select
                  value={formData.modificationId}
                  onChange={(e) => setFormData({ ...formData, modificationId: Number(e.target.value) })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                >
                  <option value={0}>Select location</option>
                  {modifications.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Light Design ID</label>
                <input
                  type="number"
                  value={formData.lightDesignId || ''}
                  onChange={(e) => setFormData({ ...formData, lightDesignId: Number(e.target.value) })}
                  placeholder="Design ID for light colors"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div>
                <label className="text-sm text-muted-foreground">Dark Design ID</label>
                <input
                  type="number"
                  value={formData.darkDesignId || ''}
                  onChange={(e) => setFormData({ ...formData, darkDesignId: Number(e.target.value) })}
                  placeholder="Design ID for dark colors"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <button
                onClick={handleSubmit}
                disabled={createMapListing.isPending}
                className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-lg disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {createMapListing.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                Save Mapping
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mappings Table */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        {mapListings.length === 0 ? (
          <div className="text-center py-12">
            <Image className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No design mappings yet</p>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left p-4 font-medium text-sm">Listing ID</th>
                <th className="text-left p-4 font-medium text-sm">Print Location</th>
                <th className="text-left p-4 font-medium text-sm">Light Design</th>
                <th className="text-left p-4 font-medium text-sm">Dark Design</th>
                <th className="text-left p-4 font-medium text-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              {mapListings.map((mapping) => (
                <tr key={mapping.id} className="border-b border-border">
                  <td className="p-4 font-mono text-sm">{mapping.listingId}</td>
                  <td className="p-4">{mapping.modificationName || `#${mapping.modificationId}`}</td>
                  <td className="p-4">
                    {mapping.lightDesignUrl ? (
                      <img
                        src={mapping.lightDesignUrl}
                        alt="Light"
                        className="w-10 h-10 object-cover rounded"
                      />
                    ) : mapping.lightDesignId ? (
                      <span className="text-sm">#{mapping.lightDesignId}</span>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td className="p-4">
                    {mapping.darkDesignUrl ? (
                      <img
                        src={mapping.darkDesignUrl}
                        alt="Dark"
                        className="w-10 h-10 object-cover rounded"
                      />
                    ) : mapping.darkDesignId ? (
                      <span className="text-sm">#{mapping.darkDesignId}</span>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td className="p-4">
                    <button
                      onClick={() => handleDelete(mapping.id)}
                      className="p-2 hover:bg-muted rounded-lg text-destructive"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
