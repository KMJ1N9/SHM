package com.shm.core.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis 配置
 * <p>
 * {@code mapUnderscoreToCamelCase=true} 已在 application.yml 中配置，
 * 使数据库 snake_case 字段自动映射到 Java camelCase 属性。
 */
@Configuration
@MapperScan("com.shm.core.mapper")
@EnableTransactionManagement
public class MyBatisConfig {
}
