#!/bin/bash
set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${1:-staging}
REGISTRY="ghcr.io"
IMAGE_PREFIX="printnest"

echo -e "${YELLOW}Deploying PrintNest to ${ENVIRONMENT}...${NC}"

# Validate environment
if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "production" ]]; then
    echo -e "${RED}Error: Invalid environment. Use 'staging' or 'production'${NC}"
    exit 1
fi

# Load environment-specific config
if [[ "$ENVIRONMENT" == "staging" ]]; then
    HOST=${STAGING_HOST:-"staging.printnest.com"}
    USER=${STAGING_USER:-"deploy"}
    SSH_KEY=${STAGING_SSH_KEY:-"~/.ssh/staging_key"}
elif [[ "$ENVIRONMENT" == "production" ]]; then
    HOST=${PRODUCTION_HOST:-"printnest.com"}
    USER=${PRODUCTION_USER:-"deploy"}
    SSH_KEY=${PRODUCTION_SSH_KEY:-"~/.ssh/production_key"}

    # Extra confirmation for production
    echo -e "${RED}WARNING: You are about to deploy to PRODUCTION!${NC}"
    read -p "Type 'yes' to continue: " confirm
    if [[ "$confirm" != "yes" ]]; then
        echo "Deployment cancelled."
        exit 0
    fi
fi

# Get version from git
VERSION=$(git rev-parse --short HEAD)
echo -e "${GREEN}Deploying version: ${VERSION}${NC}"

# Build and push images
echo -e "${YELLOW}Building Docker images...${NC}"
docker compose build

echo -e "${YELLOW}Pushing images to registry...${NC}"
docker tag ${IMAGE_PREFIX}-backend:latest ${REGISTRY}/${IMAGE_PREFIX}/backend:${VERSION}
docker tag ${IMAGE_PREFIX}-backend:latest ${REGISTRY}/${IMAGE_PREFIX}/backend:latest
docker tag ${IMAGE_PREFIX}-frontend:latest ${REGISTRY}/${IMAGE_PREFIX}/frontend:${VERSION}
docker tag ${IMAGE_PREFIX}-frontend:latest ${REGISTRY}/${IMAGE_PREFIX}/frontend:latest

docker push ${REGISTRY}/${IMAGE_PREFIX}/backend:${VERSION}
docker push ${REGISTRY}/${IMAGE_PREFIX}/backend:latest
docker push ${REGISTRY}/${IMAGE_PREFIX}/frontend:${VERSION}
docker push ${REGISTRY}/${IMAGE_PREFIX}/frontend:latest

# Deploy to server
echo -e "${YELLOW}Deploying to ${HOST}...${NC}"
ssh -i ${SSH_KEY} ${USER}@${HOST} << EOF
    cd /opt/printnest

    # Pull latest images
    docker pull ${REGISTRY}/${IMAGE_PREFIX}/backend:${VERSION}
    docker pull ${REGISTRY}/${IMAGE_PREFIX}/frontend:${VERSION}

    # Backup current state
    docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U \${DB_USER} \${DB_NAME} > /opt/backups/db_\$(date +%Y%m%d_%H%M%S).sql || true

    # Update environment with new version
    sed -i "s/VERSION=.*/VERSION=${VERSION}/" .env

    # Deploy
    docker compose -f docker-compose.prod.yml up -d --remove-orphans

    # Health check
    sleep 30
    if curl -sf http://localhost:8080/health > /dev/null; then
        echo "Health check passed!"
    else
        echo "Health check failed! Rolling back..."
        # Rollback logic here if needed
        exit 1
    fi

    # Clean up old images
    docker image prune -f
EOF

echo -e "${GREEN}Deployment to ${ENVIRONMENT} completed successfully!${NC}"
echo -e "Version: ${VERSION}"
echo -e "Server: ${HOST}"
