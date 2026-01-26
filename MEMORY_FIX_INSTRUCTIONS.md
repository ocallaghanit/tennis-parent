# Memory Issues Fixed

## Problem
The application was running out of Java heap memory (OutOfMemoryError) during:
1. **Odds refresh** - trying to load all fixtures for an entire year into memory
2. **Startup fixture refresh** - trying to refresh 60 days of fixtures on startup

## Solutions Applied

### 1. Increased Java Heap Size
Created `.mvn/jvm.config` with:
```
-Xms1g -Xmx4g
```
This gives Java 4GB of heap space instead of the default ~512MB.

### 2. Fixed Odds Refresh to Process Month-by-Month
**Before:** Loaded ALL fixtures for the year into memory at once
**After:** Processes one month at a time, clearing memory after each month

The code now:
- Fetches fixtures for January
- Processes each fixture (check if odds exist, fetch from API if not, save to DB)
- Clears the list
- Moves to February, etc.

This means we only hold ~100-500 fixtures in memory at a time instead of thousands.

### 3. Disabled Startup Fixture Refresh
Changed `application.yml`:
```yaml
import-tournaments-csv.refresh-fixtures: false
```

This prevents the automatic 60-day fixture refresh on startup, which was also causing memory issues.

## How to Run the App Now

### Option 1: Run from IntelliJ (Recommended)
1. Open Run → Edit Configurations
2. Find your Spring Boot configuration
3. Add to **VM options**: `-Xmx4g -Xms1g`
4. Click Apply
5. Run the app

### Option 2: Run from Maven
The `.mvn/jvm.config` file will automatically be picked up by Maven:
```bash
./mvnw spring-boot:run
```

### Option 3: Run the JAR
```bash
java -Xmx4g -Xms1g -jar target/api-tennis-adapter-*.jar
```

## Testing the Odds Refresh
1. Start the app
2. Go to http://localhost:8080/ui/admin/data-refresh
3. Select year: 2025
4. Click "Refresh Odds"
5. Watch the progress bar - it should now process month by month without crashing

## Why This Works
- **Monthly batches**: Only ~100-500 fixtures in memory at once
- **Immediate persistence**: Each fixture's odds are saved to DB immediately, then discarded
- **Memory clearing**: Explicitly clearing the fixtures list after each month
- **More heap space**: 4GB gives plenty of room for the JVM, Hibernate, and Spring Boot

## If You Still Get Memory Errors
1. **Increase heap further**: Change `-Xmx4g` to `-Xmx8g` (8GB)
2. **Check your system RAM**: Make sure you have enough free RAM
3. **Monitor memory**: Use `jconsole` or `jvisualvm` to see actual memory usage
4. **Reduce batch size**: Change monthly batches to weekly if needed

## Notes
- The `.mvn/jvm.config` file is in the `api-tennis-adapter` directory
- This file is automatically read by Maven when running the app
- IntelliJ needs manual configuration (see Option 1 above)
- The startup fixture refresh is now disabled - you can manually refresh from the admin page when needed

