package com.hyunprojects.finpulse

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableScheduling
class FinpulseApplication

fun main(args: Array<String>) {
	runApplication<FinpulseApplication>(*args)
}
