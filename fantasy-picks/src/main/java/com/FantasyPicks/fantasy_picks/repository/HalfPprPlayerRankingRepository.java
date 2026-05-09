package com.FantasyPicks.fantasy_picks.repository;

import com.FantasyPicks.fantasy_picks.entity.HalfPprPlayerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HalfPprPlayerRankingRepository extends JpaRepository<HalfPprPlayerRankingEntity, Long> {
    List<HalfPprPlayerRankingEntity> findByYearOrderByOverallRankAsc(int year);
    void deleteByYear(int year);
}
