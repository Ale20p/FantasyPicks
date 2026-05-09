package com.FantasyPicks.fantasy_picks.repository;

import com.FantasyPicks.fantasy_picks.entity.PprPlayerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PprPlayerRankingRepository extends JpaRepository<PprPlayerRankingEntity, Long> {
    List<PprPlayerRankingEntity> findByYearOrderByOverallRankAsc(int year);
    void deleteByYear(int year);
}
