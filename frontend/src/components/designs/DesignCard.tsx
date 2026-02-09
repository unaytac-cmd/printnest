import { useState } from 'react';
import { Card, CardContent } from '@/components/ui';
import { Button } from '@/components/ui';
import { cn } from '@/lib/utils';
import type { Design } from '@/types';
import { EyeIcon, DownloadIcon, TrashIcon, ImageIcon } from 'lucide-react';
import { cva } from 'class-variance-authority';

// Design type badge variants
const designTypeBadgeVariants = cva(
  'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
  {
    variants: {
      type: {
        image: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
        embroidery: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
        vector: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
      },
    },
    defaultVariants: {
      type: 'image',
    },
  }
);

type DesignType = 'image' | 'embroidery' | 'vector';

interface DesignCardProps {
  design: Design & { type?: DesignType };
  selected?: boolean;
  onSelect?: (design: Design) => void;
  onPreview?: (design: Design) => void;
  onDelete?: (design: Design) => void;
  onDownload?: (design: Design) => void;
  selectable?: boolean;
}

export function DesignCard({
  design,
  selected = false,
  onSelect,
  onPreview,
  onDelete,
  onDownload,
  selectable = false,
}: DesignCardProps) {
  const [isHovered, setIsHovered] = useState(false);

  const designType: DesignType = (design as Design & { type?: DesignType }).type || 'image';

  const handleClick = () => {
    if (selectable && onSelect) {
      onSelect(design);
    }
  };

  return (
    <Card
      className={cn(
        'group relative overflow-hidden transition-all',
        selectable && 'cursor-pointer hover:ring-2 hover:ring-primary',
        selected && 'ring-2 ring-primary'
      )}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Thumbnail */}
      <div className="relative aspect-square overflow-hidden bg-muted">
        {design.thumbnail ? (
          <img
            src={design.thumbnail}
            alt={design.name}
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <ImageIcon className="h-12 w-12 text-muted-foreground" />
          </div>
        )}

        {/* Selection indicator */}
        {selectable && selected && (
          <div className="absolute right-2 top-2 rounded-full bg-primary p-1">
            <svg
              className="h-4 w-4 text-primary-foreground"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
        )}

        {/* Hover Actions */}
        {isHovered && !selectable && (
          <div className="absolute inset-0 flex items-center justify-center gap-2 bg-black/50">
            {onPreview && (
              <Button
                variant="secondary"
                size="icon"
                className="h-9 w-9"
                onClick={(e) => {
                  e.stopPropagation();
                  onPreview(design);
                }}
              >
                <EyeIcon className="h-4 w-4" />
              </Button>
            )}
            {onDownload && (
              <Button
                variant="secondary"
                size="icon"
                className="h-9 w-9"
                onClick={(e) => {
                  e.stopPropagation();
                  onDownload(design);
                }}
              >
                <DownloadIcon className="h-4 w-4" />
              </Button>
            )}
            {onDelete && (
              <Button
                variant="secondary"
                size="icon"
                className="h-9 w-9 hover:bg-destructive hover:text-destructive-foreground"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(design);
                }}
              >
                <TrashIcon className="h-4 w-4" />
              </Button>
            )}
          </div>
        )}
      </div>

      {/* Card Content */}
      <CardContent className="p-3">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <h3 className="truncate font-medium">{design.name}</h3>
            <p className="text-sm text-muted-foreground">
              {design.data.width} x {design.data.height}px
            </p>
          </div>
          <span className={designTypeBadgeVariants({ type: designType })}>
            {designType.charAt(0).toUpperCase() + designType.slice(1)}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
