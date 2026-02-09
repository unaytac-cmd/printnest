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

# Tek dosya guncelleme
scp /Users/una/printnest/frontend/src/pages/Dashboard.tsx root@159.203.165.38:/opt/printnest/frontend/src/pages/
ssh root@159.203.165.38 "cd /opt/printnest && docker compose -f docker-compose.simple.yml build frontend && docker compose -f docker-compose.simple.yml up -d frontend --force-recreate"

# Backend rebuild (~5-7 dk surer)
ssh root@159.203.165.38 "cd /opt/printnest && docker compose -f docker-compose.simple.yml build backend --no-cache && docker compose -f docker-compose.simple.yml up -d backend --force-recreate"
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
| **Sub-dealer** | Sadece kendi siparisleri |
| **Employee** | Sinirli erisim |

### URL Yapisi (Domain alindiktan sonra)
| URL | Amac |
|-----|------|
| `admin.printnest.com` | Super Admin Panel |
| `{tenant}.printnest.com` | Tenant Dashboard |
| `api.printnest.com` | API Gateway |

---

## Siparis Akisi

**ONEMLI: Siparisler sadece ShipStation'dan cekilecek!**

```
ShipStation API → PrintNest → Gangsheet → Baski → Kargo
```

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

---

## Kullanici Kayit Akisi

### 1. Register (`/register`)
2 adimli kayit formu:
- Step 1: Ad, Soyad, Email, Sifre
- Step 2: Store Name, Store Slug

### 2. Onboarding Wizard (`/onboarding`)
7 adimli kurulum:
1. **Welcome** - Hos geldiniz
2. **Store Setup** - Isletme adi, subdomain, custom domain
3. **ShipStation** - API Key, API Secret
4. **Payment** - Stripe Public Key, Secret Key
5. **Shipping** - NestShipper API Key, EasyPost API Key
6. **Gangsheet Settings** - Width, Height, DPI, Spacing
7. **Complete** - Kurulum tamamlandi

---

## Veritabani

### Onemli Tablolar
| Tablo | Aciklama |
|-------|----------|
| `tenants` | Isletmeler |
| `users` | Kullanicilar |
| `orders` | Siparisler |
| `order_products` | Siparis urunleri |
| `products` | Urunler |
| `variants` | Urun varyantlari |
| `gangsheets` | Gangsheet'ler |
| `designs` | Tasarimlar |
| `map_values` | Variant eslestirmeleri |
| `map_listings` | Tasarim eslestirmeleri |

### Migration Dosyalari
Konum: `backend/src/main/resources/db/migration/`
- V1__initial_schema.sql
- V2__producer_subdealer.sql
- V3__products_categories_mapping.sql
- V4__shipping_price_profiles.sql
- V5__order_processing.sql
- V6__settings_features.sql
- V7__ticket_system.sql
- V8__scheduler_tables.sql
- V9__email_system.sql
- V10__monitor_tables.sql
- V11__digitizing_tables.sql
- V12__batch_tables.sql
- V13__variant_flags.sql

### Migration Calistirma (Manuel)
```bash
# Tum migration'lari birlestir
cat backend/src/main/resources/db/migration/V*.sql > /tmp/all_migrations.sql

# Sunucuya kopyala ve calistir
scp /tmp/all_migrations.sql root@159.203.165.38:/tmp/
ssh root@159.203.165.38 "docker cp /tmp/all_migrations.sql printnest-db:/tmp/ && docker exec printnest-db psql -U printnest -d printnest -f /tmp/all_migrations.sql"
```

---

## Cozulen Sorunlar

### 1. CORS Hatasi (403)
**Sorun:** Frontend backend'e istek atamiyor
**Cozum:** `backend/src/main/kotlin/com/printnest/plugins/CORS.kt`'de SERVER_IP ve nip.io eklendi
```kotlin
System.getenv("SERVER_IP")?.let { ip ->
    allowHost(ip, schemes = listOf("http", "https"))
    allowHost("$ip.nip.io", schemes = listOf("http", "https"))
    allowHost("*.$ip.nip.io", schemes = listOf("http", "https"))
}
```

### 2. Store Not Found
**Sorun:** IP adresiyle erisimde tenant bulunamiyor
**Cozum:** `frontend/src/hooks/useTenant.ts`'de IP adresi development mode olarak tanimlandi
```typescript
const isIPAddress = /^(\d{1,3}\.){3}\d{1,3}(\.nip\.io)?$/.test(window.location.hostname);
const isDevelopment = hostname === 'localhost' || hostname === '127.0.0.1' || isIPAddress;
```

### 3. Veritabani Tablolari Yok
**Sorun:** Migration'lar otomatik calismiyor
**Cozum:** Migration'lar manuel olarak calistirildi (yukaridaki komutlarla)

### 4. Backend Build OOM (Out of Memory)
**Sorun:** 2GB RAM'de Kotlin/Gradle build basarisiz
**Cozum:** DigitalOcean Droplet 4GB'a yukseltildi

### 5. Database Port Hatasi
**Sorun:** Backend localhost:5433'e baglanmaya calisiyor
**Cozum:** `docker-compose.simple.yml`'de DATABASE_URL duzeltildi
```yaml
environment:
  - DATABASE_URL=jdbc:postgresql://postgres:5432/printnest
```

### 6. Tenant Detection After Login
**Sorun:** Login sonrasi tenant store'dan okunmuyor, URL'den bulunmaya calisiliyor
**Cozum:** `useTenant.ts`'de hasTenantInStore kontrolu eklendi
```typescript
const hasTenantInStore = !!tenant && !!tenant.id;
// Query disabled if tenant already in store
enabled: !hasTenantInStore && (isDevelopment || !!slug),
```

---

## Bilinen Sorunlar / TODO

### Acil (Arastiriliyor)
- [ ] **White blank page sorunu**: Login/register sonrasi `/welcome` sayfasi bos gorunuyor
  - Olasiliklar: JS error, Lazy loading sorunu, GuestRoute redirect loop
  - Cozum: Browser console kontrolu gerekli
- [ ] Onboarding flow test edilmeli

### Dashboard
- [ ] Dashboard API endpoint'i (`/api/v1/dashboard`) backend'de yok
- [ ] Gercek data icin API baglantisi yapilmali

### Deployment
- [ ] Domain alinmali (su an nip.io ile calisiyor)
- [ ] SSL/HTTPS kurulmali (Let's Encrypt)
- [ ] CI/CD pipeline test edilmeli

### Entegrasyonlar
- [ ] ShipStation entegrasyonu test edilmeli
- [ ] Stripe entegrasyonu test edilmeli
- [ ] S3 bucket olusturulmali

---

## Son Yapilan Isler (Oturum Gecmisi)

### Session: 2024-XX-XX

1. **GitHub Repository Olusturuldu**
   - https://github.com/unaytac-cmd/printnest (public)
   - `gh auth refresh -h github.com -s workflow` ile workflow izni eklendi

2. **DigitalOcean Deployment**
   - 4GB RAM droplet olusturuldu (2GB yetmedi - OOM)
   - Docker Compose ile coklu servis (backend, frontend, postgres, redis)
   - nip.io ile domainsiz calistirildi

3. **CORS Duzeltmeleri**
   - IP adresi ve nip.io domain'leri CORS whitelist'e eklendi

4. **Tenant Detection Duzeltmeleri**
   - IP adresi development mode olarak tanimlandi
   - Login sonrasi tenant store'dan okuma eklendi

5. **Dashboard Guncellendi**
   - Mock data kaldirildi
   - `useDashboard` hook'u ile API baglantisi eklendi
   - Empty state "Connect ShipStation" butonuyla eklendi

6. **White Blank Page Arastirmasi (Devam Ediyor)**
   - GuestRoute redirect logic kontrolu yapildi
   - TenantWrapper basitlestirildi
   - Sorun hala devam ediyor - browser console kontrolu gerekli

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

## Gangsheet Ayarlari

Her tenant icin ozellestirilmis gangsheet konfigurasyonu:

```typescript
interface GangsheetSettings {
  width: number;        // inch (varsayilan: 22)
  height: number;       // inch (varsayilan: 60)
  dpi: number;          // varsayilan: 300
  spacing: number;      // inch (varsayilan: 0.25)
  backgroundColor: string;  // hex color
  autoArrange: boolean;     // otomatik yerlestirme
}
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

### Docker Compose (Local)
```bash
docker compose up -d
```

---

## API Entegrasyonlari

### ShipStation
- Siparis cekme
- Kargo durumu guncelleme
- Tracking bilgisi gonderme

### Stripe
- Odeme isleme
- Abonelik yonetimi
- Faturalama

### NestShipper / EasyPost
- Kargo etiketi olusturma
- Fiyat hesaplama

### AWS S3
- Tasarim dosyalari
- Gangsheet PNG'leri

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
| `walmart_integration/` | Marketplace auth patterns |

Orjinal kodu okumak icin: "teedropV2'deki X dosyasini oku" de.

---

## Notlar

- Her tabloda `tenant_id` ile row-level security
- JSONB kolonlari mevcut (order_info, price_detail, settings)
- Redis ile token caching
- S3 ile dosya depolama (henuz aktif degil)
- nip.io: Domain olmadan IP ile calismak icin kullaniliyor
- Backend build suresi: ~5-7 dakika (4GB RAM ile)
- Frontend build suresi: ~40 saniye
