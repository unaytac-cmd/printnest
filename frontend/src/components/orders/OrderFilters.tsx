import { useState, useCallback } from 'react';
import * as Popover from '@radix-ui/react-popover';
import * as Select from '@radix-ui/react-select';
import { Button, Input } from '@/components/ui';
import { cn } from '@/lib/utils';
import {
  SearchIcon,
  XIcon,
  CalendarIcon,
  ChevronDownIcon,
  CheckIcon,
  FilterIcon,
} from 'lucide-react';
import { OrderStatusType } from './OrderStatusBadge';

export interface OrderFilterValues {
  search?: string;
  statuses?: OrderStatusType[];
  storeId?: string;
  startDate?: string;
  endDate?: string;
}

interface Store {
  id: string;
  name: string;
}

interface OrderFiltersProps {
  filters: OrderFilterValues;
  onFiltersChange: (filters: OrderFilterValues) => void;
  stores?: Store[];
  className?: string;
}

const statusOptions: { value: OrderStatusType; label: string }[] = [
  { value: 'draft', label: 'Draft' },
  { value: 'payment_pending', label: 'Payment Pending' },
  { value: 'pending', label: 'Pending' },
  { value: 'in_production', label: 'In Production' },
  { value: 'shipped', label: 'Shipped' },
  { value: 'cancelled', label: 'Cancelled' },
];

export function OrderFilters({
  filters,
  onFiltersChange,
  stores = [],
  className,
}: OrderFiltersProps) {
  const [searchValue, setSearchValue] = useState(filters.search || '');

  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchValue(value);
      // Debounce search input
      const timeoutId = setTimeout(() => {
        onFiltersChange({ ...filters, search: value || undefined });
      }, 300);
      return () => clearTimeout(timeoutId);
    },
    [filters, onFiltersChange]
  );

  const handleStatusToggle = useCallback(
    (status: OrderStatusType) => {
      const currentStatuses = filters.statuses || [];
      const newStatuses = currentStatuses.includes(status)
        ? currentStatuses.filter((s) => s !== status)
        : [...currentStatuses, status];
      onFiltersChange({
        ...filters,
        statuses: newStatuses.length > 0 ? newStatuses : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const handleStoreChange = useCallback(
    (storeId: string) => {
      onFiltersChange({
        ...filters,
        storeId: storeId === 'all' ? undefined : storeId,
      });
    },
    [filters, onFiltersChange]
  );

  const handleDateChange = useCallback(
    (field: 'startDate' | 'endDate', value: string) => {
      onFiltersChange({
        ...filters,
        [field]: value || undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const handleClearFilters = useCallback(() => {
    setSearchValue('');
    onFiltersChange({});
  }, [onFiltersChange]);

  const hasActiveFilters =
    filters.search ||
    (filters.statuses && filters.statuses.length > 0) ||
    filters.storeId ||
    filters.startDate ||
    filters.endDate;

  return (
    <div className={cn('space-y-4', className)}>
      <div className="flex flex-wrap items-center gap-3">
        {/* Search Input */}
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <SearchIcon className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search orders..."
            value={searchValue}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="pl-9"
          />
        </div>

        {/* Status Multi-Select */}
        <Popover.Root>
          <Popover.Trigger asChild>
            <Button variant="outline" className="min-w-[140px]">
              <FilterIcon className="mr-2 h-4 w-4" />
              Status
              {filters.statuses && filters.statuses.length > 0 && (
                <span className="ml-2 rounded-full bg-primary px-1.5 py-0.5 text-xs text-primary-foreground">
                  {filters.statuses.length}
                </span>
              )}
              <ChevronDownIcon className="ml-2 h-4 w-4" />
            </Button>
          </Popover.Trigger>
          <Popover.Portal>
            <Popover.Content
              className="z-50 w-48 rounded-md border bg-popover p-2 shadow-md"
              sideOffset={4}
            >
              <div className="space-y-1">
                {statusOptions.map((option) => (
                  <button
                    key={option.value}
                    onClick={() => handleStatusToggle(option.value)}
                    className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent"
                  >
                    <div
                      className={cn(
                        'flex h-4 w-4 items-center justify-center rounded border',
                        filters.statuses?.includes(option.value)
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-input'
                      )}
                    >
                      {filters.statuses?.includes(option.value) && (
                        <CheckIcon className="h-3 w-3" />
                      )}
                    </div>
                    {option.label}
                  </button>
                ))}
              </div>
            </Popover.Content>
          </Popover.Portal>
        </Popover.Root>

        {/* Store Filter */}
        {stores.length > 0 && (
          <Select.Root
            value={filters.storeId || 'all'}
            onValueChange={handleStoreChange}
          >
            <Select.Trigger className="inline-flex h-10 items-center justify-between gap-2 rounded-md border border-input bg-background px-3 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 min-w-[140px]">
              <Select.Value placeholder="All Stores" />
              <Select.Icon>
                <ChevronDownIcon className="h-4 w-4 opacity-50" />
              </Select.Icon>
            </Select.Trigger>
            <Select.Portal>
              <Select.Content className="z-50 overflow-hidden rounded-md border bg-popover shadow-md">
                <Select.Viewport className="p-1">
                  <Select.Item
                    value="all"
                    className="relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-accent focus:bg-accent"
                  >
                    <Select.ItemIndicator className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                      <CheckIcon className="h-4 w-4" />
                    </Select.ItemIndicator>
                    <Select.ItemText>All Stores</Select.ItemText>
                  </Select.Item>
                  {stores.map((store) => (
                    <Select.Item
                      key={store.id}
                      value={store.id}
                      className="relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-accent focus:bg-accent"
                    >
                      <Select.ItemIndicator className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                        <CheckIcon className="h-4 w-4" />
                      </Select.ItemIndicator>
                      <Select.ItemText>{store.name}</Select.ItemText>
                    </Select.Item>
                  ))}
                </Select.Viewport>
              </Select.Content>
            </Select.Portal>
          </Select.Root>
        )}

        {/* Date Range */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <CalendarIcon className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="date"
              placeholder="Start date"
              value={filters.startDate || ''}
              onChange={(e) => handleDateChange('startDate', e.target.value)}
              className="pl-9 w-[150px]"
            />
          </div>
          <span className="text-muted-foreground">to</span>
          <div className="relative">
            <CalendarIcon className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="date"
              placeholder="End date"
              value={filters.endDate || ''}
              onChange={(e) => handleDateChange('endDate', e.target.value)}
              className="pl-9 w-[150px]"
            />
          </div>
        </div>

        {/* Clear Filters */}
        {hasActiveFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleClearFilters}
            className="text-muted-foreground"
          >
            <XIcon className="mr-1 h-4 w-4" />
            Clear
          </Button>
        )}
      </div>
    </div>
  );
}
