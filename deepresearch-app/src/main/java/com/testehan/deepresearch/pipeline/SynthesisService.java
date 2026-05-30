package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.config.ChatClientProvider;
import com.testehan.deepresearch.model.EarningsPresentationReport;
import com.testehan.deepresearch.model.FetchedSource;
import com.testehan.deepresearch.model.LlmUsage;
import com.testehan.deepresearch.service.LlmCostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SynthesisService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisService.class);

    private final ChatClientProvider chatClientProvider;
    private final ChatClient geminiChatClient;
    private final LlmCostService llmCostService;

    SynthesisService(ChatClientProvider chatClientProvider,
                     @Qualifier("geminiChatClient") ChatClient geminiChatClient,
                     LlmCostService llmCostService) {
        this.chatClientProvider = chatClientProvider;
        this.geminiChatClient = geminiChatClient;
        this.llmCostService = llmCostService;
    }

    public record Report(
            String executiveSummary,
            List<String> keyFindings,
            List<String> themes,
            List<String> openQuestions
    ) {}

    public Report synthesize(String topic, List<FetchedSource> sources, int chunkSize,
                             String analyzeChunkPrompt, String compileReportPrompt,
                             LlmUsage usage, String modelId) {
        log.info("--- Step 3: Synthesize ---");

        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += chunkSize) {
            var chunk = sources.subList(i, Math.min(i + chunkSize, sources.size()));
            int chunkNum = (i / chunkSize) + 1;
            int totalChunks = (sources.size() + chunkSize - 1) / chunkSize;
            log.info("  Analyzing chunk {}/{} ({} sources)...", chunkNum, totalChunks, chunk.size());

            String prompt = analyzeChunkPrompt.formatted(topic, buildSourcesText(chunk));
            ChatResponse response = callLlm(prompt, modelId, "synthesis_chunk", usage);
            chunkSummaries.add(response.getResult().getOutput().getText());
        }

        log.info("  Compiling final report...");
        String allSummaries = String.join("\n\n---\n\n", chunkSummaries);
        return compileFinalReport(topic, allSummaries, compileReportPrompt, usage, modelId);
    }

    /** Builds chunk prompts without making LLM calls — used for batch mode. */
    public List<String> buildChunkPrompts(String topic, List<FetchedSource> sources,
                                          int chunkSize, String analyzeChunkPrompt) {
        List<String> prompts = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += chunkSize) {
            var chunk = sources.subList(i, Math.min(i + chunkSize, sources.size()));
            prompts.add(analyzeChunkPrompt.formatted(topic, buildSourcesText(chunk)));
        }
        return prompts;
    }

    /** Compiles chunk summaries into a final report — callable after batch results arrive. */
    public Report compileFinalReport(String topic, String allSummaries,
                                     String compileReportPrompt, LlmUsage usage, String modelId) {
        String finalPrompt = compileReportPrompt.formatted(topic, allSummaries);
        var converter = new BeanOutputConverter<>(Report.class);
        ChatResponse finalResponse = callLlm(finalPrompt + "\n\n" + converter.getFormat(),
                modelId, "synthesis_compile", usage);
        return converter.convert(finalResponse.getResult().getOutput().getText());
    }

    public EarningsPresentationReport synthesizeDocument(List<byte[]> images, String pagePrompt,
                                                         String compileReportPrompt, LlmUsage usage) {
        log.info("--- Step: Document Synthesis ---");

        List<String> pageSummaries = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            int pageNum = i + 1;
            log.info("  Analyzing page {}/{}...", pageNum, images.size());

            String prompt = pagePrompt.formatted(pageNum, "");
            byte[] imageBytes = images.get(i);

            ChatResponse pageResponse = geminiChatClient.prompt()
                    .user(u -> u.text(prompt)
                            .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                    .call().chatResponse();
            llmCostService.logAndAccumulate(pageResponse, "document_page", usage, "gemini-2.5-pro");
            String summary = pageResponse.getResult().getOutput().getText();

            log.info("Page {} summary: {}", pageNum, summary);
            pageSummaries.add("Page " + pageNum + ":\n" + summary);

            String appendixCheckPrompt = """
                Analyze the following summary and determine if it represents the start of the 'Appendix' section of an earnings presentation.

                These sections typically follow the main business narrative and are characterized by:
                1. Explicit headers like 'Appendix'.

                Respond in JSON format:
                {
                  "isAppendix": boolean,
                  "explanation": "Briefly explain why or why not, referencing the specific markers found",
                  "triggerField": "The specific field or text that caused this decision (null if not an appendix)"
                }

                Summary:
                %s
                """.formatted(summary);

            try {
                record AppendixDetection(boolean isAppendix, String explanation, String triggerField) {}

                var appendixConverter = new BeanOutputConverter<>(AppendixDetection.class);
                ChatResponse appendixResponse = geminiChatClient
                        .prompt(appendixCheckPrompt + "\n\n" + appendixConverter.getFormat())
                        .call().chatResponse();
                llmCostService.logAndAccumulate(appendixResponse, "document_appendix_check", usage, "gemini-2.5-pro");

                var detection = appendixConverter.convert(appendixResponse.getResult().getOutput().getText());

                if (detection != null) {
                    log.info("  Appendix check - isAppendix: {}, explanation: {}, triggerField: {}",
                            detection.isAppendix(), detection.explanation(), detection.triggerField());

                    if (detection.isAppendix()) {
                        log.info("  Appendix detected on page {}. Skipping remaining pages.", pageNum);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("  Failed to perform appendix check for page {}: {}", pageNum, e.getMessage());
            }
        }

        log.info("  Compiling final document report...");
        String allSummaries = String.join("\n\n---\n\n", pageSummaries);

        var reportConverter = new BeanOutputConverter<>(EarningsPresentationReport.class);
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("  Compiling final document report (Attempt {}/{})...", attempt, maxRetries);
                String finalPrompt = compileReportPrompt.formatted(allSummaries) + "\n\n" + reportConverter.getFormat();
                if (attempt > 1) {
                    finalPrompt = "RETRY HINT: Your previous response was not a valid JSON. Please ensure all quotes are closed and colons are used correctly.\n" + finalPrompt;
                }
                ChatResponse compileResponse = geminiChatClient.prompt(finalPrompt).call().chatResponse();
                llmCostService.logAndAccumulate(compileResponse, "document_compile", usage, "gemini-2.5-pro");
                return reportConverter.convert(compileResponse.getResult().getOutput().getText());
            } catch (Exception e) {
                log.error("  Attempt {} failed to compile final report: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    throw e;
                }
            }
        }
        return null;
    }

    private ChatResponse callLlm(String prompt, String modelId, String operationName, LlmUsage usage) {
        ChatClient client = chatClientProvider.chatClientFor(modelId);
        GoogleGenAiChatOptions options = chatClientProvider.optionsFor(modelId);
        var requestSpec = client.prompt(prompt);
        if (options != null) requestSpec = requestSpec.options(options);
        ChatResponse response = requestSpec.call().chatResponse();
        llmCostService.logAndAccumulate(response, operationName, usage, modelId);
        return response;
    }

    private static String buildSourcesText(List<FetchedSource> sources) {
        var sb = new StringBuilder();
        for (var source : sources) {
            sb.append("--- Source: %s (%s) ---\n%s\n\n".formatted(
                    source.title(), source.url(), source.content()));
        }
        return sb.toString();
    }
}
