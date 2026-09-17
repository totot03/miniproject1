package com.pharmaprice;

import com.pharmaprice.auth.security.JwtProperties;
import com.pharmaprice.common.config.RecommendationProperties;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RecommendationProperties.class, JwtProperties.class})
public class MiniProject1BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniProject1BackendApplication.class, args);
	}

	@PostConstruct
	void initTimezone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

}
