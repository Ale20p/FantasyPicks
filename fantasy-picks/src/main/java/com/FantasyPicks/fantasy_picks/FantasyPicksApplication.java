package com.FantasyPicks.fantasy_picks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication
public class FantasyPicksApplication {

	public static void main(String[] args) {
		SpringApplication.run(FantasyPicksApplication.class, args);
	}

}
