package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.FetchedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SynthesisService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisService.class);

    private final ChatClient chatClient;

    SynthesisService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public record Report(
            String executiveSummary,
            List<String> keyFindings,
            List<String> themes,
            List<String> openQuestions
    ) {}

    public Report synthesize(String topic, List<FetchedSource> sources, int chunkSize, 
                             String analyzeChunkPrompt, String compileReportPrompt) {
        log.info("--- Step 3: Synthesize ---");

        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += chunkSize) {
            var chunk = sources.subList(i, Math.min(i + chunkSize, sources.size()));
            int chunkNum = (i / chunkSize) + 1;
            int totalChunks = (sources.size() + chunkSize - 1) / chunkSize;
            log.info("  Analyzing chunk {}/{} ({} sources)...", chunkNum, totalChunks, chunk.size());

            StringBuilder sourcesText = new StringBuilder();
            for (var source : chunk) {
                sourcesText.append("--- Source: %s (%s) ---\n%s\n\n".formatted(
                        source.title(), source.url(), source.content()));
            }

            String prompt = analyzeChunkPrompt.formatted(topic, sourcesText.toString());
            String summary = chatClient.prompt(prompt)
                    .call()
                    .content();

            log.info("Adding summary {}" , summary);
            chunkSummaries.add(summary);
        }

        log.info("  Compiling final report...");
        String allSummaries = String.join("\n\n---\n\n", chunkSummaries);

        String finalPrompt = compileReportPrompt.formatted(topic, allSummaries);
        return chatClient.prompt(finalPrompt)
                .call()
                .entity(Report.class);
    }
}