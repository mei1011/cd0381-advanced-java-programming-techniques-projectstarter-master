package com.udacity.webcrawler;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.time.Clock;
import java.util.stream.Collectors;

import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;


/**
 * A class that crawls data from a given URL and its links.
 */
public class DataCrawler extends RecursiveTask<Boolean> {
    private final ConcurrentSkipListSet<String> visitedUrls;

    private final ConcurrentMap<String, Integer> counts;

    private final List<Pattern> ignoredUrls;
    private final Clock clock;
    private final String url;

    private final int maxDepth;

    private final Instant deadline;

    private final PageParserFactory parserFactory;

    public DataCrawler(String url, int maxDepth, Instant deadline, ConcurrentMap<String, Integer> counts,
                       ConcurrentSkipListSet<String> visitedUrls, List<Pattern> ignoredUrls, Clock clock,
                       PageParserFactory parserFactory) {

        this.maxDepth = maxDepth;
        this.deadline = deadline;
        this.url = url;
        this.clock = clock;
        this.parserFactory = parserFactory;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
        this.ignoredUrls = ignoredUrls;
    }

    /**
     * Computes the crawling task.
     *
     * @return true if the task was successful, false otherwise.
     */
    @Override
    protected Boolean compute() {
        if (shouldStopCrawling()) {
            return false;
        }

        if (!visitedUrls.add(url)) {
            return false;
        }

        final PageParser.Result result = parsePage();

        updateWordCounts(result);
        crawlLinks(result);

        return true;
    }


    private void updateWordCounts(PageParser.Result result) {
        for (var entry : result.getWordCounts().entrySet()) {
            counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private boolean shouldStopCrawling() {
        return maxDepth == 0 || clock.instant().isAfter(deadline) || isUrlIgnored();
    }

    private boolean isUrlIgnored() {
        return ignoredUrls.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    }

    private PageParser.Result parsePage() {
        return parserFactory.get(url).parse();
    }


    private void crawlLinks(PageParser.Result result) {
        List<DataCrawler> subtasks = result.getLinks().stream()
                .map(link -> new DataCrawler(link, maxDepth - 1, deadline, counts, visitedUrls, ignoredUrls, clock,
                        parserFactory))
                .collect(Collectors.toList());
        invokeAll(subtasks);
    }
}
