package com.cabpooling.employeecabpooling;

import com.cabpooling.employeecabpooling.config.RoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RoutingProperties.class)
public class EmployeeCabPoolingApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmployeeCabPoolingApplication.class, args);
	}

}
