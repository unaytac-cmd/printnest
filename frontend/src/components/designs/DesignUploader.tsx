import { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { Button, Card, CardContent } from '@/components/ui';
import { cn } from '@/lib/utils';
import {
  UploadCloudIcon,
  FileIcon,
  XIcon,
  CheckCircleIcon,
  AlertCircleIcon,
} from 'lucide-react';

interface UploadedFile {
  id: string;
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error';
  error?: string;
}

interface DesignUploaderProps {
  onUpload: (files: File[]) => Promise<void>;
  maxFiles?: number;
  maxSize?: number; // in bytes
  acceptedTypes?: string[];
  className?: string;
}

const defaultAcceptedTypes = [
  'image/png',
  'image/jpeg',
  'image/jpg',
  'application/x-dst', // DST embroidery files
  'application/illustrator', // AI files
  'image/svg+xml', // SVG files
];

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

export function DesignUploader({
  onUpload,
  maxFiles = 10,
  maxSize = 50 * 1024 * 1024, // 50MB default
  acceptedTypes = defaultAcceptedTypes,
  className,
}: DesignUploaderProps) {
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([]);
  const [isUploading, setIsUploading] = useState(false);

  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      // Create upload file entries
      const newFiles: UploadedFile[] = acceptedFiles.map((file) => ({
        id: Math.random().toString(36).substr(2, 9),
        file,
        progress: 0,
        status: 'pending' as const,
      }));

      setUploadedFiles((prev) => [...prev, ...newFiles]);

      // Start upload
      setIsUploading(true);

      try {
        // Update status to uploading
        setUploadedFiles((prev) =>
          prev.map((f) =>
            newFiles.some((nf) => nf.id === f.id)
              ? { ...f, status: 'uploading' as const, progress: 50 }
              : f
          )
        );

        await onUpload(acceptedFiles);

        // Update status to success
        setUploadedFiles((prev) =>
          prev.map((f) =>
            newFiles.some((nf) => nf.id === f.id)
              ? { ...f, status: 'success' as const, progress: 100 }
              : f
          )
        );
      } catch (error) {
        // Update status to error
        setUploadedFiles((prev) =>
          prev.map((f) =>
            newFiles.some((nf) => nf.id === f.id)
              ? {
                  ...f,
                  status: 'error' as const,
                  error: error instanceof Error ? error.message : 'Upload failed',
                }
              : f
          )
        );
      } finally {
        setIsUploading(false);
      }
    },
    [onUpload]
  );

  const { getRootProps, getInputProps, isDragActive, fileRejections } = useDropzone({
    onDrop,
    accept: acceptedTypes.reduce((acc, type) => ({ ...acc, [type]: [] }), {}),
    maxFiles,
    maxSize,
    disabled: isUploading,
  });

  const removeFile = (id: string) => {
    setUploadedFiles((prev) => prev.filter((f) => f.id !== id));
  };

  const clearCompleted = () => {
    setUploadedFiles((prev) =>
      prev.filter((f) => f.status !== 'success' && f.status !== 'error')
    );
  };

  return (
    <div className={cn('space-y-4', className)}>
      {/* Drop Zone */}
      <div
        {...getRootProps()}
        className={cn(
          'relative cursor-pointer rounded-lg border-2 border-dashed p-8 text-center transition-colors',
          isDragActive
            ? 'border-primary bg-primary/5'
            : 'border-muted-foreground/25 hover:border-primary/50',
          isUploading && 'cursor-not-allowed opacity-50'
        )}
      >
        <input {...getInputProps()} />
        <UploadCloudIcon className="mx-auto h-12 w-12 text-muted-foreground" />
        <h3 className="mt-4 text-lg font-medium">
          {isDragActive ? 'Drop files here' : 'Drag & drop files here'}
        </h3>
        <p className="mt-2 text-sm text-muted-foreground">
          or click to browse from your computer
        </p>
        <p className="mt-4 text-xs text-muted-foreground">
          Supported formats: PNG, JPG, DST, AI, SVG (max {formatFileSize(maxSize)})
        </p>
      </div>

      {/* File Rejections */}
      {fileRejections.length > 0 && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4">
          <h4 className="flex items-center gap-2 font-medium text-destructive">
            <AlertCircleIcon className="h-4 w-4" />
            Some files were rejected
          </h4>
          <ul className="mt-2 space-y-1 text-sm text-destructive">
            {fileRejections.map(({ file, errors }) => (
              <li key={file.name}>
                {file.name}: {errors.map((e) => e.message).join(', ')}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Upload Progress */}
      {uploadedFiles.length > 0 && (
        <Card>
          <CardContent className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <h4 className="font-medium">Uploads</h4>
              {uploadedFiles.some(
                (f) => f.status === 'success' || f.status === 'error'
              ) && (
                <Button variant="ghost" size="sm" onClick={clearCompleted}>
                  Clear Completed
                </Button>
              )}
            </div>
            <div className="space-y-3">
              {uploadedFiles.map((uploadedFile) => (
                <div
                  key={uploadedFile.id}
                  className="flex items-center gap-3 rounded-lg border p-3"
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded bg-muted">
                    <FileIcon className="h-5 w-5 text-muted-foreground" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{uploadedFile.file.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {formatFileSize(uploadedFile.file.size)}
                    </p>
                    {uploadedFile.status === 'uploading' && (
                      <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                        <div
                          className="h-full bg-primary transition-all"
                          style={{ width: `${uploadedFile.progress}%` }}
                        />
                      </div>
                    )}
                    {uploadedFile.status === 'error' && (
                      <p className="mt-1 text-sm text-destructive">
                        {uploadedFile.error}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {uploadedFile.status === 'success' && (
                      <CheckCircleIcon className="h-5 w-5 text-green-500" />
                    )}
                    {uploadedFile.status === 'error' && (
                      <AlertCircleIcon className="h-5 w-5 text-destructive" />
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => removeFile(uploadedFile.id)}
                    >
                      <XIcon className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
