.PHONY: help dev build up down logs clean test deploy-staging deploy-prod

# Colors
GREEN  := $(shell tput -Txterm setaf 2)
YELLOW := $(shell tput -Txterm setaf 3)
RESET  := $(shell tput -Txterm sgr0)

## Help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make ${YELLOW}<target>${RESET}\n\nTargets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  ${GREEN}%-15s${RESET} %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

## Development
dev: ## Start development environment (databases only)
	docker compose up -d postgres redis adminer
	@echo "${GREEN}Development databases started!${RESET}"
	@echo "PostgreSQL: localhost:5433"
	@echo "Redis: localhost:6380"
	@echo "Adminer: http://localhost:8081"

dev-full: ## Start full development environment (all services)
	docker compose up -d
	@echo "${GREEN}Full development environment started!${RESET}"
	@echo "Frontend: http://localhost:3000"
	@echo "Backend: http://localhost:8080"
	@echo "Adminer: http://localhost:8081"

## Build
build: ## Build all Docker images
	docker compose build

build-backend: ## Build backend Docker image
	docker compose build backend

build-frontend: ## Build frontend Docker image
	docker compose build frontend

## Docker Compose
up: ## Start all services
	docker compose up -d

down: ## Stop all services
	docker compose down

restart: ## Restart all services
	docker compose down
	docker compose up -d

logs: ## Show logs (all services)
	docker compose logs -f

logs-backend: ## Show backend logs
	docker compose logs -f backend

logs-frontend: ## Show frontend logs
	docker compose logs -f frontend

## Cleanup
clean: ## Stop services and remove volumes
	docker compose down -v
	docker system prune -f

clean-all: ## Remove everything including images
	docker compose down -v --rmi all
	docker system prune -af

## Testing
test: test-backend test-frontend ## Run all tests

test-backend: ## Run backend tests
	cd backend && ./gradlew test

test-frontend: ## Run frontend tests and lint
	cd frontend && npm run lint && npx tsc --noEmit

## Backend specific
backend-build: ## Build backend JAR
	cd backend && ./gradlew buildFatJar

backend-run: ## Run backend locally
	cd backend && ./gradlew run

## Frontend specific
frontend-install: ## Install frontend dependencies
	cd frontend && npm ci

frontend-build: ## Build frontend
	cd frontend && npm run build

frontend-dev: ## Run frontend dev server
	cd frontend && npm run dev

## Database
db-migrate: ## Run database migrations
	cd backend && ./gradlew flywayMigrate

db-reset: ## Reset database (drop and recreate)
	docker compose exec postgres psql -U postgres -c "DROP DATABASE IF EXISTS printnest;"
	docker compose exec postgres psql -U postgres -c "CREATE DATABASE printnest;"
	@echo "${GREEN}Database reset!${RESET}"

db-shell: ## Open PostgreSQL shell
	docker compose exec postgres psql -U postgres -d printnest

redis-shell: ## Open Redis shell
	docker compose exec redis redis-cli

## Production
deploy-staging: ## Deploy to staging
	@echo "${YELLOW}Deploying to staging...${RESET}"
	./scripts/deploy.sh staging

deploy-prod: ## Deploy to production
	@echo "${YELLOW}Deploying to production...${RESET}"
	./scripts/deploy.sh production

## Health checks
health: ## Check service health
	@echo "Backend health:"
	@curl -s http://localhost:8080/health | jq . || echo "Backend not running"
	@echo "\nFrontend health:"
	@curl -s http://localhost:3000/health || echo "Frontend not running"

## Docker
docker-login: ## Login to GitHub Container Registry
	echo $$GITHUB_TOKEN | docker login ghcr.io -u $$GITHUB_USER --password-stdin

docker-push: build ## Build and push images to registry
	docker push ghcr.io/$(GITHUB_USER)/printnest-backend:latest
	docker push ghcr.io/$(GITHUB_USER)/printnest-frontend:latest
