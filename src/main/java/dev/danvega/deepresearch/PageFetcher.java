package dev.danvega.deepresearch;

import dev.danvega.browserbase.Browserbase;
import dev.danvega.browserbase.model.FetchAPICreateResponse;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
class PageFetcher {

    private final Browserbase browserbase;

    PageFetcher(Browserbase browserbase) {
        this.browserbase = browserbase;
    }

    @ConcurrencyLimit(5)
    FetchAPICreateResponse fetch(String url) {
        return browserbase.fetchAPI().create(url);
    }
}
