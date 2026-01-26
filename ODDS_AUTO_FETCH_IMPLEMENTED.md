# Automatic Odds Fetching - Implementation Summary

## What Was Implemented

We've now implemented **automatic odds fetching** during predictions and backtests. This means:

### ✅ When Running Predictions (`/ui/predictions/results`)
- If a match doesn't have odds in the database
- The system will **automatically fetch them from API-Tennis**
- Store them in the database
- Use them for the prediction

### ✅ When Running Backtests (`/ui/predictions/backtest`)
- Same behavior as prediction results
- Automatically fetches and stores missing odds
- This is especially useful for historical tournaments like Athens

### ✅ Smart Caching to Avoid Redundant API Calls
The `fetchAndStoreOdds()` helper method:
1. **First checks the database** for existing odds
2. If odds exist → returns them immediately (no API call)
3. If odds don't exist → fetches from API, saves to DB, returns them
4. Logs each fetch: `✅ Fetched and stored odds for match: {eventKey}`

## How It Works

### Flow Diagram
```
User runs backtest/prediction
    ↓
For each match:
    ↓
Check: Do odds exist in DB?
    ↓
YES → Use existing odds (no API call)
    ↓
NO → Fetch from API → Save to DB → Use odds
    ↓
Continue with prediction
```

## Benefits

1. **No Manual Odds Refresh Needed**: When you run a backtest for Athens tournament, it will automatically fetch any missing odds
2. **Efficient**: Only makes API calls for matches that don't have odds yet
3. **Persistent**: Once fetched, odds are stored permanently in the database
4. **Resumable**: If a backtest is interrupted, already-fetched odds won't be re-fetched

## Example: Athens Tournament

**Before:**
- Run backtest for Athens
- No odds available
- Can't calculate P&L or ROI

**After:**
- Run backtest for Athens
- System detects missing odds
- Automatically fetches them from API-Tennis
- Stores them in the database
- Completes the backtest with full P&L data
- Next time you run Athens backtest → uses cached odds (no API calls)

## Console Output

When odds are fetched, you'll see:
```
✅ Fetched and stored odds for match: 12087946
✅ Fetched and stored odds for match: 12087947
...
```

If there's an error:
```
❌ Error fetching odds for 12087946: Connection timeout
```

## Rate Limiting

- The backtest/prediction pages don't have built-in delays
- If you're processing many matches, the API calls happen as fast as possible
- The manual odds refresh (admin page) has a 300ms delay between calls
- Consider adding a small delay if you hit rate limits

## Technical Details

### New Method: `fetchAndStoreOdds()`
Location: `UiController.java` (line ~3257)

**Parameters:**
- `eventKey`: Match ID
- `tournamentKey`: Tournament ID
- `eventDate`: Match date

**Returns:**
- `Map<String, Object>` with `firstOdds`, `secondOdds`, `book`
- `null` if fetch failed

**Database Check:**
```java
var existing = oddsSnapshotRepository.findByMatchKeyAndSnapshotType(eventKey, "pre");
if (existing.isPresent()) {
    // Return cached odds without API call
}
```

### Integration Points

1. **Prediction Results** (line ~2332):
   ```java
   var opt = oddsSnapshotRepository.findByMatchKeyAndSnapshotType(matchKey, "pre");
   if (opt.isPresent()) {
       // Use existing
   } else {
       var oddsData = fetchAndStoreOdds(matchKey, ...);
   }
   ```

2. **Backtest** (line ~2581):
   - Same pattern as prediction results

## Testing

### Test 1: Athens Tournament Backtest
1. Go to `/ui/predictions/backtest`
2. Select tournament: Athens (13423)
3. Click "Run Backtest"
4. Watch console for `✅ Fetched and stored odds for match: ...`
5. Check database: `SELECT * FROM odds_snapshots WHERE tournament_key = '13423';`

### Test 2: Verify No Duplicate Fetches
1. Run Athens backtest again
2. Console should NOT show any `✅ Fetched...` messages
3. All odds should be loaded from cache

### Test 3: Prediction Results
1. Go to `/ui/predictions/results`
2. Select Athens tournament
3. Should see odds for all matches (fetched automatically if missing)

## Notes

- This only fetches **pre-match odds** (`snapshotType = "pre"`)
- Live odds are not fetched automatically
- The manual odds refresh page still exists for bulk operations
- API-Tennis rate limits may apply for large tournaments

