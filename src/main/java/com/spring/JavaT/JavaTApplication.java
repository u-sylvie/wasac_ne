package com.spring.JavaT;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.spring.JavaT.security.JwtProperties;
import com.spring.JavaT.security.SecurityProperties;
import com.spring.JavaT.notification.MailProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class, MailProperties.class})
public class JavaTApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavaTApplication.class, args);
	}

}
