package com.testehan.deepresearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
class SynthesisService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisService.class);

    private final ChatClient chatClient;

    SynthesisService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    record Report(
            String executiveSummary,
            List<String> keyFindings,
            List<String> themes,
            List<String> openQuestions
    ) {}

    Report synthesize(String topic, List<FetchedSource> sources) {
        log.info("--- Step 3: Synthesize ---");

        // Stage 1: Analyze sources in chunks of 4
        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += 4) {
            var chunk = sources.subList(i, Math.min(i + 4, sources.size()));
            int chunkNum = (i / 4) + 1;
            int totalChunks = (sources.size() + 3) / 4;
            log.info("  Analyzing chunk {}/{} ({} sources)...", chunkNum, totalChunks, chunk.size());

            StringBuilder sourcesText = new StringBuilder();
            for (var source : chunk) {
                sourcesText.append("--- Source: %s (%s) ---\n%s\n\n".formatted(
                        source.title(), source.url(), source.content()));
            }

            String summary = chatClient.prompt("""
                    You are a research analyst. Analyze these sources about "%s".

                    Extract and summarize:
                    - Key facts and findings (cite the source URL for each)
                    - Points where sources agree
                    - Points where sources disagree or have caveats

                    Sources:
                    %s
                    """.formatted(topic, sourcesText.toString()))
                    .call()
                    .content();

            log.info("Adding summary {}" , summary);
            chunkSummaries.add(summary);
        }

        // Stage 2: Compile final report
        log.info("  Compiling final report...");
        String allSummaries = String.join("\n\n---\n\n", chunkSummaries);

        return chatClient.prompt("""
                You are a senior research analyst compiling a briefing document about "%s".

                Based on these extracted findings from multiple sources, produce:
                1. An executive summary (2-3 paragraphs)
                2. Key findings (list of concise bullet points, cite source URLs)
                3. Major themes and areas of consensus
                4. Open questions that need further research

                Extracted findings:
                %s
                """.formatted(topic, allSummaries))
                .call()
                .entity(Report.class);
    }
}
