// MongoDB Initialization Script
// Creates roles and users for adapter and prediction services
// Each service owns its own collections and can only read from the other's

// Switch to the tennis database
db = db.getSiblingDB('tennis');

print('🔧 Setting up MongoDB RBAC for Tennis services...');

// ============================================================
// ADAPTER ROLE
// The simple-adapter service owns data ingestion collections
// ============================================================
db.createRole({
  role: "adapterRole",
  privileges: [
    // WRITE access to adapter-owned collections
    { 
      resource: { db: "tennis", collection: "fixtures" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "players" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "tournaments" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "h2h" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "odds" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "events" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    // READ-ONLY access to prediction collections (for debugging/monitoring)
    { 
      resource: { db: "tennis", collection: "predictions" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "prediction_results" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "model_configs" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "backtest_runs" }, 
      actions: ["find", "collStats"] 
    }
  ],
  roles: []
});
print('✅ Created adapterRole');

// ============================================================
// PREDICTION ROLE
// The prediction service owns prediction/analysis collections
// Can read adapter data but not modify it
// ============================================================
db.createRole({
  role: "predictionRole",
  privileges: [
    // READ-ONLY access to adapter collections
    { 
      resource: { db: "tennis", collection: "fixtures" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "players" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "tournaments" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "h2h" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "odds" }, 
      actions: ["find", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "events" }, 
      actions: ["find", "collStats"] 
    },
    // WRITE access to prediction-owned collections
    { 
      resource: { db: "tennis", collection: "predictions" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "prediction_results" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "model_configs" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "backtest_runs" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    },
    { 
      resource: { db: "tennis", collection: "owl_ratings" }, 
      actions: ["find", "insert", "update", "remove", "createIndex", "collStats"] 
    }
  ],
  roles: []
});
print('✅ Created predictionRole');

// ============================================================
// CREATE SERVICE USERS
// ============================================================

// Adapter service user
db.createUser({
  user: "adapter_service",
  pwd: "adapter_secure_password_2026",
  roles: [{ role: "adapterRole", db: "tennis" }]
});
print('✅ Created adapter_service user');

// Prediction service user
db.createUser({
  user: "prediction_service",
  pwd: "prediction_secure_password_2026",
  roles: [{ role: "predictionRole", db: "tennis" }]
});
print('✅ Created prediction_service user');

// ============================================================
// SUMMARY
// ============================================================
print('');
print('╔════════════════════════════════════════════════════════════════════╗');
print('║           MongoDB RBAC Setup Complete                              ║');
print('╠════════════════════════════════════════════════════════════════════╣');
print('║ adapter_service:                                                   ║');
print('║   ✓ Read/Write: fixtures, players, tournaments, h2h, odds, events  ║');
print('║   ✓ Read-Only:  predictions, prediction_results, model_configs,    ║');
print('║                 backtest_runs                                      ║');
print('╠════════════════════════════════════════════════════════════════════╣');
print('║ prediction_service:                                                ║');
print('║   ✓ Read-Only:  fixtures, players, tournaments, h2h, odds, events  ║');
print('║   ✓ Read/Write: predictions, prediction_results, model_configs,    ║');
print('║                 backtest_runs                                      ║');
print('╚════════════════════════════════════════════════════════════════════╝');
print('');

