# PrintNest - Multi-Tenant Print-on-Demand Platform

A complete SaaS platform for print-on-demand businesses with ShipStation integration, gangsheet generation, and AWS S3 storage.

## Tech Stack

### Backend
- **Kotlin** + **Ktor 3.0** - Modern async web framework
- **Exposed ORM** - Type-safe SQL
- **PostgreSQL** - Primary database
- **Redis** - Caching & session storage
- **Koin** - Dependency injection

### Frontend
- **React 18** + **TypeScript** - UI framework
- **Vite** - Build tool
- **Tailwind CSS** + **shadcn/ui** - Styling
- **TanStack Query** - Server state
- **Zustand** - Client state

## Project Structure

```
printnest/
├── backend/                    # Kotlin + Ktor API
│   ├── src/main/kotlin/com/printnest/
│   │   ├── config/            # Configuration
│   │   ├── domain/            # Models, repos, services
│   │   ├── plugins/           # Ktor plugins
│   │   ├── routes/            # API routes
│   │   └── integrations/      # External services
│   └── src/main/resources/
│       └── db/migration/      # Flyway migrations
│
├── frontend/                   # React + TypeScript
│   └── src/
│       ├── components/        # UI components
│       ├── api/hooks/         # React Query hooks
│       ├── stores/            # Zustand stores
│       └── pages/             # Page components
│
├── nginx/                      # Nginx configuration
├── scripts/                    # Deployment scripts
├── .github/workflows/          # CI/CD pipelines
├── docker-compose.yml          # Development setup
└── docker-compose.prod.yml     # Production setup
```

## Features

### Multi-Tenant Architecture
- Row-level tenant isolation
- Subdomain routing (tenant.printnest.com)
- Self-service tenant registration with onboarding wizard

### Order Management
- ShipStation integration for order sync
- Multi-step order workflow (Step 1-4)
- Batch operations
- Price calculation with modifications

### Gangsheet Generation
- Automatic gangsheet building
- AWS S3 storage for designs
- Row-based packing algorithm
- QR code and order info overlay

### Integrations
- **ShipStation** - Order sync and fulfillment
- **Stripe** - Payments and subscriptions
- **AWS S3** - File storage
- **EasyPost/NestShipper** - Shipping labels

## Quick Start

### Using Docker (Recommended)

```bash
# Clone the repository
git clone https://github.com/your-org/printnest.git
cd printnest

# Copy environment file
cp .env.example .env
# Edit .env with your credentials

# Start development environment (databases only)
make dev

# In separate terminals:
# Backend
cd backend && ./gradlew run

# Frontend
cd frontend && npm install && npm run dev
```

### Full Docker Setup

```bash
# Start all services (backend, frontend, databases)
make dev-full

# Or using docker compose directly
docker compose up -d

# Access:
# - Frontend: http://localhost:3000
# - Backend API: http://localhost:8080
# - Adminer (DB UI): http://localhost:8081
```

### Manual Setup

#### Prerequisites
- Java 17+ (for backend)
- Node.js 20+ (for frontend)
- PostgreSQL 16+
- Redis 7+

#### Backend Setup

```bash
cd backend

# Build
./gradlew build

# Run
./gradlew run
```

#### Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev
```

## Environment Variables

See `.env.example` for all available variables.

### Required Variables

```env
# Database
DB_HOST=localhost
DB_PORT=5433
DB_NAME=printnest
DB_USER=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6380

# JWT (generate a secure random string)
JWT_SECRET=your-super-secret-jwt-key-change-in-production

# AWS S3
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-1
AWS_S3_BUCKET=printnest-uploads

# Stripe
STRIPE_SECRET_KEY=sk_test_xxx
```

## Makefile Commands

```bash
make help           # Show all commands

# Development
make dev            # Start databases only
make dev-full       # Start all services

# Build
make build          # Build all Docker images
make build-backend  # Build backend only
make build-frontend # Build frontend only

# Testing
make test           # Run all tests
make test-backend   # Run backend tests
make test-frontend  # Run frontend linting/type check

# Database
make db-shell       # Open PostgreSQL shell
make db-reset       # Reset database

# Deployment
make deploy-staging # Deploy to staging
make deploy-prod    # Deploy to production
```

## Deployment

### CI/CD Pipeline

The project includes GitHub Actions workflows:

1. **CI Pipeline** (`.github/workflows/ci.yml`)
   - Runs on push/PR to main/develop
   - Backend tests with PostgreSQL and Redis
   - Frontend linting and type checking
   - Docker image building and pushing

2. **Deploy Pipeline** (`.github/workflows/deploy.yml`)
   - Automatic staging deployment on main push
   - Manual production deployment with approval

### Production Deployment

1. **Setup server:**
   ```bash
   # On your server
   mkdir -p /opt/printnest /opt/backups
   cd /opt/printnest

   # Copy docker-compose.prod.yml and .env
   ```

2. **Configure SSL:**
   ```bash
   # Initialize SSL with Certbot
   docker compose -f docker-compose.prod.yml --profile ssl up certbot
   ```

3. **Deploy:**
   ```bash
   # Using the script
   ./scripts/deploy.sh production

   # Or manually
   docker compose -f docker-compose.prod.yml up -d
   ```

### Backup & Restore

```bash
# Create backup
./scripts/backup.sh

# Restore from backup
./scripts/restore.sh /opt/backups/printnest_20240315_120000.sql.gz
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/refresh` - Refresh token
- `POST /api/v1/auth/onboarding/complete` - Complete onboarding

### Orders
- `GET /api/v1/orders` - List orders
- `POST /api/v1/orders` - Create order
- `GET /api/v1/orders/{id}` - Get order details
- `PUT /api/v1/orders/{id}/step2` - Update order items
- `GET /api/v1/orders/{id}/step3` - Calculate price
- `POST /api/v1/orders/{id}/step3/pay` - Process payment

### Products & Categories
- `GET /api/v1/categories` - List categories
- `GET /api/v1/products` - List products
- `POST /api/v1/products` - Create product

### Gangsheets
- `POST /api/v1/gangsheets` - Create gangsheet
- `GET /api/v1/gangsheets/{id}` - Get gangsheet status
- `GET /api/v1/gangsheets/{id}/download` - Download gangsheet

### Settings
- `GET /api/v1/settings` - Get tenant settings
- `PUT /api/v1/settings` - Update settings

## URLs

| URL | Purpose |
|-----|---------|
| `http://localhost:3000` | Frontend (dev) |
| `http://localhost:8080` | Backend API (dev) |
| `http://localhost:8081` | Adminer (DB UI) |
| `admin.printnest.com` | Super Admin Panel |
| `api.printnest.com` | Production API |
| `{tenant}.printnest.com` | Tenant Dashboard |

## Health Checks

```bash
# Check backend health
curl http://localhost:8080/health

# Response:
# {"status":"healthy","version":"1.0.0","service":"printnest-backend"}
```

## License

Proprietary - All rights reserved
