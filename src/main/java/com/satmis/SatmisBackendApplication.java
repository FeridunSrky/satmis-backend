package com.satmis;

import com.satmis.config.RequiredEnvironmentInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SatmisBackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(SatmisBackendApplication.class);
		application.addInitializers(new RequiredEnvironmentInitializer());
		application.run(args);
	}

}
