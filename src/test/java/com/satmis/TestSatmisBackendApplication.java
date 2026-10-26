package com.satmis;

import org.springframework.boot.SpringApplication;

public class TestSatmisBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(SatmisBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
