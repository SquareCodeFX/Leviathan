package de.feelix.leviathan.command.pagination.demo;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.datasource.ListDataSource;
import de.feelix.leviathan.command.pagination.domain.NavigationWindow;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import de.feelix.leviathan.command.pagination.service.PaginationService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PaginationDemo {

    public static void main(String[] args) {
        // Create test data (309 pages * 10 items = 3090 entries)
        List<String> testData = createTestData(3090);

        // Configure pagination with ±3 side pages
        PaginationConfig config = PaginationConfig.builder()
            .pageSize(5)            // 10 items per page
            .sidePages(3)            // ±3 pages visible around current
            .cacheEnabled(true)      // Enable caching
            .cacheMaxSize(50)        // Cache 50 pages
            .asyncEnabled(true)      // Enable async operations
            .navigationSymbols("<-", "->")        // Navigation symbols
            .currentPageMarkers("_", "_")         // Highlight current: _7_
            .pageSeparator(" | ")    // Separator between pages
            .ellipsis("...")         // Ellipsis for hidden pages
            .build();

        // Create data source from list
        ListDataSource<String> dataSource = ListDataSource.of(testData);

        // Build pagination service with caching
        PaginationService<String> service = PaginationService.<String>builder()
            .config(config)
            .dataSource(dataSource)
            .withDefaultCache()
            .build();

        for (int i = 0; i < 20; i++) {
            PaginatedResult<String> result = service.getPage(ThreadLocalRandom.current().nextInt(1, 309) + 1);

            result.getItems().forEach(System.out::println);
            System.out.println();
            System.out.println(renderPageWindow(result.getNavigationWindow(), config));
        }

        service.getCacheStats().ifPresent(stats -> {
            System.out.println("Cache Hits:      " + stats.getHitCount());
            System.out.println("Cache Misses:    " + stats.getMissCount());
            System.out.println("Hit Rate:        " + String.format("%.1f%%", stats.getHitRate() * 100));
            System.out.println("Total Entries:   " + stats.getCurrentSize());
            System.out.println("Evictions:       " + stats.getEvictionCount());
        });
    }

    private static String renderPageWindow(NavigationWindow window, PaginationConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        List<Integer> visiblePages = window.getVisiblePages();
        boolean first = true;

        if (window.showStartEllipsis()) {
            sb.append("1 ").append(config.getEllipsis()).append(" ");
        }

        for (int page : visiblePages) {
            if (!first) {
                sb.append(config.getPageSeparator());
            }
            first = false;

            if (page == window.getCurrentPage()) {
                sb.append(config.getCurrentPagePrefix())
                    .append(page)
                    .append(config.getCurrentPageSuffix());
            } else {
                sb.append(page);
            }
        }

        if (window.showEndEllipsis()) {
            sb.append(" ").append(config.getEllipsis()).append(" ").append(window.getTotalPages());
        }

        sb.append(")");
        return sb.toString();
    }

    private static List<String> createTestData(int count) {
        List<String> data = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            data.add("Entry #" + i);
        }
        return data;
    }
}
