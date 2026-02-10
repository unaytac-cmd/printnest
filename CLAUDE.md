# PrintNest - Project Documentation

## Proje Ozeti

PrintNest, multi-tenant Print-on-Demand SaaS platformu. DTF baski isletmeleri icin siparis yonetimi, gangsheet olusturma ve kargo entegrasyonu saglayan bir sistem.

## Tech Stack

### Backend
- **Kotlin + Ktor 3.0** - Web framework
- **Exposed ORM** - PostgreSQL database
- **Koin** - Dependency Injection
- **Redis** - Token cache

### Frontend
- **React 18 + TypeScript** - UI framework
- **Vite** - Build tool
- **Tailwind CSS + shadcn/ui** - Styling
- **Zustand** - State management
- **TanStack Query** - Server state
- **React Router** - Routing

---

## Production Deployment

### Sunucu Bilgileri
- **Provider:** DigitalOcean
- **IP:** 159.203.165.38
- **RAM:** 4GB
- **OS:** Ubuntu 24.04

### URL'ler
| Ortam | URL |
|-------|-----|
| **Frontend** | http://159.203.165.38.nip.io |
| **Backend API** | http://159.203.165.38.nip.io:8080/api/v1 |
| **GitHub Repo** | https://github.com/unaytac-cmd/printnest (public) |

### Docker Servisleri
```bash
# Servisleri kontrol et
ssh root@159.203.165.38 "docker ps"

# Loglari gor
ssh root@159.203.165.38 "docker logs printnest-backend --tail 50"
ssh root@159.203.165.38 "docker logs printnest-frontend --tail 50"

# Yeniden baslat
ssh root@159.203.165.38 "cd /opt/printnest && docker compose -f docker-compose.simple.yml restart"

# Veritabani erisimi
ssh root@159.203.165.38 "docker exec printnest-db psql -U printnest -d printnest"
```

### Deploy Komutlari
```bash
# Git uzerinden deploy (onerilen)
ssh root@159.203.165.38 "cd /opt/printnest && git pull && docker compose -f docker-compose.simple.yml up -d --build"

# Sadece frontend deploy
ssh root@159.203.165.38 "cd /opt/printnest && git pull && docker compose -f docker-compose.simple.yml up -d --build frontend"

# Sadece backend deploy (~5-7 dk surer)
ssh root@159.203.165.38 "cd /opt/printnest && git pull && docker compose -f docker-compose.simple.yml up -d --build backend"

# Commit, push ve deploy (tek komut)
git add -A && git commit -m "mesaj" && git push origin main && ssh root@159.203.165.38 "cd /opt/printnest && git pull && docker compose -f docker-compose.simple.yml up -d --build"
```

---

## Multi-Tenant Mimari

### Kullanici Hiyerarsisi
```
Super Admin (Platform sahibi)
    │
    ├── Tenant 1 (Producer - DTF isletmesi)
    │       ├── Sub-dealer 1 (Bayii)
    │       ├── Sub-dealer 2 (Bayii)
    │       └── Employees (Calisanlar)
    │
    ├── Tenant 2 (Producer)
    │       └── ...
```

### Kullanici Rolleri
| Rol | Erisim |
|-----|--------|
| **Super Admin** | Platform yonetimi, tum tenant'lara erisim |
| **Tenant Owner** | Tenant ayarlari, kullanici yonetimi |
| **Tenant Admin** | Siparis, urun, tasarim yonetimi |
| **Sub-dealer** | Sadece kendi siparisleri (atanan store'lar) |
| **Employee** | Sinirli erisim |

---

## Siparis Yonetimi

### Siparis Akisi
```
ShipStation API → PrintNest (Sync) → Gangsheet → Baski → Kargo
```

**ONEMLI:**
- Siparisler ShipStation'dan "awaiting_shipment" statusu ile cekilir
- Sadece gonderime hazir siparisler sync edilir

### Order Status Enum
```kotlin
enum class OrderStatus(val code: Int) {
    COMBINED(-4),
    COMPLETED(-3),
    INVALID_ADDRESS(-2),
    DELETED(-1),
    NEW_ORDER(0),
    CANCELLED(2),
    PAYMENT_PENDING(4),
    EDITING(8),
    PENDING(12),      // Odeme tamamlandi, baskiya hazir
    URGENT(14),
    IN_PRODUCTION(16),
    SHIPPED(20)
}
```

### Orders Sayfa Yapisi

Sidebar'da Orders menu'su altinda:
- **New Orders** (`/orders/new-orders`) - Bekleyen siparisler (NEW, PENDING, URGENT, IN_PRODUCTION, PAYMENT_PENDING)
- **Order List** (`/orders/list`) - Tamamlanan siparisler (SHIPPED, COMPLETED)

Her iki sayfada:
- Store bazli filtreleme (dropdown)
- Arama fonksiyonu
- Pagination
- New Orders'da "Sync Orders" butonu

### Store Filtreleme
- Frontend: `/shipstation/stores` endpoint'inden store listesi cekilir
- Backend: `storeId` parametresi hem `Orders.storeId` hem de `Orders.shipstationStoreId` kolonlarini kontrol eder
- Subdealerlar otomatik olarak atandiklari store'a filtrelenir

---

## ShipStation Entegrasyonu

### Baglanti
1. Settings > ShipStation'da API Key ve Secret girilir
2. `/shipstation/connect` endpoint'i credentials'i validate eder
3. Basarili ise `tenants.settings` JSONB'ye kaydedilir

### Store Sync
- `/shipstation/sync-stores` - ShipStation'daki store'lari `shipstation_stores` tablosuna kaydeder
- Her store'un `shipstationStoreId` (ShipStation'daki ID) ve `id` (bizim ID) degerleri var

### Order Sync
- `/shipstation/sync-orders` - Sadece "awaiting_shipment" siparisleri cekilir
- Siparisler `orders` tablosuna `shipstationStoreId` ile kaydedilir
- Order items `order_products` tablosuna kaydedilir

### Onemli Dosyalar
```
backend/src/main/kotlin/com/printnest/integrations/shipstation/
├── ShipStationClient.kt    # HTTP client, API cagrilari
├── ShipStationService.kt   # Business logic, order sync
├── ShipStationModels.kt    # Request/Response modelleri
```

### Serialization Notu
ShipStation order sync'de `buildJsonObject` kullanilmali, `mapOf` degil. Cunku:
- `mapOf` karisik tipler icerdiginde (String, Boolean, Double) `Map<String, Any>` olusturur
- kotlinx.serialization `Any` tipini serialize edemez
- `buildJsonObject` ile her alan tipine gore `put()` kullanilmali

```kotlin
// YANLIS - Serialization hatasi verir
val json = json.encodeToString(mapOf(
    "name" to "value",
    "gift" to true  // Boolean - Map<String, Any> olusturur
))

// DOGRU
val json = buildJsonObject {
    put("name", "value")
    put("gift", true)
}.toString()
```

---

## Veritabani

### Onemli Tablolar
| Tablo | Aciklama |
|-------|----------|
| `tenants` | Isletmeler (settings JSONB kolonu) |
| `users` | Kullanicilar |
| `orders` | Siparisler |
| `order_products` | Siparis urunleri |
| `shipstation_stores` | ShipStation store'lari |
| `products` | Urunler |
| `variants` | Urun varyantlari |
| `gangsheets` | Gangsheet'ler |
| `designs` | Tasarimlar |

### Settings JSONB Yapisi (tenants.settings)
```json
{
  "shipstationSettings": {
    "apiKey": "xxx",
    "apiSecret": "xxx"
  },
  "stripeSettings": {
    "publicKey": "pk_xxx",
    "secretKey": "sk_xxx"
  },
  "awsSettings": {
    "accessKeyId": "xxx",
    "secretAccessKey": "xxx",
    "region": "us-east-1",
    "bucket": "xxx"
  },
  "shippingSettings": {
    "easyPostApiKey": "xxx",
    "nestShipperApiKey": "xxx"
  },
  "gangsheetSettings": {
    "width": 22,
    "height": 60,
    "dpi": 300,
    "spacing": 0.25
  }
}
```

### Migration Calistirma
```bash
cat backend/src/main/resources/db/migration/V*.sql > /tmp/all_migrations.sql
scp /tmp/all_migrations.sql root@159.203.165.38:/tmp/
ssh root@159.203.165.38 "docker cp /tmp/all_migrations.sql printnest-db:/tmp/ && docker exec printnest-db psql -U printnest -d printnest -f /tmp/all_migrations.sql"
```

### Veritabani Temizleme
```bash
# Shipped order'lari sil
ssh root@159.203.165.38 "docker exec printnest-db psql -U printnest -d printnest -c \"DELETE FROM order_products WHERE order_id IN (SELECT id FROM orders WHERE order_status = 20); DELETE FROM orders WHERE order_status = 20;\""

# Tum order'lari sil
ssh root@159.203.165.38 "docker exec printnest-db psql -U printnest -d printnest -c \"DELETE FROM order_products; DELETE FROM orders;\""
```

---

## Cozulen Sorunlar

### 1. Settings Double-Encoding
**Sorun:** Tenant settings JSONB'de cift escape (`\"` yerine `\\\"`) ile kaydediliyordu
**Cozum:** `Tables.kt`'de `jsonb<String>` yerine `text()` kullanildi
```kotlin
// Onceki (hatali)
val settings = jsonb<String>("settings", jsonSerializer).default("{}")
// Sonraki (dogru)
val settings = text("settings").default("{}")
```

### 2. Order Serialization Hatasi
**Sorun:** "Serializer for class 'Any' is not found" hatasi
**Cozum:** `ShipStationService.kt`'de `mapOf` yerine `buildJsonObject` kullanildi

### 3. Store Filter Calismiyordu
**Sorun:** ShipStation siparisleri `shipstationStoreId` ile kaydediliyor ama filtre `storeId` kolonunu kontrol ediyordu
**Cozum:** `OrderRepository.kt`'de her iki kolon da kontrol ediliyor:
```kotlin
filters.storeId?.let { storeId ->
    query = query.andWhere {
        (Orders.storeId eq storeId) or (Orders.shipstationStoreId eq storeId)
    }
}
```

### 4. Orders Sayfasi Crash
**Sorun:** "Cannot read properties of undefined (reading 'orders')" hatasi
**Cozum:** `useOrders.ts`'de response handling duzeltildi - `api.get` zaten `res.data` doner

### 5. CORS Hatasi (403)
**Sorun:** Frontend backend'e istek atamiyor
**Cozum:** `CORS.kt`'de SERVER_IP ve nip.io eklendi

### 6. Tenant Detection After Login
**Sorun:** Login sonrasi tenant store'dan okunmuyor
**Cozum:** `useTenant.ts`'de hasTenantInStore kontrolu eklendi

---

## Frontend Yapisi

### Sidebar Navigasyonu
```
Overview
├── Dashboard
├── Analytics (sadece producer)

Orders
└── Orders (expandable)
    ├── New Orders
    └── Order List

Print on Demand (sadece producer)
├── Design Studio
├── Gangsheet
├── Mapping
├── Catalog
└── Fulfillment

Settings (sadece producer)
└── Settings
```

### Onemli Dosyalar
```
frontend/src/
├── pages/
│   ├── Orders.tsx           # Order sayfasi (New Orders + Order List)
│   ├── Dashboard.tsx        # Ana dashboard
│   └── Settings.tsx         # Ayarlar sayfasi
├── components/
│   └── layout/
│       └── Sidebar.tsx      # Sidebar navigasyonu
├── hooks/
│   └── useOrders.ts         # Order hook'u
└── stores/
    └── authStore.ts         # Auth state (isSubdealer, assignedStoreIds)
```

---

## Ortam Degiskenleri

### Backend (.env on server)
```env
KTOR_ENV=production
DATABASE_URL=jdbc:postgresql://postgres:5432/printnest
DATABASE_USER=printnest
DATABASE_PASSWORD=<generated>
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<generated>
JWT_SECRET=<generated>
JWT_ISSUER=printnest
JWT_AUDIENCE=printnest-users
SESSION_SECRET=<generated>
SERVER_IP=159.203.165.38
```

### Frontend (docker-compose build args)
```env
VITE_API_URL=http://159.203.165.38.nip.io:8080/api/v1
VITE_WS_URL=ws://159.203.165.38.nip.io:8080
VITE_APP_NAME=PrintNest
VITE_APP_ENV=production
```

---

## Local Development

### Backend Calistirma
```bash
cd backend
./gradlew run
# http://localhost:8080
```

### Frontend Calistirma
```bash
cd frontend
npm install
npm run dev
# http://localhost:3000
```

---

## Referans Proje

**TeeDropV2** - Python/Flask ile yazilmis orjinal proje.
```
/Users/una/teedropV2/
```

### Onemli Referans Dosyalari
| Dosya | Icerigi |
|-------|---------|
| `db_pooling.py` | DB operations pattern |
| `models/enums.py` | Status codes, enums |
| `blueprints/admin/order_helpers.py` | Price calculation logic |
| `blueprints/admin/gangsheet.py` | Gangsheet generation |

---

## Notlar

- Her tabloda `tenant_id` ile row-level security
- JSONB kolonlari: `orders.order_info`, `orders.order_detail`, `tenants.settings`
- Redis ile token caching
- nip.io: Domain olmadan IP ile calismak icin kullaniliyor
- Backend build suresi: ~5-7 dakika (4GB RAM ile)
- Frontend build suresi: ~40 saniye
- ShipStation sync sadece "awaiting_shipment" siparisleri cekilir
