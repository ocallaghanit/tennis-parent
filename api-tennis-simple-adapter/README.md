# API Tennis Simple Adapter

A simple Spring Boot service that ingests data from the API Tennis API and stores it in MongoDB for later retrieval.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  api-tennis-simple-adapter                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │ API Tennis  │───▶│  Ingestion  │───▶│  MongoDB    │ │
│  │   Client    │    │   Service   │    │  (Docker)   │ │
│  └─────────────┘    └─────────────┘    └─────────────┘ │
│                            │                    ▲       │
│                            ▼                    │       │
│                     ┌─────────────┐             │       │
│                     │  REST API   │─────────────┘       │
│                     │  (Query)    │                     │
│                     └─────────────┘                     │
└─────────────────────────────────────────────────────────┘
```

## Quick Start

### 1. Set your API key

Create a `.env` file in the project root:

```bash
TENNIS_API_KEY=your_api_key_here
```

Or export it:

```bash
export TENNIS_API_KEY=your_api_key_here
```

### 2. Start with Docker Compose

```bash
docker-compose up -d
```

This starts:
- **App** on `http://localhost:8081`
- **MongoDB** on `localhost:27017`
- **Mongo Express** (web UI) on `http://localhost:8082`

### 3. Test the connection

```bash
curl http://localhost:8081/api/test
```

## API Endpoints

### Test & Health

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/test` | GET | Test API Tennis connection |
| `/actuator/health` | GET | Health check |

### Ingestion (POST)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ingest/events` | POST | Ingest all event types |
| `/api/ingest/tournaments?eventTypeKey=265` | POST | Ingest tournaments |
| `/api/ingest/fixtures?dateStart=2024-01-01&dateStop=2024-01-31` | POST | Ingest fixtures by date |
| `/api/ingest/fixtures/tournament/{tournamentKey}` | POST | Ingest fixtures by tournament |
| `/api/ingest/players/{playerKey}` | POST | Ingest a single player |
| `/api/ingest/odds?dateStart=2024-01-01&dateStop=2024-01-31` | POST | Ingest odds by date |
| `/api/ingest/catalog` | POST | Full catalog refresh (events + ATP/WTA tournaments) |

### Query (GET)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/data/stats` | GET | Get document counts |
| `/api/data/events` | GET | List all events |
| `/api/data/tournaments?eventTypeKey=265` | GET | List tournaments |
| `/api/data/fixtures?dateStart=...&dateStop=...` | GET | List fixtures |
| `/api/data/fixtures/player/{playerKey}` | GET | Get player's matches |
| `/api/data/players` | GET | List all players |
| `/api/data/odds` | GET | List all odds |

## MongoDB Collections

| Collection | Key Field | Description |
|------------|-----------|-------------|
| `events` | `eventKey` | Event types (ATP Singles, WTA Singles, etc.) |
| `tournaments` | `tournamentKey` | Tournament info (Wimbledon, US Open, etc.) |
| `fixtures` | `eventKey` | Match data |
| `players` | `playerKey` | Player profiles |
| `odds` | `matchKey` | Betting odds |

All documents store the **raw API response** in a `raw` field (BSON Document), plus extracted key fields for indexing.

## Example Usage

### Ingest a full catalog and recent fixtures

```bash
# 1. Ingest events and tournaments
curl -X POST http://localhost:8081/api/ingest/catalog

# 2. Ingest last 7 days of fixtures
curl -X POST "http://localhost:8081/api/ingest/fixtures?dateStart=$(date -v-7d +%Y-%m-%d)&dateStop=$(date +%Y-%m-%d)"

# 3. Check stats
curl http://localhost:8081/api/data/stats
```

### Query fixtures for a tournament

```bash
curl "http://localhost:8081/api/data/fixtures?tournamentKey=2553"
```

## Development

### Run locally (without Docker)

1. Start MongoDB:
   ```bash
   docker run -d -p 27017:27017 --name tennis-mongo mongo:7
   ```

2. Run the app:
   ```bash
   export TENNIS_API_KEY=your_key
   ./mvnw spring-boot:run
   ```

### Build JAR

```bash
./mvnw clean package -DskipTests
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TENNIS_API_KEY` | (required) | Your API Tennis API key |
| `TENNIS_API_BASE_URL` | `https://api.api-tennis.com` | API base URL |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/tennis` | MongoDB connection string |

