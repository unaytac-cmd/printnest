import { useState } from 'react';
import { Routes, Route, Link, useNavigate, useParams } from 'react-router-dom';
import {
  Plus,
  Download,
  Trash2,
  Loader2,
  CheckCircle,
  XCircle,
  Clock,
  FileImage,
  ArrowLeft,
  RefreshCw,
  Settings,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useGangsheets,
  useGangsheet,
  useCreateGangsheet,
  useDeleteGangsheet,
  useGangsheetSettings,
  useUpdateGangsheetSettings,
  useOrdersForGangsheet,
} from '@/api/hooks';

// Gangsheet List Page
function GangsheetList() {
  const [page] = useState(1);
  const { data, isLoading, error, refetch } = useGangsheets({ page, pageSize: 10 });
  const deleteGangsheet = useDeleteGangsheet();

  const gangsheets = data?.gangsheets || [];

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'Completed':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'Failed':
        return <XCircle className="w-5 h-5 text-red-500" />;
      default:
        return <Clock className="w-5 h-5 text-yellow-500 animate-pulse" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Completed':
        return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
      case 'Failed':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
      default:
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
    }
  };

  const handleDelete = (id: number) => {
    if (confirm('Are you sure you want to delete this gangsheet?')) {
      deleteGangsheet.mutate(id);
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
    return (
      <div className="text-center py-12">
        <p className="text-destructive">Failed to load gangsheets</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Gangsheets</h1>
          <p className="text-muted-foreground">Create and manage print gangsheets</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            Refresh
          </button>
          <Link
            to="/gangsheet/settings"
            className="inline-flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors"
          >
            <Settings className="w-4 h-4" />
            Settings
          </Link>
          <Link
            to="/gangsheet/create"
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create Gangsheet
          </Link>
        </div>
      </div>

      {/* Gangsheet List */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        {gangsheets.length === 0 ? (
          <div className="text-center py-12">
            <FileImage className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No gangsheets yet</p>
            <Link
              to="/gangsheet/create"
              className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
            >
              <Plus className="w-4 h-4" />
              Create First Gangsheet
            </Link>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left p-4 font-medium text-sm">Name</th>
                <th className="text-left p-4 font-medium text-sm">Status</th>
                <th className="text-left p-4 font-medium text-sm">Orders</th>
                <th className="text-left p-4 font-medium text-sm">Created</th>
                <th className="text-left p-4 font-medium text-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              {gangsheets.map((gangsheet) => (
                <tr
                  key={gangsheet.id}
                  className="border-b border-border hover:bg-muted/50 transition-colors"
                >
                  <td className="p-4">
                    <Link
                      to={`/gangsheet/${gangsheet.id}`}
                      className="font-medium hover:text-primary transition-colors"
                    >
                      {gangsheet.name}
                    </Link>
                  </td>
                  <td className="p-4">
                    <span
                      className={cn(
                        'inline-flex items-center gap-1.5 px-2 py-1 text-xs rounded-full',
                        getStatusColor(gangsheet.status)
                      )}
                    >
                      {getStatusIcon(gangsheet.status)}
                      {gangsheet.status}
                    </span>
                  </td>
                  <td className="p-4 text-muted-foreground">
                    {gangsheet.orderIds.length} orders
                  </td>
                  <td className="p-4 text-muted-foreground">
                    {new Date(gangsheet.createdAt).toLocaleDateString()}
                  </td>
                  <td className="p-4">
                    <div className="flex items-center gap-2">
                      {gangsheet.downloadUrl && (
                        <a
                          href={gangsheet.downloadUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="p-2 hover:bg-muted rounded-lg transition-colors text-primary"
                          title="Download"
                        >
                          <Download className="w-4 h-4" />
                        </a>
                      )}
                      <button
                        onClick={() => handleDelete(gangsheet.id)}
                        className="p-2 hover:bg-muted rounded-lg transition-colors text-destructive"
                        title="Delete"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
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

// Create Gangsheet Page
function GangsheetCreate() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [selectedOrders, setSelectedOrders] = useState<number[]>([]);

  const { data: ordersData, isLoading: isLoadingOrders } = useOrdersForGangsheet();
  const { data: settings } = useGangsheetSettings();
  const createGangsheet = useCreateGangsheet();

  const orders = ordersData?.orders || [];

  const toggleOrder = (orderId: number) => {
    setSelectedOrders((prev) =>
      prev.includes(orderId) ? prev.filter((id) => id !== orderId) : [...prev, orderId]
    );
  };

  const selectAll = () => {
    if (selectedOrders.length === orders.length) {
      setSelectedOrders([]);
    } else {
      setSelectedOrders(orders.map((o) => o.id));
    }
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      alert('Please enter a gangsheet name');
      return;
    }
    if (selectedOrders.length === 0) {
      alert('Please select at least one order');
      return;
    }

    try {
      await createGangsheet.mutateAsync({
        name,
        orderIds: selectedOrders,
        settings: settings || undefined,
      });
      navigate('/gangsheet');
    } catch {
      alert('Failed to create gangsheet');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/gangsheet')}
          className="p-2 hover:bg-muted rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold">Create Gangsheet</h1>
          <p className="text-muted-foreground">Select orders to include in the gangsheet</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Order Selection */}
        <div className="lg:col-span-2">
          <div className="bg-card border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold">Select Orders</h2>
              <button
                onClick={selectAll}
                className="text-sm text-primary hover:underline"
              >
                {selectedOrders.length === orders.length ? 'Deselect All' : 'Select All'}
              </button>
            </div>

            {isLoadingOrders ? (
              <div className="flex items-center justify-center h-32">
                <Loader2 className="w-6 h-6 animate-spin text-primary" />
              </div>
            ) : orders.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                No orders ready for gangsheet
              </div>
            ) : (
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {orders.map((order) => (
                  <label
                    key={order.id}
                    className={cn(
                      'flex items-center gap-4 p-3 rounded-lg cursor-pointer transition-colors',
                      selectedOrders.includes(order.id)
                        ? 'bg-primary/10 border border-primary'
                        : 'bg-muted/50 hover:bg-muted border border-transparent'
                    )}
                  >
                    <input
                      type="checkbox"
                      checked={selectedOrders.includes(order.id)}
                      onChange={() => toggleOrder(order.id)}
                      className="rounded border-border"
                    />
                    <div className="flex-1">
                      <p className="font-medium">{order.orderNumber}</p>
                      <p className="text-sm text-muted-foreground">
                        {order.customerName} - {order.productCount} products
                      </p>
                    </div>
                    <span className="text-sm text-muted-foreground">
                      {new Date(order.createdAt).toLocaleDateString()}
                    </span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Settings & Submit */}
        <div className="space-y-4">
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold mb-4">Gangsheet Details</h2>
            <div className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">Name *</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Gangsheet 2024-01-15"
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                />
              </div>

              {settings && (
                <div className="space-y-2 text-sm">
                  <p className="font-medium">Current Settings:</p>
                  <div className="grid grid-cols-2 gap-2 text-muted-foreground">
                    <span>Roll Width:</span>
                    <span>{settings.rollWidth}"</span>
                    <span>Roll Height:</span>
                    <span>{settings.rollHeight}"</span>
                    <span>DPI:</span>
                    <span>{settings.dpi}</span>
                    <span>Gap:</span>
                    <span>{settings.gap}"</span>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold mb-4">Summary</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Selected Orders:</span>
                <span className="font-medium">{selectedOrders.length}</span>
              </div>
            </div>

            <button
              onClick={handleSubmit}
              disabled={createGangsheet.isPending || !name || selectedOrders.length === 0}
              className="w-full mt-6 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {createGangsheet.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Creating...
                </>
              ) : (
                <>
                  <FileImage className="w-4 h-4" />
                  Create Gangsheet
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// Gangsheet Detail Page
function GangsheetDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: gangsheet, isLoading, error } = useGangsheet(Number(id));

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (error || !gangsheet) {
    return (
      <div className="text-center py-12">
        <p className="text-destructive">Failed to load gangsheet</p>
      </div>
    );
  }

  const isProcessing = !['Completed', 'Failed'].includes(gangsheet.status);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/gangsheet')}
          className="p-2 hover:bg-muted rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{gangsheet.name}</h1>
          <p className="text-muted-foreground">
            Created {new Date(gangsheet.createdAt).toLocaleString()}
          </p>
        </div>
        {gangsheet.downloadUrl && (
          <a
            href={gangsheet.downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
          >
            <Download className="w-4 h-4" />
            Download ZIP
          </a>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Status */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4">Status</h2>
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              {isProcessing ? (
                <Loader2 className="w-6 h-6 animate-spin text-yellow-500" />
              ) : gangsheet.status === 'Completed' ? (
                <CheckCircle className="w-6 h-6 text-green-500" />
              ) : (
                <XCircle className="w-6 h-6 text-red-500" />
              )}
              <span className="text-lg font-medium">{gangsheet.status}</span>
            </div>

            {isProcessing && (
              <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
                <p className="text-sm text-yellow-800 dark:text-yellow-200">
                  Gangsheet is being processed. This page will automatically update when complete.
                </p>
              </div>
            )}

            {gangsheet.errorMessage && (
              <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                <p className="text-sm text-red-800 dark:text-red-200">
                  Error: {gangsheet.errorMessage}
                </p>
              </div>
            )}

            {gangsheet.completedAt && (
              <p className="text-sm text-muted-foreground">
                Completed: {new Date(gangsheet.completedAt).toLocaleString()}
              </p>
            )}
          </div>
        </div>

        {/* Settings */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4">Settings</h2>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-muted-foreground">Roll Width:</span>
              <p className="font-medium">{gangsheet.settings.rollWidth}"</p>
            </div>
            <div>
              <span className="text-muted-foreground">Roll Height:</span>
              <p className="font-medium">{gangsheet.settings.rollHeight}"</p>
            </div>
            <div>
              <span className="text-muted-foreground">DPI:</span>
              <p className="font-medium">{gangsheet.settings.dpi}</p>
            </div>
            <div>
              <span className="text-muted-foreground">Gap:</span>
              <p className="font-medium">{gangsheet.settings.gap}"</p>
            </div>
            <div>
              <span className="text-muted-foreground">Border:</span>
              <p className="font-medium">{gangsheet.settings.border ? 'Yes' : 'No'}</p>
            </div>
            {gangsheet.settings.border && (
              <div>
                <span className="text-muted-foreground">Border Size:</span>
                <p className="font-medium">{gangsheet.settings.borderSize}"</p>
              </div>
            )}
          </div>
        </div>

        {/* Orders */}
        <div className="lg:col-span-2 bg-card border border-border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4">
            Included Orders ({gangsheet.orderIds.length})
          </h2>
          <div className="flex flex-wrap gap-2">
            {gangsheet.orderIds.map((orderId) => (
              <Link
                key={orderId}
                to={`/orders/${orderId}`}
                className="px-3 py-1.5 bg-muted rounded-lg text-sm hover:bg-muted/80 transition-colors"
              >
                Order #{orderId}
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// Gangsheet Settings Page
function GangsheetSettingsPage() {
  const navigate = useNavigate();
  const { data: settings, isLoading } = useGangsheetSettings();
  const updateSettings = useUpdateGangsheetSettings();

  const [formData, setFormData] = useState({
    rollWidth: 24,
    rollHeight: 100,
    dpi: 300,
    gap: 0.25,
    border: true,
    borderSize: 0.25,
    borderColor: '#FF0000',
  });

  // Update form when settings load
  useState(() => {
    if (settings) {
      setFormData(settings);
    }
  });

  const handleSubmit = async () => {
    try {
      await updateSettings.mutateAsync(formData);
      alert('Settings saved successfully');
    } catch {
      alert('Failed to save settings');
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/gangsheet')}
          className="p-2 hover:bg-muted rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold">Gangsheet Settings</h1>
          <p className="text-muted-foreground">Configure default gangsheet generation settings</p>
        </div>
      </div>

      <div className="max-w-2xl">
        <div className="bg-card border border-border rounded-xl p-6 space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm text-muted-foreground">Roll Width (inches)</label>
              <input
                type="number"
                step="0.5"
                value={formData.rollWidth}
                onChange={(e) => setFormData({ ...formData, rollWidth: Number(e.target.value) })}
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Roll Height (inches)</label>
              <input
                type="number"
                step="1"
                value={formData.rollHeight}
                onChange={(e) => setFormData({ ...formData, rollHeight: Number(e.target.value) })}
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm text-muted-foreground">DPI</label>
              <input
                type="number"
                value={formData.dpi}
                onChange={(e) => setFormData({ ...formData, dpi: Number(e.target.value) })}
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Gap (inches)</label>
              <input
                type="number"
                step="0.05"
                value={formData.gap}
                onChange={(e) => setFormData({ ...formData, gap: Number(e.target.value) })}
                className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
          </div>

          <div className="flex items-center gap-3">
            <input
              type="checkbox"
              id="border"
              checked={formData.border}
              onChange={(e) => setFormData({ ...formData, border: e.target.checked })}
              className="rounded border-border"
            />
            <label htmlFor="border" className="text-sm">Enable border around designs</label>
          </div>

          {formData.border && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm text-muted-foreground">Border Size (inches)</label>
                <input
                  type="number"
                  step="0.05"
                  value={formData.borderSize}
                  onChange={(e) => setFormData({ ...formData, borderSize: Number(e.target.value) })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                />
              </div>
              <div>
                <label className="text-sm text-muted-foreground">Border Color</label>
                <input
                  type="color"
                  value={formData.borderColor}
                  onChange={(e) => setFormData({ ...formData, borderColor: e.target.value })}
                  className="w-full mt-1 h-10 bg-background border border-border rounded-lg"
                />
              </div>
            </div>
          )}

          <button
            onClick={handleSubmit}
            disabled={updateSettings.isPending}
            className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {updateSettings.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Saving...
              </>
            ) : (
              'Save Settings'
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

// Main Gangsheet Component with Routes
export default function Gangsheet() {
  return (
    <Routes>
      <Route index element={<GangsheetList />} />
      <Route path="create" element={<GangsheetCreate />} />
      <Route path="settings" element={<GangsheetSettingsPage />} />
      <Route path=":id" element={<GangsheetDetail />} />
    </Routes>
  );
}
