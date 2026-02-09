#!/bin/bash
set -e

# Configuration
BACKUP_DIR="/opt/backups"
DB_CONTAINER="printnest-postgres"
DB_USER=${DB_USER:-"postgres"}
DB_NAME=${DB_NAME:-"printnest"}
RETENTION_DAYS=30

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Create backup directory if not exists
mkdir -p ${BACKUP_DIR}

# Generate backup filename
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/printnest_${TIMESTAMP}.sql.gz"

echo -e "${YELLOW}Creating database backup...${NC}"

# Create backup
docker exec ${DB_CONTAINER} pg_dump -U ${DB_USER} ${DB_NAME} | gzip > ${BACKUP_FILE}

# Check if backup was created successfully
if [[ -f ${BACKUP_FILE} ]]; then
    SIZE=$(du -h ${BACKUP_FILE} | cut -f1)
    echo -e "${GREEN}Backup created successfully!${NC}"
    echo "File: ${BACKUP_FILE}"
    echo "Size: ${SIZE}"
else
    echo "Error: Backup failed!"
    exit 1
fi

# Clean up old backups
echo -e "${YELLOW}Cleaning up backups older than ${RETENTION_DAYS} days...${NC}"
find ${BACKUP_DIR} -name "printnest_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

# List remaining backups
echo -e "\nCurrent backups:"
ls -lh ${BACKUP_DIR}/printnest_*.sql.gz 2>/dev/null || echo "No backups found"
