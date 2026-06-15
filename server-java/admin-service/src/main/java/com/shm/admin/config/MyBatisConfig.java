package com.shm.admin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Admin Service MyBatis 配置
 */
@Configuration
@MapperScan("com.shm.admin.mapper")
@EnableTransactionManagement
public class MyBatisConfig {
}
