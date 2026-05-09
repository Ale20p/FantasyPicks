package com.FantasyPicks.fantasy_picks.repository;

import com.FantasyPicks.fantasy_picks.entity.ScrapeMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScrapeMetadataRepository extends JpaRepository<ScrapeMetadataEntity, Long> {
    Optional<ScrapeMetadataEntity> findByYearAndLeagueType(int year, String leagueType);
}
