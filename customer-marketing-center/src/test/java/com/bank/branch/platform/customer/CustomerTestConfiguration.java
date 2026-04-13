package com.bank.branch.platform.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.customer")
@MapperScan("com.bank.branch.platform.customer.mapper")
public class CustomerTestConfiguration {
}
