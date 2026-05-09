package com.FantasyPicks.fantasy_picks.repository;

import com.FantasyPicks.fantasy_picks.entity.StandardPlayerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StandardPlayerRankingRepository extends JpaRepository<StandardPlayerRankingEntity, Long> {
    List<StandardPlayerRankingEntity> findByYearOrderByOverallRankAsc(int year);
    void deleteByYear(int year);
}
