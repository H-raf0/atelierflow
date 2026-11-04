package dev.atelierflow;

import org.springframework.boot.SpringApplication;

public class TestAtelierflowApplication {

	public static void main(String[] args) {
		SpringApplication.from(AtelierflowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
