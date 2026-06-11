package com.realteeth.image_processing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImageProcessingApplicationTests {

	@Test
	void contextLoads() {
	}

}
