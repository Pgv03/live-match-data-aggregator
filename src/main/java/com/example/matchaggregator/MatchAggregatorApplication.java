package com.example.matchaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MatchAggregatorApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // stored timestamps must not depend on the host's zone
		SpringApplication.run(MatchAggregatorApplication.class, args);
	}
}
