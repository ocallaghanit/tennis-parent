# Tournament-Specific Odds Refresh - Implementation Summary

## ✅ What Was Added

### 1. **Tournament Dropdown on Admin Data Refresh Page**
- Added a tournament selector to the odds refresh form
- Dropdown is populated with **ATP Singles (265)** and **Challenger Men Singles (281)** tournaments only
- Sorted alphabetically by tournament name
- Optional field - leave blank to refresh all tournaments

### 2. **Backend Tournament Filtering**
- Updated `@GetMapping("/admin/data-refresh")` to load tournaments for the dropdown
- Updated `@PostMapping("/admin/data-refresh/odds")` to accept `tournamentKey` parameter
- Implemented two different processing modes:
  - **Tournament Mode**: When tournament is selected
  - **Year Mode**: When no tournament is selected (processes monthly)

## 🎯 How It Works

### Flow for Tournament-Specific Odds Refresh

```
User selects Athens tournament (13423) on admin page
    ↓
Click "Refresh Pre-Match Odds"
    ↓
Backend receives tournamentKey = "13423"
    ↓
Fetch ALL fixtures for tournament 13423 (ignores year/month)
    ↓
For each fixture:
    ├─ Check if odds exist in DB
    ├─ If yes → skip (no API call)
    └─ If no → fetch from API → save to DB
    ↓
Progress updates every 5 matches
    ↓
Complete! Show total processed/skipped/errors
```

### Flow for Year-Wide Odds Refresh

```
User leaves tournament dropdown as "All Tournaments"
    ↓
Click "Refresh Pre-Match Odds"
    ↓
Backend processes month by month (Jan-Dec)
    ↓
For each month:
    ├─ Fetch fixtures for that month
    ├─ Filter by event type if specified
    └─ Process each fixture (check DB → fetch if missing)
    ↓
Complete! Show total processed/skipped/errors
```

## 📊 Console Output Examples

### Tournament Mode
```
🔄 Starting odds refresh for tournament: 13423
🏆 Fetching fixtures for tournament: 13423
📊 Found 42 fixtures for tournament 13423
  📈 Saved odds for 5 matches
  📈 Saved for 10 matches
  📈 Saved odds for 15 matches
...
✅ Odds refresh complete: 38 processed, 4 skipped, 0 errors
```

### Year Mode
```
🔄 Starting odds refresh for year: 2025
📅 Processing JANUARY: 156 fixtures
  📈 Saved odds for 10 matches
📅 Processing FEBRUARY: 142 fixtures
  📈 Saved odds for 20 matches
...
✅ Odds refresh complete: 1247 processed, 523 skipped, 12 errors
```

## 🚀 Usage

### Refresh Odds for Specific Tournament (e.g., Athens)

1. Go to `/ui/admin/data-refresh`
2. In the **"Refresh Pre-Match Odds"** card:
   - **Year**: 2025 (or any year)
   - **Event Type**: Leave as "All" or select specific
   - **Tournament**: Select "Athens" (or any tournament)
3. Click **"Refresh Pre-Match Odds"**
4. Watch the progress bar
5. Check console for detailed logs

### Refresh Odds for All Tournaments in a Year

1. Go to `/ui/admin/data-refresh`
2. In the **"Refresh Pre-Match Odds"** card:
   - **Year**: 2025
   - **Event Type**: "All (ATP Singles + Challenger)"
   - **Tournament**: Leave as "All Tournaments"
3. Click **"Refresh Pre-Match Odds"**
4. This will process month by month for the entire year

## 💡 Key Features

### 1. **Smart Caching**
- Always checks DB first before making API calls
- Skips matches that already have odds
- Console shows: `Skipped: X` for cached matches

### 2. **Rate Limiting**
- 300ms delay between API calls
- Prevents hitting API rate limits
- Logs progress every 5 matches

### 3. **Progress Tracking**
- Real-time progress bar on the webpage
- Detailed console logging
- Shows: processed count, skipped count, error count

### 4. **Error Handling**
- Catches and logs individual match errors
- Continues processing even if some matches fail
- Final summary includes error count

### 5. **Tournament Filtering**
- Dropdown only shows ATP Singles and Challenger tournaments
- Sorted alphabetically for easy selection
- Fetches ALL fixtures for the tournament (ignores date ranges)

## 📝 Tournament Dropdown

The tournament dropdown is populated with:
- **Event Type 265**: ATP Singles tournaments
- **Event Type 281**: Challenger Men Singles tournaments
- **Sorted**: Alphabetically by name
- **Example tournaments**:
  - Athens
  - Australian Open
  - French Open
  - Wimbledon
  - US Open
  - Indian Wells
  - Miami
  - etc.

## 🔍 Verification

### Check if Odds Were Saved

```sql
-- Check odds for Athens tournament
SELECT * FROM odds_snapshots 
WHERE tournament_key = '13423';

-- Count odds by tournament
SELECT tournament_key, COUNT(*) as odds_count
FROM odds_snapshots
GROUP BY tournament_key
ORDER BY odds_count DESC;

-- Check specific match
SELECT * FROM odds_snapshots
WHERE match_key = '12087946';
```

### Check Console Logs

Look for:
- `🏆 Fetching fixtures for tournament: X`
- `📊 Found Y fixtures for tournament X`
- `📈 Saved odds for Z matches`
- `✅ Odds refresh complete: ...`

## ⚠️ Important Notes

1. **Run Fixtures Refresh First**
   - Always refresh fixtures before refreshing odds
   - Odds refresh needs fixtures to exist in the DB

2. **Tournament Must Exist**
   - The tournament must be in your database
   - If you don't see a tournament in the dropdown, run fixtures refresh first

3. **API Rate Limits**
   - Tournament mode is faster (fewer matches)
   - Year mode takes longer but processes everything
   - Both modes include rate limiting (300ms delay)

4. **Network Required**
   - Stable internet connection needed
   - API-Tennis must be accessible
   - Check console for DNS/connection errors

## 🎯 Use Cases

### Use Case 1: Quick Odds for One Tournament
**Scenario**: You want to backtest Athens tournament but it has no odds

**Solution**:
1. Select Athens from dropdown
2. Click "Refresh Pre-Match Odds"
3. Wait ~2-5 minutes (depending on tournament size)
4. Run backtest with full P&L data

### Use Case 2: Populate Odds for Entire Year
**Scenario**: You want to backtest multiple tournaments in 2025

**Solution**:
1. Leave tournament as "All Tournaments"
2. Select year: 2025
3. Click "Refresh Pre-Match Odds"
4. Wait ~20-60 minutes (depending on number of matches)
5. All tournaments will have odds

### Use Case 3: Fill Missing Odds
**Scenario**: Some tournaments have odds, some don't

**Solution**:
1. Leave tournament as "All Tournaments"
2. The refresh will skip tournaments that already have odds
3. Only fetches missing odds (efficient)

## 🔄 Integration with Automatic Odds Fetching

This manual odds refresh complements the automatic odds fetching:

- **Manual Refresh** (this feature):
  - Bulk operation for many matches
  - Progress bar and detailed logging
  - Ideal for pre-populating odds
  - Run during off-peak hours

- **Automatic Fetching** (during predictions/backtests):
  - On-demand for individual matches
  - Happens automatically when viewing predictions
  - Ideal for missing odds during analysis
  - Rate limited (300ms delay)

Both methods:
- Check DB first (no redundant API calls)
- Save odds permanently
- Include rate limiting
- Handle errors gracefully

## ✅ Summary

You can now:
- ✅ Refresh odds for a **specific tournament** (e.g., Athens)
- ✅ Refresh odds for **all tournaments** in a year
- ✅ See only **ATP Singles** and **Challenger** tournaments in dropdown
- ✅ Track progress in real-time
- ✅ Avoid redundant API calls (smart caching)
- ✅ Handle rate limits automatically (300ms delay)
- ✅ View detailed console logs for debugging

