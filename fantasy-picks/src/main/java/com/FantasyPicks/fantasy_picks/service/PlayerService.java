package com.FantasyPicks.fantasy_picks.service;

import com.FantasyPicks.fantasy_picks.entity.BasePlayerRankingEntity;
import com.FantasyPicks.fantasy_picks.entity.HalfPprPlayerRankingEntity;
import com.FantasyPicks.fantasy_picks.entity.PprPlayerRankingEntity;
import com.FantasyPicks.fantasy_picks.entity.ScrapeMetadataEntity;
import com.FantasyPicks.fantasy_picks.entity.StandardPlayerRankingEntity;
import com.FantasyPicks.fantasy_picks.model.PlayerApiResponse;
import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.FantasyPicks.fantasy_picks.normalization.PlayerNormalizer;
import com.FantasyPicks.fantasy_picks.repository.HalfPprPlayerRankingRepository;
import com.FantasyPicks.fantasy_picks.repository.PprPlayerRankingRepository;
import com.FantasyPicks.fantasy_picks.repository.ScrapeMetadataRepository;
import com.FantasyPicks.fantasy_picks.repository.StandardPlayerRankingRepository;
import com.FantasyPicks.fantasy_picks.scraper.RankingScraper;
import com.FantasyPicks.fantasy_picks.scraper.ScrapingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final Map<String, RankingScraper> scraperMap;
    private final PlayerNormalizer normalizer;
    
    private final PprPlayerRankingRepository pprRepo;
    private final HalfPprPlayerRankingRepository halfPprRepo;
    private final StandardPlayerRankingRepository standardRepo;
    private final ScrapeMetadataRepository metadataRepo;

    public PlayerService(List<RankingScraper> scrapers, PlayerNormalizer normalizer,
                         PprPlayerRankingRepository pprRepo,
                         HalfPprPlayerRankingRepository halfPprRepo,
                         StandardPlayerRankingRepository standardRepo,
                         ScrapeMetadataRepository metadataRepo) {
        this.normalizer = normalizer;
        this.pprRepo = pprRepo;
        this.halfPprRepo = halfPprRepo;
        this.standardRepo = standardRepo;
        this.metadataRepo = metadataRepo;
        
        this.scraperMap = new LinkedHashMap<>();
        for (RankingScraper scraper : scrapers) {
            scraperMap.put(scraper.getSourceId(), scraper);
            log.info("Registered scraper: {} ({})", scraper.getSourceId(), scraper.getSourceName());
        }
    }

    public Set<String> getAvailableSourceIds() {
        return scraperMap.keySet();
    }

    @Transactional
    public PlayerApiResponse getPlayerRankings(String requestedSources, int year, String leagueType, boolean refresh) {
        Set<String> sourceIds = Arrays.stream(requestedSources.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("Fetching rankings: sources={}, year={}, leagueType={}, refresh={}", sourceIds, year, leagueType, refresh);

        Optional<ScrapeMetadataEntity> metadataOpt = metadataRepo.findByYearAndLeagueType(year, leagueType);
        boolean shouldScrape = false;
        int currentYear = java.time.Year.now().getValue();
        
        if (metadataOpt.isEmpty()) {
            shouldScrape = true;
        } else {
            ScrapeMetadataEntity metadata = metadataOpt.get();
            LocalDateTime lastUpdated = metadata.getLastUpdated();
            long minutesSinceUpdate = ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now());
            long hoursSinceUpdate = ChronoUnit.HOURS.between(lastUpdated, LocalDateTime.now());

            if (year < currentYear) {
                log.info("Requested year is in the past. Always returning DB data.");
                shouldScrape = false;
            } else {
                if (refresh) {
                    shouldScrape = true;
                } else {
                    if (hoursSinceUpdate >= 24) {
                        shouldScrape = true;
                    }
                }
            }
        }

        List<PlayerRanking> players;
        boolean updateSuccess = false;
        
        if (shouldScrape) {
            log.info("Scraping fresh data...");
            players = scrapeAndMerge(sourceIds, year, leagueType);
            if (!players.isEmpty()) {
                saveToDatabase(players, year, leagueType);
                updateMetadata(year, leagueType);
                updateSuccess = true;
            } else {
                log.error("Scraping returned no data! Using old data from DB.");
                players = loadFromDatabase(year, leagueType, sourceIds);
            }
        } else {
            log.info("Loading data from database...");
            players = loadFromDatabase(year, leagueType, sourceIds);
            if (players.isEmpty()) {
                log.info("DB is empty, fallback to scraping...");
                players = scrapeAndMerge(sourceIds, year, leagueType);
                if (!players.isEmpty()) {
                    saveToDatabase(players, year, leagueType);
                    updateMetadata(year, leagueType);
                    updateSuccess = true;
                }
            }
        }
        
        computeOverallRanks(players, sourceIds);
        players.sort(Comparator.comparingInt(PlayerRanking::getOverallRank));
        
        PlayerApiResponse response = new PlayerApiResponse(players);
        metadataOpt = metadataRepo.findByYearAndLeagueType(year, leagueType);
        if (metadataOpt.isPresent()) {
            // Send ISO string for JS
            response.setLastUpdated(metadataOpt.get().getLastUpdated().toInstant(ZoneOffset.UTC).toString());
        }
        
        return response;
    }

    private List<PlayerRanking> scrapeAndMerge(Set<String> sourceIds, int year, String leagueType) {
        Map<String, PlayerRanking> mergedPlayers = new LinkedHashMap<>();

        for (String sourceId : sourceIds) {
            RankingScraper scraper = scraperMap.get(sourceId);
            if (scraper == null) {
                log.warn("Unknown source requested: '{}' — skipping", sourceId);
                continue;
            }

            try {
                List<PlayerRanking> scraped = scraper.scrapeRankings(year, leagueType);
                log.info("Source '{}' returned {} players", sourceId, scraped.size());

                for (PlayerRanking player : scraped) {
                    player.setTeam(normalizer.normalizeTeam(player.getTeam()));
                    String key = normalizer.buildNormalizedKey(player.getName(), player.getTeam());
                    PlayerRanking existing = mergedPlayers.get(key);

                    if (existing == null) {
                        mergedPlayers.put(key, player);
                    } else {
                        existing.getRankings().putAll(player.getRankings());
                    }
                }
            } catch (ScrapingException e) {
                log.error("Failed to scrape from '{}': {}", sourceId, e.getMessage());
            }
        }
        return new ArrayList<>(mergedPlayers.values());
    }

    private List<PlayerRanking> loadFromDatabase(int year, String leagueType, Set<String> sourceIds) {
        List<? extends BasePlayerRankingEntity> entities;
        if ("ppr".equalsIgnoreCase(leagueType)) {
            entities = pprRepo.findByYearOrderByOverallRankAsc(year);
        } else if ("half_ppr".equalsIgnoreCase(leagueType)) {
            entities = halfPprRepo.findByYearOrderByOverallRankAsc(year);
        } else {
            entities = standardRepo.findByYearOrderByOverallRankAsc(year);
        }

        return entities.stream().map(e -> {
            PlayerRanking p = new PlayerRanking();
            p.setName(e.getName());
            p.setTeam(e.getTeam());
            p.setPosition(e.getPosition());
            p.setOverallRank(e.getOverallRank());

            Map<String, Integer> rankings = new HashMap<>();
            for (String sourceId : sourceIds) {
                Integer rank = e.getRankings().get(sourceId);
                if (rank != null) {
                    rankings.put(sourceId, rank);
                }
            }
            p.setRankings(rankings);
            return p;
        }).collect(Collectors.toList());
    }

    private void saveToDatabase(List<PlayerRanking> players, int year, String leagueType) {
        if ("ppr".equalsIgnoreCase(leagueType)) {
            pprRepo.deleteByYear(year);
            List<PprPlayerRankingEntity> entities = players.stream().map(p -> {
                PprPlayerRankingEntity e = new PprPlayerRankingEntity();
                mapToEntity(p, e, year);
                return e;
            }).collect(Collectors.toList());
            pprRepo.saveAll(entities);
        } else if ("half_ppr".equalsIgnoreCase(leagueType)) {
            halfPprRepo.deleteByYear(year);
            List<HalfPprPlayerRankingEntity> entities = players.stream().map(p -> {
                HalfPprPlayerRankingEntity e = new HalfPprPlayerRankingEntity();
                mapToEntity(p, e, year);
                return e;
            }).collect(Collectors.toList());
            halfPprRepo.saveAll(entities);
        } else {
            standardRepo.deleteByYear(year);
            List<StandardPlayerRankingEntity> entities = players.stream().map(p -> {
                StandardPlayerRankingEntity e = new StandardPlayerRankingEntity();
                mapToEntity(p, e, year);
                return e;
            }).collect(Collectors.toList());
            standardRepo.saveAll(entities);
        }
    }

    private void mapToEntity(PlayerRanking dto, BasePlayerRankingEntity entity, int year) {
        entity.setName(dto.getName());
        entity.setPosition(dto.getPosition());
        entity.setTeam(dto.getTeam());
        entity.setYear(year);
        entity.setOverallRank(dto.getOverallRank());
        entity.setRankings(new HashMap<>(dto.getRankings()));
    }

    private void updateMetadata(int year, String leagueType) {
        ScrapeMetadataEntity metadata = metadataRepo.findByYearAndLeagueType(year, leagueType)
                .orElseGet(() -> {
                    ScrapeMetadataEntity m = new ScrapeMetadataEntity();
                    m.setYear(year);
                    m.setLeagueType(leagueType);
                    return m;
                });
        metadata.setLastUpdated(LocalDateTime.now());
        metadataRepo.save(metadata);
    }

    private void computeOverallRanks(List<PlayerRanking> players, Set<String> sourceIds) {
        List<PlayerWithScore> scored = new ArrayList<>();
        for (PlayerRanking player : players) {
            double totalRank = 0;
            int sourceCount = 0;

            for (String sourceId : sourceIds) {
                Integer rank = player.getRankings().get(sourceId);
                if (rank != null) {
                    totalRank += rank;
                    sourceCount++;
                }
            }

            double avgRank = sourceCount > 0 ? totalRank / sourceCount : 9999.0;
            scored.add(new PlayerWithScore(player, avgRank));
        }

        scored.sort(Comparator.comparingDouble(s -> s.avgRank));
        for (int i = 0; i < scored.size(); i++) {
            scored.get(i).player.setOverallRank(i + 1);
        }
    }

    private static class PlayerWithScore {
        final PlayerRanking player;
        final double avgRank;

        PlayerWithScore(PlayerRanking player, double avgRank) {
            this.player = player;
            this.avgRank = avgRank;
        }
    }
}
