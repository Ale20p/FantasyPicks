# FantasyPicks 🏈

FantasyPicks is a fantasy football draft assistance tool that aggregates and normalizes player rankings from multiple top-tier sources (ESPN, Sleeper, Yahoo, DraftSharks, etc.) to provide a consensus view for your draft.

Built with a Spring Boot backend and a clean, responsive web frontend, it helps you make data-driven decisions during your fantasy football draft.

## ✨ Key Features

- **Consensus Rankings**: View average rankings calculated from multiple sources.
- **Source Selection**: Toggle specific data sources (ESPN, Sleeper, etc.) to customize your consensus.
- **League Type Support**: Specialized rankings for **1 PPR**, **0.5 PPR**, and **Standard** (Non-PPR) formats.
- **Smart Data Persistence**: 
    - Automatic caching with a 24-hour update cycle.
    - Permanent storage for historical season data.
    - Manual refresh capability.
- **Draft Mode**: Interactive UI to track drafted players in real-time.
- **Data Normalization**: Advanced fuzzy matching for player names and standardized NFL team tickers across different sources.

## 🏗️ Architecture

- **Backend**: Java 21, Spring Boot 3.5.x, Hibernate/JPA.
- **Scraping Engine**: Hybrid approach using **Jsoup** for static sites and **Playwright** for dynamic, JavaScript-heavy sources.
- **Database**: PostgreSQL (running in Docker).
- **Frontend**: Modern Vanilla JS, HTML5, and CSS3 (integrated as a nested monolith within the Spring Boot application).

## 🚀 Getting Started

### Prerequisites

Ensure you have the following installed on your machine:
- **Java 21** (JDK 21)
- **Docker & Docker Compose**
- **Maven** (optional, the project includes a Maven wrapper `./mvnw`)

### Setup Instructions

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/your-username/FantasyPicks.git
    cd FantasyPicks
    ```

2.  **Start the Database**
    The application requires PostgreSQL. Use the provided Docker Compose file to spin up the database:
    ```bash
    docker-compose up -d
    ```

3.  **Install Playwright Browsers**
    The scraping engine requires Playwright browsers. Run the following command to ensure they are installed (this only needs to be done once):
    ```bash
    mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install" -pl fantasy-picks
    ```
    *Alternatively, Playwright will attempt to install browsers automatically on the first run.*

4.  **Run the Application**
    Navigate to the project directory and start the Spring Boot server:
    ```bash
    cd fantasy-picks
    ./mvnw spring-boot:run
    ```

5.  **Access the Dashboard**
    Once the application starts, open your browser and navigate to:
    [http://localhost:8081](http://localhost:8081)

## 🛠️ Configuration

The application's configuration can be found in `fantasy-picks/src/main/resources/application.properties`.

Key settings:
- `server.port`: Default is `8081`.
- `spring.datasource.url`: JDBC connection string for PostgreSQL.
- `spring.jpa.hibernate.ddl-auto`: Set to `update` for automatic schema management.

## 📁 Project Structure

```text
.
├── fantasy-picks/          # Main Spring Boot Application
│   ├── src/main/java       # Backend logic (Scrapers, Services, Controllers)
│   └── src/main/resources  # Config and Frontend (static/)
├── docs/                   # Detailed documentation (Features, Architecture)
├── docker-compose.yml      # Database orchestration
└── README.md               # You are here
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.