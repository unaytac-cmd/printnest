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

## Multi-Tenant Mimari

### URL Yapisi
| URL | Amac |
|-----|------|
| `admin.printnest.com` | Super Admin Panel |
| `{tenant}.printnest.com` | Tenant Dashboard |
| `api.printnest.com` | API Gateway |

### Kullanici Rolleri
- **Super Admin** - Platform yonetimi, tum tenant'lara erisim
- **Tenant Owner** - Tenant ayarlari, kullanici yonetimi
- **Tenant Admin** - Siparis, urun, tasarim yonetimi
- **Employee** - Sinirli erisim

---

## Siparis Akisi

**ONEMLI: Siparisler sadece ShipStation'dan cekilecek!**

Marketplace entegrasyonlari (Amazon, Shopify, TikTok, Walmart, Etsy) kaldirildi. Tum siparisler ShipStation API uzerinden alinacak.

```
ShipStation API → PrintNest → Gangsheet → Baski → Kargo
```

---

## Kullanici Kayit Akisi

### 1. Landing Page (`/welcome`)
Basit kayit formu:
- Full Name
- Email
- Password
- Confirm Password

### 2. Onboarding Wizard (`/onboarding`)
7 adimli kurulum:

1. **Welcome** - Hos geldiniz
2. **Store Setup** - Isletme adi, subdomain, custom domain
3. **ShipStation** - API Key, API Secret
4. **Payment** - Stripe Public Key, Secret Key
5. **Shipping** - NestShipper API Key, EasyPost API Key
6. **Gangsheet Settings**:
   - Width (inch)
   - Height (inch)
   - DPI (varsayilan: 300)
   - Spacing (inch)
   - Background Color
   - Auto Arrange toggle
7. **Complete** - Kurulum tamamlandi

---

## Super Admin

### Dashboard (`/superadmin`)
- Total Tenants
- Total Users
- Monthly Revenue
- Total Orders
- Plan Distribution (Starter, Professional, Enterprise)
- Recent Tenants listesi

### Tenant Yonetimi (`/superadmin/tenants`)
- Tenant arama ve filtreleme
- Status: Active, Trial, Suspended
- Plan: Starter, Professional, Enterprise
- Aksiyonlar: Impersonate, Suspend, Delete

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

## Development

### Frontend Calistirma
```bash
cd frontend
npm run dev
# http://localhost:3000
```

### Localhost Development
Localhost'ta calisirken tenant kontrolu bypass edilir ve mock DEV_TENANT kullanilir.

### Onemli Sayfalar
- `/welcome` - Landing page (kayit)
- `/onboarding` - Kurulum wizard
- `/login` - Giris
- `/dashboard` - Ana panel
- `/superadmin` - Super admin paneli

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

---

## Referans Proje (Orjinal Kod)

**TeeDropV2** - Python/Flask ile yazilmis orjinal proje. Yeni kodu yazarken buradan referans alinacak.

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
- JSONB kolonlari mevcut (order_info, price_detail)
- Redis ile token caching
- S3 ile dosya depolama
