import { Upload, Palette, Type, Image, Layers, Download, Save } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function DesignStudio() {
  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-border">
        <div>
          <h1 className="text-2xl font-bold">Design Studio</h1>
          <p className="text-muted-foreground">
            Create custom designs for your products
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button className="inline-flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-accent transition-colors">
            <Download className="w-4 h-4" />
            Export
          </button>
          <button className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors">
            <Save className="w-4 h-4" />
            Save Design
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex gap-4 pt-4 overflow-hidden">
        {/* Toolbar */}
        <div className="w-16 bg-card border border-border rounded-xl p-2 flex flex-col gap-2">
          <button
            className="p-3 rounded-lg bg-primary text-primary-foreground"
            title="Upload Image"
          >
            <Upload className="w-5 h-5" />
          </button>
          <button
            className="p-3 rounded-lg hover:bg-muted transition-colors"
            title="Add Text"
          >
            <Type className="w-5 h-5" />
          </button>
          <button
            className="p-3 rounded-lg hover:bg-muted transition-colors"
            title="Add Shape"
          >
            <Layers className="w-5 h-5" />
          </button>
          <button
            className="p-3 rounded-lg hover:bg-muted transition-colors"
            title="Add Image"
          >
            <Image className="w-5 h-5" />
          </button>
          <button
            className="p-3 rounded-lg hover:bg-muted transition-colors"
            title="Colors"
          >
            <Palette className="w-5 h-5" />
          </button>
        </div>

        {/* Canvas Area */}
        <div className="flex-1 bg-muted/50 rounded-xl flex items-center justify-center">
          <div className="bg-white rounded-lg shadow-lg w-96 h-96 flex items-center justify-center">
            <div className="text-center text-muted-foreground">
              <Image className="w-16 h-16 mx-auto mb-4 opacity-50" />
              <p className="text-lg font-medium">Design Canvas</p>
              <p className="text-sm">
                Click the upload button to add your design
              </p>
            </div>
          </div>
        </div>

        {/* Properties Panel */}
        <div className="w-72 bg-card border border-border rounded-xl p-4 overflow-y-auto">
          <h3 className="font-semibold mb-4">Product Preview</h3>

          {/* Product Selector */}
          <div className="space-y-4">
            <div>
              <label className="text-sm text-muted-foreground">Product Type</label>
              <select className="w-full mt-1 px-3 py-2 border border-border rounded-lg bg-background">
                <option>T-Shirt</option>
                <option>Hoodie</option>
                <option>Mug</option>
                <option>Phone Case</option>
                <option>Poster</option>
              </select>
            </div>

            <div>
              <label className="text-sm text-muted-foreground">Color</label>
              <div className="flex gap-2 mt-2">
                {['white', 'black', 'navy', 'red', 'green'].map((color) => (
                  <button
                    key={color}
                    className={cn(
                      'w-8 h-8 rounded-full border-2',
                      color === 'white' && 'bg-white border-gray-300',
                      color === 'black' && 'bg-black border-black',
                      color === 'navy' && 'bg-blue-900 border-blue-900',
                      color === 'red' && 'bg-red-600 border-red-600',
                      color === 'green' && 'bg-green-600 border-green-600'
                    )}
                    title={color}
                  />
                ))}
              </div>
            </div>

            <div>
              <label className="text-sm text-muted-foreground">Size</label>
              <div className="flex gap-2 mt-2">
                {['XS', 'S', 'M', 'L', 'XL', '2XL'].map((size) => (
                  <button
                    key={size}
                    className={cn(
                      'px-3 py-1 text-sm rounded-lg border border-border hover:bg-muted transition-colors',
                      size === 'M' && 'bg-primary text-primary-foreground border-primary'
                    )}
                  >
                    {size}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <hr className="my-6 border-border" />

          <h3 className="font-semibold mb-4">Design Properties</h3>
          <div className="space-y-4">
            <div>
              <label className="text-sm text-muted-foreground">Position X</label>
              <input
                type="range"
                className="w-full mt-1"
                min="0"
                max="100"
                defaultValue="50"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Position Y</label>
              <input
                type="range"
                className="w-full mt-1"
                min="0"
                max="100"
                defaultValue="50"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Scale</label>
              <input
                type="range"
                className="w-full mt-1"
                min="10"
                max="200"
                defaultValue="100"
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground">Rotation</label>
              <input
                type="range"
                className="w-full mt-1"
                min="0"
                max="360"
                defaultValue="0"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
