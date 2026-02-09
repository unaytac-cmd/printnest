import { useState } from 'react';
import {
  Building2,
  Search,
  Plus,
  MoreHorizontal,
  CheckCircle,
  AlertCircle,
  Clock,
  ExternalLink,
  Edit,
  Trash2,
  UserPlus,
  Ban,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface Tenant {
  id: string;
  name: string;
  slug: string;
  email: string;
  plan: 'starter' | 'professional' | 'enterprise';
  status: 'active' | 'suspended' | 'trial';
  users: number;
  orders: number;
  revenue: string;
  createdAt: string;
}

export default function TenantsPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [planFilter, setPlanFilter] = useState<string>('all');
  const [selectedTenant, setSelectedTenant] = useState<string | null>(null);

  const tenants: Tenant[] = [
    {
      id: '1',
      name: 'Acme Print Co',
      slug: 'acme',
      email: 'admin@acmeprint.com',
      plan: 'enterprise',
      status: 'active',
      users: 12,
      orders: 1245,
      revenue: '$12,456',
      createdAt: '2024-01-15',
    },
    {
      id: '2',
      name: 'Best POD Shop',
      slug: 'bestpod',
      email: 'owner@bestpod.com',
      plan: 'professional',
      status: 'active',
      users: 5,
      orders: 856,
      revenue: '$8,234',
      createdAt: '2024-02-20',
    },
    {
      id: '3',
      name: 'Cool Tees',
      slug: 'cooltees',
      email: 'hello@cooltees.com',
      plan: 'starter',
      status: 'trial',
      users: 1,
      orders: 23,
      revenue: '$456',
      createdAt: '2024-03-01',
    },
    {
      id: '4',
      name: 'PrintMaster Pro',
      slug: 'printmaster',
      email: 'info@printmaster.com',
      plan: 'professional',
      status: 'active',
      users: 8,
      orders: 567,
      revenue: '$5,678',
      createdAt: '2024-01-28',
    },
    {
      id: '5',
      name: 'Design Hub',
      slug: 'designhub',
      email: 'team@designhub.io',
      plan: 'enterprise',
      status: 'active',
      users: 25,
      orders: 2341,
      revenue: '$23,456',
      createdAt: '2023-11-10',
    },
    {
      id: '6',
      name: 'Suspended Store',
      slug: 'suspended',
      email: 'owner@suspended.com',
      plan: 'starter',
      status: 'suspended',
      users: 2,
      orders: 45,
      revenue: '$890',
      createdAt: '2024-02-01',
    },
  ];

  const planColors = {
    starter: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200',
    professional: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    enterprise: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  };

  const statusColors = {
    active: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    suspended: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    trial: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
  };

  const statusIcons = {
    active: CheckCircle,
    suspended: AlertCircle,
    trial: Clock,
  };

  const filteredTenants = tenants.filter((tenant) => {
    const matchesSearch = tenant.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tenant.slug.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tenant.email.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = statusFilter === 'all' || tenant.status === statusFilter;
    const matchesPlan = planFilter === 'all' || tenant.plan === planFilter;
    return matchesSearch && matchesStatus && matchesPlan;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Tenants</h1>
          <p className="text-muted-foreground">
            Manage all tenants on the platform
          </p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors">
          <Plus className="w-4 h-4" />
          Add Tenant
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search tenants..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-4 py-2 border border-border rounded-lg bg-background"
        >
          <option value="all">All Status</option>
          <option value="active">Active</option>
          <option value="trial">Trial</option>
          <option value="suspended">Suspended</option>
        </select>
        <select
          value={planFilter}
          onChange={(e) => setPlanFilter(e.target.value)}
          className="px-4 py-2 border border-border rounded-lg bg-background"
        >
          <option value="all">All Plans</option>
          <option value="starter">Starter</option>
          <option value="professional">Professional</option>
          <option value="enterprise">Enterprise</option>
        </select>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-card rounded-lg border border-border p-4">
          <p className="text-sm text-muted-foreground">Total Tenants</p>
          <p className="text-2xl font-bold">{tenants.length}</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <p className="text-sm text-muted-foreground">Active</p>
          <p className="text-2xl font-bold text-green-600">
            {tenants.filter(t => t.status === 'active').length}
          </p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <p className="text-sm text-muted-foreground">Trial</p>
          <p className="text-2xl font-bold text-yellow-600">
            {tenants.filter(t => t.status === 'trial').length}
          </p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <p className="text-sm text-muted-foreground">Suspended</p>
          <p className="text-2xl font-bold text-red-600">
            {tenants.filter(t => t.status === 'suspended').length}
          </p>
        </div>
      </div>

      {/* Tenants Table */}
      <div className="bg-card rounded-xl border border-border overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Tenant</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Plan</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Status</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Users</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Orders</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Revenue</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Created</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredTenants.map((tenant) => {
                const StatusIcon = statusIcons[tenant.status];
                return (
                  <tr
                    key={tenant.id}
                    className="border-b border-border hover:bg-muted/50 transition-colors"
                  >
                    <td className="py-4 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                          <Building2 className="w-5 h-5 text-primary" />
                        </div>
                        <div>
                          <p className="font-medium">{tenant.name}</p>
                          <p className="text-sm text-muted-foreground">{tenant.slug}.printnest.com</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <span className={cn('px-2 py-1 text-xs rounded-full capitalize', planColors[tenant.plan])}>
                        {tenant.plan}
                      </span>
                    </td>
                    <td className="py-4 px-4">
                      <span className={cn('inline-flex items-center gap-1 px-2 py-1 text-xs rounded-full capitalize', statusColors[tenant.status])}>
                        <StatusIcon className="w-3 h-3" />
                        {tenant.status}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-right">{tenant.users}</td>
                    <td className="py-4 px-4 text-right">{tenant.orders.toLocaleString()}</td>
                    <td className="py-4 px-4 text-right font-medium">{tenant.revenue}</td>
                    <td className="py-4 px-4 text-muted-foreground">{tenant.createdAt}</td>
                    <td className="py-4 px-4">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          className="p-1.5 hover:bg-muted rounded-lg"
                          title="Visit Store"
                        >
                          <ExternalLink className="w-4 h-4 text-muted-foreground" />
                        </button>
                        <button
                          className="p-1.5 hover:bg-muted rounded-lg"
                          title="Edit"
                        >
                          <Edit className="w-4 h-4 text-muted-foreground" />
                        </button>
                        <div className="relative">
                          <button
                            onClick={() => setSelectedTenant(selectedTenant === tenant.id ? null : tenant.id)}
                            className="p-1.5 hover:bg-muted rounded-lg"
                          >
                            <MoreHorizontal className="w-4 h-4 text-muted-foreground" />
                          </button>
                          {selectedTenant === tenant.id && (
                            <div className="absolute right-0 mt-1 w-48 bg-card border border-border rounded-lg shadow-lg py-1 z-10">
                              <button className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-muted w-full">
                                <UserPlus className="w-4 h-4" />
                                Impersonate
                              </button>
                              <button className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-muted w-full">
                                <Ban className="w-4 h-4" />
                                {tenant.status === 'suspended' ? 'Activate' : 'Suspend'}
                              </button>
                              <hr className="my-1 border-border" />
                              <button className="flex items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-muted w-full">
                                <Trash2 className="w-4 h-4" />
                                Delete
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-border">
          <p className="text-sm text-muted-foreground">
            Showing {filteredTenants.length} of {tenants.length} tenants
          </p>
          <div className="flex items-center gap-2">
            <button className="px-3 py-1 text-sm border border-border rounded-lg hover:bg-muted disabled:opacity-50" disabled>
              Previous
            </button>
            <button className="px-3 py-1 text-sm border border-border rounded-lg hover:bg-muted disabled:opacity-50" disabled>
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
