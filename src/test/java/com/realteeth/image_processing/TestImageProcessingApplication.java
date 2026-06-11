package com.realteeth.image_processing;

import org.springframework.boot.SpringApplication;

public class TestImageProcessingApplication {

	public static void main(String[] args) {
		SpringApplication.from(ImageProcessingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
