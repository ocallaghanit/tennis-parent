# Tennis Prediction API

A Spring Boot service for predicting tennis match outcomes using data from the `api-tennis-simple-adapter` service.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           MongoDB: tennis                             │
│                                                                       │
│  ┌─────────────────────────────┐    ┌─────────────────────────────┐  │
│  │  ADAPTER COLLECTIONS        │    │  PREDICTION COLLECTIONS      │  │
│  │  (read-only access)         │    │  (full read/write access)    │  │
│  │                             │    │                               │  │
│  │  • fixtures                 │    │  • predictions                │  │
│  │  • players                  │    │  • prediction_results         │  │
│  │  • tournaments              │    │  • model_configs              │  │
│  │  • h2h                      │    │  • backtest_runs              │  │
│  │  • odds                     │    │                               │  │
│  │  • events                   │    │                               │  │
│  └─────────────────────────────┘    └─────────────────────────────┘  │
│              ▲                                  ▲                     │
│              │ read-only                        │ read/write          │
│              └──────────────┬──────────────────┘                     │
│                             │                                         │
│                    ┌────────┴────────┐                               │
│                    │  prediction-api  │                               │
│                    └─────────────────┘                               │
└──────────────────────────────────────────────────────────────────────┘
```

## Features

- **Read-only access** to adapter data (fixtures, players, tournaments, H2H, odds)
- **Write access** to prediction-specific collections
- **Swagger UI** for API documentation
- **Health checks** with MongoDB connectivity status

## Prerequisites

- The `api-tennis-simple-adapter` service must be running
- MongoDB must be initialized with RBAC (users/roles already created by adapter)

## Running

### With Docker (Recommended)

1. Ensure the adapter service is running:
   ```bash
   cd ../api-tennis-simple-adapter
   docker-compose up -d
   ```

2. Start the prediction API:
   ```bash
   cd ../prediction-api
   docker-compose up --build -d
   ```

3. Access the service:
   - API: http://localhost:8083/api/health
   - Swagger: http://localhost:8083/swagger-ui.html

### Local Development

1. Ensure MongoDB is accessible at localhost:27017

2. Run with Maven:
   ```bash
   ./mvnw spring-boot:run
   ```

3. Access the service:
   - API: http://localhost:8082/api/health
   - Swagger: http://localhost:8082/swagger-ui.html

## API Endpoints

### Health & Status
- `GET /api/health` - Health check with database connectivity

### Data (Read-only)
- `GET /api/data/stats` - Data statistics
- `GET /api/data/fixtures?dateStart=&dateStop=` - Fixtures by date range
- `GET /api/data/fixtures/{eventKey}` - Single fixture
- `GET /api/data/players/{playerKey}` - Player details
- `GET /api/data/players/ranked` - Ranked players
- `GET /api/data/tournaments/{tournamentKey}` - Tournament details
- `GET /api/data/h2h/{player1}/{player2}` - Head-to-head
- `GET /api/data/odds/{matchKey}` - Match odds

### Predictions (Read/Write)
- `GET /api/predictions` - All predictions
- `GET /api/predictions/match/{matchKey}` - Predictions for a match
- `GET /api/predictions/upcoming` - Upcoming matches to predict
- `POST /api/predictions/predict/{matchKey}` - Create prediction
- `DELETE /api/predictions/{id}` - Delete prediction

## MongoDB Access Control

This service uses the `prediction_service` user with the following permissions:

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

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_URI` | `mongodb://prediction_service:...@localhost:27017/tennis?authSource=tennis` | MongoDB connection string |
| `SERVER_PORT` | `8082` | Server port |

## Development

### Project Structure

```
prediction-api/
├── src/main/java/com/tennis/prediction/
│   ├── config/           # Configuration classes
│   ├── controller/       # REST controllers
│   ├── model/
│   │   ├── readonly/     # Read-only models (adapter data)
│   │   └── *.java        # Writable prediction models
│   ├── repository/
│   │   ├── readonly/     # Read-only repositories
│   │   └── *.java        # Writable repositories
│   └── service/          # Business logic (TODO)
├── src/main/resources/
│   └── application.yml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

### TODO

- [ ] Implement actual prediction algorithms
- [ ] Add backtesting service
- [ ] Add model evaluation metrics
- [ ] Add scheduled prediction jobs
- [ ] Add UI dashboard

