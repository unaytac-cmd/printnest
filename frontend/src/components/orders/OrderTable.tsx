import { useState, useMemo, useCallback } from 'react';
import {
  ColumnDef,
  PaginationState,
  RowSelectionState,
  SortingState,
} from '@tanstack/react-table';
import { DataTable } from '@/components/common';
import { Button } from '@/components/ui';
import { OrderStatusBadge, mapApiStatusToBadgeStatus } from './OrderStatusBadge';
import { OrderFilters, OrderFilterValues } from './OrderFilters';
import { formatCurrency, formatDate } from '@/lib/utils';
import type { Order } from '@/types';
import {
  MoreHorizontalIcon,
  EyeIcon,
  EditIcon,
  TruckIcon,
  FileSpreadsheetIcon,
  CheckSquareIcon,
  ChevronDownIcon,
} from 'lucide-react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import * as Checkbox from '@radix-ui/react-checkbox';
import { CheckIcon } from 'lucide-react';

interface Store {
  id: string;
  name: string;
}

interface OrderTableProps {
  orders: Order[];
  loading?: boolean;
  totalCount: number;
  pageCount: number;
  pagination: PaginationState;
  onPaginationChange: (pagination: PaginationState) => void;
  sorting?: SortingState;
  onSortingChange?: (sorting: SortingState) => void;
  filters: OrderFilterValues;
  onFiltersChange: (filters: OrderFilterValues) => void;
  stores?: Store[];
  onViewOrder: (order: Order) => void;
  onEditOrder?: (order: Order) => void;
  onBulkStatusChange?: (orderIds: string[], status: string) => void;
  onBulkExport?: (orderIds: string[]) => void;
  onCreateGangsheet?: (orderIds: string[]) => void;
}

export function OrderTable({
  orders,
  loading = false,
  totalCount,
  pageCount,
  pagination,
  onPaginationChange,
  sorting,
  onSortingChange,
  filters,
  onFiltersChange,
  stores = [],
  onViewOrder,
  onEditOrder,
  onBulkStatusChange,
  onBulkExport,
  onCreateGangsheet,
}: OrderTableProps) {
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});

  const selectedOrderIds = useMemo(() => {
    return Object.keys(rowSelection)
      .filter((key) => rowSelection[key])
      .map((index) => orders[parseInt(index)]?.id)
      .filter(Boolean);
  }, [rowSelection, orders]);

  const columns: ColumnDef<Order>[] = useMemo(
    () => [
      {
        id: 'select',
        header: ({ table }) => (
          <Checkbox.Root
            checked={
              table.getIsAllPageRowsSelected() ||
              (table.getIsSomePageRowsSelected() && 'indeterminate')
            }
            onCheckedChange={(value) =>
              table.toggleAllPageRowsSelected(!!value)
            }
            className="flex h-4 w-4 items-center justify-center rounded border border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground"
          >
            <Checkbox.Indicator>
              <CheckIcon className="h-3 w-3" />
            </Checkbox.Indicator>
          </Checkbox.Root>
        ),
        cell: ({ row }) => (
          <Checkbox.Root
            checked={row.getIsSelected()}
            onCheckedChange={(value) => row.toggleSelected(!!value)}
            className="flex h-4 w-4 items-center justify-center rounded border border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground"
          >
            <Checkbox.Indicator>
              <CheckIcon className="h-3 w-3" />
            </Checkbox.Indicator>
          </Checkbox.Root>
        ),
        enableSorting: false,
        size: 40,
      },
      {
        accessorKey: 'orderNumber',
        header: 'Order ID',
        cell: ({ row }) => (
          <button
            onClick={() => onViewOrder(row.original)}
            className="font-medium text-primary hover:underline"
          >
            #{row.original.orderNumber}
          </button>
        ),
      },
      {
        id: 'externalId',
        header: 'External ID',
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {(row.original as Order & { externalId?: string }).externalId || '-'}
          </span>
        ),
      },
      {
        id: 'customer',
        header: 'Customer',
        cell: ({ row }) => (
          <div>
            <div className="font-medium">
              {row.original.customer.firstName} {row.original.customer.lastName}
            </div>
            <div className="text-sm text-muted-foreground">
              {row.original.customer.email}
            </div>
          </div>
        ),
      },
      {
        id: 'store',
        header: 'Store',
        cell: ({ row }) => {
          const store = stores.find(
            (s) => s.id === (row.original as Order & { storeId?: string }).storeId
          );
          return <span>{store?.name || '-'}</span>;
        },
      },
      {
        accessorKey: 'total',
        header: 'Total',
        cell: ({ row }) => (
          <span className="font-medium">
            {formatCurrency(row.original.total)}
          </span>
        ),
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <OrderStatusBadge
            status={mapApiStatusToBadgeStatus(
              row.original.status,
              row.original.paymentStatus
            )}
          />
        ),
      },
      {
        accessorKey: 'createdAt',
        header: 'Date',
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {formatDate(row.original.createdAt)}
          </span>
        ),
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => (
          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8">
                <MoreHorizontalIcon className="h-4 w-4" />
              </Button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <DropdownMenu.Content
                className="z-50 min-w-[160px] rounded-md border bg-popover p-1 shadow-md"
                align="end"
              >
                <DropdownMenu.Item
                  className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none hover:bg-accent"
                  onClick={() => onViewOrder(row.original)}
                >
                  <EyeIcon className="h-4 w-4" />
                  View Details
                </DropdownMenu.Item>
                {onEditOrder && (
                  <DropdownMenu.Item
                    className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none hover:bg-accent"
                    onClick={() => onEditOrder(row.original)}
                  >
                    <EditIcon className="h-4 w-4" />
                    Edit Order
                  </DropdownMenu.Item>
                )}
                <DropdownMenu.Separator className="my-1 h-px bg-muted" />
                <DropdownMenu.Item className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none hover:bg-accent">
                  <TruckIcon className="h-4 w-4" />
                  Print Label
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        ),
        size: 50,
      },
    ],
    [onViewOrder, onEditOrder, stores]
  );

  const handleClearSelection = useCallback(() => {
    setRowSelection({});
  }, []);

  // Batch Actions Toolbar
  const toolbar = selectedOrderIds.length > 0 && (
    <div className="flex items-center gap-2 rounded-lg border bg-muted/50 p-2">
      <span className="text-sm font-medium">
        {selectedOrderIds.length} selected
      </span>
      <div className="h-4 w-px bg-border" />

      {onBulkStatusChange && (
        <DropdownMenu.Root>
          <DropdownMenu.Trigger asChild>
            <Button variant="outline" size="sm">
              <CheckSquareIcon className="mr-2 h-4 w-4" />
              Change Status
              <ChevronDownIcon className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content className="z-50 min-w-[160px] rounded-md border bg-popover p-1 shadow-md">
              {['pending', 'in_production', 'shipped', 'cancelled'].map(
                (status) => (
                  <DropdownMenu.Item
                    key={status}
                    className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none hover:bg-accent"
                    onClick={() => onBulkStatusChange(selectedOrderIds, status)}
                  >
                    {status.replace('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
                  </DropdownMenu.Item>
                )
              )}
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
      )}

      {onBulkExport && (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onBulkExport(selectedOrderIds)}
        >
          <FileSpreadsheetIcon className="mr-2 h-4 w-4" />
          Export
        </Button>
      )}

      {onCreateGangsheet && (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onCreateGangsheet(selectedOrderIds)}
        >
          Create Gangsheet
        </Button>
      )}

      <Button variant="ghost" size="sm" onClick={handleClearSelection}>
        Clear
      </Button>
    </div>
  );

  return (
    <div className="space-y-4">
      <OrderFilters
        filters={filters}
        onFiltersChange={onFiltersChange}
        stores={stores}
      />

      <DataTable
        columns={columns}
        data={orders}
        loading={loading}
        pagination={pagination}
        onPaginationChange={(updater) => {
          const newPagination =
            typeof updater === 'function' ? updater(pagination) : updater;
          onPaginationChange(newPagination);
        }}
        pageCount={pageCount}
        totalCount={totalCount}
        rowSelection={rowSelection}
        onRowSelectionChange={setRowSelection}
        enableRowSelection
        sorting={sorting}
        onSortingChange={onSortingChange ? (updater) => {
          const newSorting =
            typeof updater === 'function' ? updater(sorting || []) : updater;
          onSortingChange(newSorting);
        } : undefined}
        toolbar={toolbar}
        emptyState={{
          title: 'No orders found',
          description: 'Try adjusting your filters or create a new order.',
        }}
      />
    </div>
  );
}
