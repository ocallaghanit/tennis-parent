# MongoDB Role-Based Access Control (RBAC) Setup

This document describes the security configuration for MongoDB in the Tennis adapter ecosystem.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              MongoDB: tennis                             │
│                                                                          │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────┐ │
│  │  ADAPTER COLLECTIONS        │    │  PREDICTION COLLECTIONS          │ │
│  │  (owned by simple-adapter)  │    │  (owned by prediction-service)   │ │
│  │                             │    │                                   │ │
│  │  • fixtures                 │◀───│  • predictions                    │ │
│  │  • players                  │read│  • prediction_results             │ │
│  │  • tournaments              │    │  • model_configs                  │ │
│  │  • h2h                      │    │  • backtest_runs                  │ │
│  │  • odds                     │    │                                   │ │
│  │  • events                   │    │                                   │ │
│  └─────────────────────────────┘    └─────────────────────────────────┘ │
│         ▲                                      ▲                         │
│         │ read/write                           │ read/write              │
│         │                                      │                         │
│  ┌──────┴──────┐                        ┌──────┴──────┐                 │
│  │   Adapter   │                        │ Prediction  │                 │
│  │   Service   │                        │   Service   │                 │
│  └─────────────┘                        └─────────────┘                 │
└─────────────────────────────────────────────────────────────────────────┘
```

## Users & Roles

### 1. `adapter_service` (adapterRole)

**Purpose**: Used by the `api-tennis-simple-adapter` service for data ingestion.

| Collection | Permission |
|------------|------------|
| fixtures | Read/Write |
| players | Read/Write |
| tournaments | Read/Write |
| h2h | Read/Write |
| odds | Read/Write |
| events | Read/Write |
| predictions | Read Only |
| prediction_results | Read Only |
| model_configs | Read Only |
| backtest_runs | Read Only |

### 2. `prediction_service` (predictionRole)

**Purpose**: Used by the prediction service for running predictions and storing results.

| Collection | Permission |
|------------|------------|
| fixtures | Read Only |
| players | Read Only |
| tournaments | Read Only |
| h2h | Read Only |
| odds | Read Only |
| events | Read Only |
| predictions | Read/Write |
| prediction_results | Read/Write |
| model_configs | Read/Write |
| backtest_runs | Read/Write |

## Connection Strings

### Adapter Service
```
mongodb://adapter_service:adapter_secure_password_2026@mongo:27017/tennis?authSource=tennis
```

### Prediction Service
```
mongodb://prediction_service:prediction_secure_password_2026@mongo:27017/tennis?authSource=tennis
```

### Admin (for maintenance only)
```
mongodb://admin:tennis_admin_2026@mongo:27017/admin?authSource=admin
```

## Security Features

1. **Collection-Level Isolation**: Each service can only write to collections it owns
2. **Read Access Sharing**: Both services can read from all collections
3. **Authentication Required**: All connections must authenticate
4. **No Cross-Contamination**: A bug in the prediction service cannot corrupt adapter data

## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

```bash
# MongoDB Root (admin operations only)
MONGO_ROOT_USER=admin
MONGO_ROOT_PASSWORD=your_secure_admin_password

# Adapter Service
MONGO_ADAPTER_USER=adapter_service
MONGO_ADAPTER_PASSWORD=your_secure_adapter_password

# Prediction Service (for future use)
MONGO_PREDICTION_USER=prediction_service
MONGO_PREDICTION_PASSWORD=your_secure_prediction_password
```

## Testing Access Control

```bash
# Test adapter can write to fixtures
docker exec tennis-mongo mongosh -u adapter_service -p "$MONGO_ADAPTER_PASSWORD" \
  --authenticationDatabase tennis tennis \
  --eval 'db.fixtures.insertOne({test: true})'

# Test prediction CANNOT write to fixtures (should fail)
docker exec tennis-mongo mongosh -u prediction_service -p "$MONGO_PREDICTION_PASSWORD" \
  --authenticationDatabase tennis tennis \
  --eval 'db.fixtures.insertOne({test: true})'
# Expected: "not authorized on tennis to execute command"

# Test prediction CAN read fixtures
docker exec tennis-mongo mongosh -u prediction_service -p "$MONGO_PREDICTION_PASSWORD" \
  --authenticationDatabase tennis tennis \
  --eval 'db.fixtures.find().limit(1)'

# Test prediction CAN write to predictions
docker exec tennis-mongo mongosh -u prediction_service -p "$MONGO_PREDICTION_PASSWORD" \
  --authenticationDatabase tennis tennis \
  --eval 'db.predictions.insertOne({test: true})'
```

## Resetting RBAC

If you need to reset the RBAC configuration:

```bash
# 1. Stop containers
docker-compose down

# 2. Remove the data volume
docker volume rm api-tennis-simple-adapter_mongo-data

# 3. Restart (init scripts will run fresh)
docker-compose up -d
```

## Adding New Collections

To add a new collection with access control:

1. Edit `mongo-init/01-init-users.js`
2. Add the collection to the appropriate role's privileges
3. Reset RBAC (see above)

Example for adding a new prediction collection:
```javascript
// In predictionRole privileges
{ 
  resource: { db: "tennis", collection: "new_collection" }, 
  actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
}
```

