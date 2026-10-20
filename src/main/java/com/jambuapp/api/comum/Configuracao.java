package com.jambuapp.api.comum;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(JambuProperties.class)
public class Configuracao {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
