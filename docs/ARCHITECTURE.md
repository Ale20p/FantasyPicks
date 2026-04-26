# Core Architecture: Nested Monolith
The application is a Java project that has the frontend inside of it.

# Backend
Tech: Java, Spring Boot

For scraping data:
- Jsoup (Static Sites)
- Playwright (Dynamic/JS Sites)

Data Sources:
- Yahoo Fantasy
- Sleeper
- ESPN
- NFL.com
- FantasyPros
- CBS Sports

Normalization Layer:
- will be used to normalize the data from the different sources
- will be used to combine the data from the different sources
- Team Normalization: Create a standardized mapping for NFL teams (e.g., "Kansas City Chiefs", "KC", and "Chiefs" all map to "KC"). This ensures the team part of the key is always identical across sources.
- Name Normalization: Implement a "fuzzy" cleaning process that:
    
    - Removes suffixes (Jr., Sr., II, III).
    - Removes special characters (periods, apostrophes).
    - Standardizes common nicknames (e.g., "Mitch Trubisky" vs "Mitchell Trubisky").

## Database
Tech: PostgreSQL
- will be used to store players' data

# Frontend
### For Now:
Tech: HTML, CSS and JavaScript if needed
- Using this for now for the sake of pure simplisity

### In The Future:
Tech: React + Vite