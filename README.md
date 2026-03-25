# Deep Research

An AI-powered research agent built with Spring Boot, Spring AI, and [Browserbase](https://www.browserbase.com/).
Give it a topic, and it searches the web, fetches pages, and synthesizes a structured research report, all in a single API call.

Inspired by [aarondfrancis/deep-research](https://github.com/aarondfrancis/deep-research).

## How It Works

The research pipeline has three stages:

1. **Discover** — An LLM generates 5 diverse search queries for your topic, then executes each via Browserbase's Search API. URLs are deduplicated across queries.
2. **Fetch** — Pages are fetched in parallel using virtual threads. A `@ConcurrencyLimit` annotation throttles requests to avoid rate limiting.
3. **Synthesize** — Sources are analyzed in chunks by the LLM, then compiled into a final report with an executive summary, key findings, themes, and open questions.

Reports are saved as Markdown files in the `reports/` directory.

## Prerequisites

- Java 26+
- [Browserbase](https://www.browserbase.com/) account and API key
- OpenAI API key

## Quick Start

Set your API keys:

```bash
export OPENAI_API_KEY=your-openai-key
export BROWSERBASE_API_KEY=your-browserbase-key
```

Run the app:

```bash
./mvnw spring-boot:run
```

Make a request:

```bash
curl "http://localhost:8080/api/research?topic=state+of+browser+based+AI+agents"
```

Watch the console for real-time progress, then check `reports/` for the markdown output.

## Browserbase Spring Boot Starter

This project uses the [Browserbase Spring Boot Starter](https://github.com/danvega/browserbase-spring-boot-starter) which provides:

- **Search API** — `browserbase.search().web(query, numResults)` for web search
- **Fetch API** — `browserbase.fetchAPI().create(url)` for page content retrieval

The starter auto-configures a `Browserbase` bean from your API key in `application.yaml`.

## API

**GET** `/api/research?topic={topic}`

Returns a JSON report:

```json
{
  "topic": "state of browser-based AI agents",
  "executiveSummary": "...",
  "keyFindings": ["...", "..."],
  "themes": ["...", "..."],
  "openQuestions": ["...", "..."],
  "sources": [
    { "url": "https://...", "title": "..." }
  ],
  "diagnostics": {
    "queriesGenerated": 5,
    "urlsDiscovered": 47,
    "urlsFetched": 28,
    "sourcesUsed": 28,
    "durationMs": 45000
  }
}
```

## Configuration

`src/main/resources/application.yaml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

browserbase:
  api-key: ${BROWSERBASE_API_KEY}
```

## Tech Stack

- **Spring Boot 4** with virtual threads
- **Spring AI** for LLM integration (structured output, ChatClient)
- **Browserbase** for web search and page fetching
- **`@ConcurrencyLimit`** (Spring Framework 7) for API rate limiting
