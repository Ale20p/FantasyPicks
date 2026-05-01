package com.FantasyPicks.fantasy_picks;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

public class PlaywrightTest {
    @Test
    public void fetchDraftSharks() {
        System.out.println("Starting Playwright...");
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            System.out.println("Navigating to DraftSharks...");
            page.navigate(
                    "https://www.draftsharks.com/rankings/load-table?pprSuperflexSlug=ppr&researchDepth=rankings");

            // Wait for the table rows to render
            page.waitForSelector("tr.player-row");

            String html = page.content();

            // Print the top 5 players
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            org.jsoup.select.Elements rows = doc.select("tr.player-row");
            System.out.println("Found " + rows.size() + " rows!");
            if (!rows.isEmpty()) {
                System.out.println("First row HTML:");
                System.out.println(rows.first().outerHtml());
            }
            System.out.println("Done.");
        }
    }
}
