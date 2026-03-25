package dev.danvega.deepresearch;

import org.springframework.web.bind.annotation.*;

@RestController
public class ResearchController {

    private final ResearchPipeline pipeline;

    public ResearchController(ResearchPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping("/api/research")
    public ResearchReport research(@RequestParam String topic) {
        return pipeline.execute(topic);
    }
}
