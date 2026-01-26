# Rate Limit Handling & API Monitoring - Implementation Summary

## ✅ What Was Added

### 1. **Automatic Rate Limiting**
- **300ms delay** between API calls to avoid hitting rate limits
- Tracks time since last API call and sleeps if needed
- Applies to all automatic odds fetching (predictions, backtests)

### 2. **API Call Statistics Tracking**
```java
oddsApiCallCount  // Total API calls made this session
oddsApiErrorCount // Total errors encountered
lastOddsApiCall   // Timestamp of last API call
```

### 3. **Comprehensive Error Detection**

#### HTTP Status Codes
- **429 (Too Many Requests)**: Rate limit exceeded
- **403 (Forbidden)**: API key issue or subscription problem
- **Other HTTP errors**: Logged with status code

#### API Response Errors
- Checks for `{"error": "..."}` in API response
- Detects keywords: "limit", "quota", "throttle", "rate"

#### Exception Message Scanning
- Scans exception messages for rate limit indicators
- Alerts you if rate limiting is suspected

### 4. **Detailed Console Logging**

#### Progress Updates (Every 10 Successful Fetches)
```
📈 Odds API progress: 20 calls, 0 errors
📈 Odds API progress: 30 calls, 1 errors
```

#### Rate Limit Detection
```
🚫 RATE LIMIT (429) for 12087946! You've exceeded your API quota.
📊 API calls made this session: 45
⏸️  Consider waiting before making more requests.
```

#### Other Errors
```
⚠️  API Error for 12087946: Daily quota exceeded
🔒 FORBIDDEN (403) for 12087946! Check your API key or subscription.
ℹ️  No odds available for match: 12087946
❌ HTTP 500 error fetching odds for 12087946: Internal Server Error
```

## 🎯 How It Works

### Flow with Rate Limiting
```
User runs backtest for Athens tournament
    ↓
For each match:
    ↓
Check DB for odds
    ├─ If exists → return immediately (no API call)
    └─ If missing:
        ↓
        Wait 300ms since last API call (rate limiting)
        ↓
        Increment oddsApiCallCount
        ↓
        Call API-Tennis
        ↓
        Check response for errors/rate limits
        ↓
        If successful → save to DB, return odds
        ↓
        If error → log detailed message, increment oddsApiErrorCount
    ↓
Continue with next match
```

## 📊 Console Output Examples

### Normal Operation
```
✅ Fetched and stored odds for match: 12087946
✅ Fetched and stored odds for match: 12087947
📈 Odds API progress: 10 calls, 0 errors
✅ Fetched and stored odds for match: 12087948
...
```

### Rate Limit Hit
```
✅ Fetched and stored odds for match: 12087946
📈 Odds API progress: 40 calls, 0 errors
🚫 RATE LIMIT (429) for 12087947! You've exceeded your API quota.
📊 API calls made this session: 41
⏸️  Consider waiting before making more requests.
❌ Error fetching odds for 12087948: WebClientResponseException - 429 Too Many Requests
🚫 Possible rate limit detected in error message!
📊 API calls made this session: 42
```

### API Key Issue
```
🔒 FORBIDDEN (403) for 12087946! Check your API key or subscription.
📊 API calls made this session: 1
```

### No Odds Available
```
ℹ️  No odds available for match: 12087946
```

## 🚀 Testing

### Test 1: Normal Backtest with Odds Fetching
1. Restart your app
2. Go to `/ui/predictions/backtest`
3. Select Athens tournament
4. Run backtest
5. Watch console for:
   - `📈 Odds API progress: X calls, Y errors`
   - Successful odds fetches

### Test 2: Check Rate Limit Handling
1. Run a backtest with many matches (e.g., full year)
2. Watch for rate limit messages
3. If you hit the limit, you'll see:
   ```
   🚫 RATE LIMIT (429)
   📊 API calls made this session: X
   ```

### Test 3: Verify Rate Limiting Delay
1. Enable debug logging to see timestamps
2. Run backtest
3. Verify ~300ms between API calls

## 💡 API-Tennis Limits

Based on typical API plans:
- **Free tier**: ~100-500 requests/day
- **Basic tier**: ~1,000-5,000 requests/day
- **Pro tier**: 10,000+ requests/day

The 300ms delay means:
- **Max ~200 requests/minute**
- **Max ~3 requests/second**

This should be well within most API limits.

## 🔧 Adjusting Rate Limit Delay

If you need to change the delay, edit line 3311 in `UiController.java`:

```java
// Current: 300ms delay
if (timeSinceLastCall < 300 && lastOddsApiCall > 0) {
    Thread.sleep(300 - timeSinceLastCall);
}

// Slower (safer): 500ms delay
if (timeSinceLastCall < 500 && lastOddsApiCall > 0) {
    Thread.sleep(500 - timeSinceLastCall);
}

// Faster (riskier): 100ms delay
if (timeSinceLastCall < 100 && lastOddsApiCall > 0) {
    Thread.sleep(100 - timeSinceLastCall);
}
```

## 📝 What to Do If You Hit Rate Limits

### Immediate Actions
1. **Stop the current operation** (refresh the page or interrupt the backtest)
2. **Wait 1 hour** (most APIs reset hourly)
3. **Check your API dashboard** to see your quota usage

### Long-term Solutions
1. **Upgrade your API plan** if you need more requests
2. **Use the manual odds refresh** during off-peak hours to pre-populate odds
3. **Filter backtests** to smaller date ranges or specific tournaments
4. **Increase the rate limit delay** (see above)

### Check Your API Usage
- Log into your API-Tennis account
- Check the dashboard for:
  - Total requests made today
  - Remaining quota
  - Rate limit reset time

## 🎯 Pre-computed Backtest Mode

When you select a tournament in **pre-computed backtest mode** (no custom config):
- It will now automatically fetch missing odds
- Same rate limiting and error handling applies
- Odds are cached in DB for future use

## 📊 Session Statistics

The counters reset when you restart the app:
- `oddsApiCallCount`: Total API calls this session
- `oddsApiErrorCount`: Total errors this session

These help you track how many API calls you're making during testing.

## 🐛 Troubleshooting

### "Connection error" or DNS issues
- Not a rate limit - it's a network/DNS problem
- See `MEMORY_FIX_INSTRUCTIONS.md` for DNS troubleshooting

### Odds not appearing after fetch
- Check console for error messages
- Verify the match has odds available (some matches don't)
- Check database: `SELECT * FROM odds_snapshots WHERE match_key = 'YOUR_MATCH_KEY';`

### Too many API calls
- Enable `refresh-catalog: false` in `application.yml` (already done)
- Use manual odds refresh during off-peak hours
- Filter to smaller date ranges

## ✅ Summary

You now have:
- ✅ Automatic rate limiting (300ms delay)
- ✅ Comprehensive error detection (HTTP codes, API errors, exceptions)
- ✅ Detailed console logging with emojis for easy scanning
- ✅ API call statistics tracking
- ✅ Rate limit detection and warnings
- ✅ Works in both prediction results and backtest modes
- ✅ Caches odds in DB to avoid redundant API calls

