# Country Days Tracker Bot

A Telegram bot that tracks the countries you've visited and provides statistics about your travels.

## Features

- Track countries visited based on location data
- View statistics about your travels, including:
  - List of visited countries with days spent in each
  - Percentage of world countries visited
  - Current country and length of stay
- Converts time spent in countries to a human-readable format (years, months, weeks, days)

## Requirements

- Java 22
- ClickHouse database
- Telegram Bot API token

## Environment Variables

- `CLICKHOUSE_URL`: URL to your ClickHouse database
- Access control environment variables (from the `ktgbotapi-restrict-access-middleware` library)

## Building and Running

### Local Development

```bash
# Build the application
./gradlew build

# Run the application
./gradlew run
```

### Production Deployment

The application is configured to be built and deployed using Docker:

```bash
# Build the Docker image
docker build -t country-days-tracker-bot .

# Run the Docker container
docker run -e CLICKHOUSE_URL=your_clickhouse_url -e OTHER_ENV_VARS=values country-days-tracker-bot
```

## Database Schema

The application requires a ClickHouse database with the following schema:

```sql
CREATE TABLE country_days_tracker_bot.country_days_tracker (
    date_time DateTime,
    user_id Int64,
    latitude Float32,
    longitude Float32,
    country String,
    tzname String
) ENGINE = MergeTree()
ORDER BY (user_id, date_time);
```

## License

See the [LICENSE](LICENSE) file for details.