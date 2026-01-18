# DeepResearch Component

DeepResearch is a Spring Boot application designed to perform automated research using Large Language Models (LLMs). It supports two primary modes: web-based research and document-based analysis (specifically optimized for earnings presentations).

## Features

- **Web Research (News)**: Automatically generates search queries, discovers relevant URLs, fetches content, and synthesizes a comprehensive report on a given subject.
- **Document Analysis (Earnings Presentations)**: Processes PDF documents (e.g., earnings presentations) by converting pages to images, analyzing them with vision-capable LLMs, and compiling a detailed financial report.
- **Polymorphic Reporting**: Generates structured reports based on the research topic:
    - `NewsReport`: Focuses on findings, themes, and open questions with full source citations.
    - `EarningsPresentationReport`: Extracts key financials, KPIs, guidance, and strategic highlights from financial documents.
- **Intelligent Processing**:
    - **Appendix Detection**: Automatically identifies the start of appendix sections in documents to skip irrelevant technical schedules and optimize processing time.
    - **Retry Mechanism**: Implements robust retry logic for LLM JSON output to handle malformed responses during complex report compilation.
- **Async Job Execution**: Uses Spring's `@Async` and Virtual Threads to handle long-running research tasks without blocking the API.

## Prerequisites

- **Java 21+**: The project is configured to use modern Java features (Virtual Threads).
- **Maven**: For building and dependency management.
- **Ollama**: Must be running locally or at a reachable URL with a vision-capable model (e.g., `gemma4:26b` or `llama3-vision`).
- **Browserbase**: Required for web discovery and fetching (API key required).

## Configuration

The application is configured via `deepresearch-app/src/main/resources/application.properties`. Key properties include:

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | The port the application runs on. | `8081` |
| `spring.ai.ollama.base-url` | URL for the Ollama service. | `http://localhost:11434` |
| `spring.ai.ollama.chat.options.model` | The LLM model to use. | `gemma4:26b` |
| `browserbase.api-key` | API key for Browserbase fetching. | (Required) |
| `research.max-sources` | Default limit for discovered web sources. | `5` |

### Secrets
You can provide sensitive keys in a `secrets.properties` file in the project root:
```properties
browserbase.api-key=your_api_key_here
```

## API Endpoints

### 1. Web Research
`POST /api/research`

Starts a web research job.

**Request Body:**
```json
{
  "topic": "news",
  "subject": "The future of quantum computing",
  "maxSources": 10
}
```

### 2. Document Research
`POST /api/research/document`

Analyzes an uploaded PDF document.

**Form Data:**
- `pdf`: The PDF file (multipart/form-data).
- `request`: A JSON part containing `ResearchDocumentRequest`.

**Request Part (`request`):**
```json
{
  "topic": "earnings_presentation"
}
```

### 3. Job Status & Results
`GET /api/research/{jobId}`

Retrieves the current status or the final result of a research job.

**Response (News):**
```json
{
  "status": "completed",
  "reportType": "news",
  "result": {
    "topic": "news",
    "executiveSummary": "...",
    "keyFindings": ["..."],
    "sources": [{"url": "...", "title": "..."}]
  }
}
```

**Response (Earnings Presentation):**
```json
{
  "status": "completed",
  "reportType": "earnings_presentation",
  "result": {
    "company_metadata": { "company_name": "...", "report_period": "..." },
    "headline_financials": { "total_revenue": "...", "yoy_revenue_growth": "..." },
    "strategic_highlights": ["..."]
  }
}
```

## Running the Application

1. Ensure Ollama is running and the required model is pulled.
2. Build the project: `./mvnw clean install`
3. Run the application: `./mvnw spring-boot:run -pl deepresearch-app`
