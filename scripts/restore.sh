#!/bin/bash
set -e

# Configuration
BACKUP_DIR="/opt/backups"
DB_CONTAINER="printnest-postgres"
DB_USER=${DB_USER:-"postgres"}
DB_NAME=${DB_NAME:-"printnest"}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if backup file is provided
if [[ -z "$1" ]]; then
    echo -e "${YELLOW}Usage: ./restore.sh <backup_file>${NC}"
    echo -e "\nAvailable backups:"
    ls -lh ${BACKUP_DIR}/printnest_*.sql.gz 2>/dev/null || echo "No backups found"
    exit 1
fi

BACKUP_FILE=$1

# Check if backup file exists
if [[ ! -f ${BACKUP_FILE} ]]; then
    echo -e "${RED}Error: Backup file not found: ${BACKUP_FILE}${NC}"
    exit 1
fi

# Confirm restore
echo -e "${RED}WARNING: This will replace all data in the database!${NC}"
echo "Database: ${DB_NAME}"
echo "Backup file: ${BACKUP_FILE}"
read -p "Type 'yes' to continue: " confirm

if [[ "$confirm" != "yes" ]]; then
    echo "Restore cancelled."
    exit 0
fi

echo -e "${YELLOW}Stopping application services...${NC}"
docker compose stop backend frontend || true

echo -e "${YELLOW}Dropping existing database...${NC}"
docker exec ${DB_CONTAINER} psql -U ${DB_USER} -c "DROP DATABASE IF EXISTS ${DB_NAME};"
docker exec ${DB_CONTAINER} psql -U ${DB_USER} -c "CREATE DATABASE ${DB_NAME};"

echo -e "${YELLOW}Restoring database from backup...${NC}"
if [[ ${BACKUP_FILE} == *.gz ]]; then
    gunzip -c ${BACKUP_FILE} | docker exec -i ${DB_CONTAINER} psql -U ${DB_USER} -d ${DB_NAME}
else
    cat ${BACKUP_FILE} | docker exec -i ${DB_CONTAINER} psql -U ${DB_USER} -d ${DB_NAME}
fi

echo -e "${YELLOW}Starting application services...${NC}"
docker compose start backend frontend

echo -e "${GREEN}Database restored successfully!${NC}"
