package com.shm.core.repository;

import com.shm.common.model.entity.User;
import com.shm.core.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRepository / UserMapper 集成测试（Phase 11.2.1）
 *
 * <p>使用 @MybatisTest 只加载 MyBatis 层，避免触发 Nacos/Feign/Sentinel 自动配置。
 * 连接真实 MySQL 数据库（配置在 application-test.yml），@Transactional 保证测试数据自动回滚。
 */
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @Order(1)
    @Transactional
    void shouldInsertAndFindUser() {
        User user = buildUser("13800001111", "集成测试用户");
        int inserted = userMapper.insert(user);
        assertEquals(1, inserted);
        assertNotNull(user.getId());

        User found = userMapper.findById(user.getId());
        assertNotNull(found);
        assertEquals("13800001111", found.getPhone());
        assertEquals("集成测试用户", found.getNickname());
    }

    @Test
    @Order(2)
    @Transactional
    void shouldFindByPhone() {
        User user = buildUser("13800002222", "PhoneTest");
        userMapper.insert(user);

        User found = userMapper.findByPhone("13800002222");
        assertNotNull(found);
        assertEquals("13800002222", found.getPhone());
    }

    @Test
    @Order(3)
    @Transactional
    void shouldUpdateCreditScore() {
        User user = buildUser("13800003333", "CreditTest");
        userMapper.insert(user);

        int updated = userMapper.updateCreditScore(user.getId(), -10, 200);
        assertEquals(1, updated);

        User found = userMapper.findById(user.getId());
        assertEquals(90, found.getCreditScore());
    }

    @Test
    @Order(4)
    @Transactional
    void shouldUpdateStatus() {
        User user = buildUser("13800004444", "StatusTest");
        userMapper.insert(user);

        int updated = userMapper.updateStatus(user.getId(), "banned");
        assertEquals(1, updated);

        User found = userMapper.findById(user.getId());
        assertEquals("banned", found.getStatus());
    }

    @Test
    @Order(5)
    @Transactional
    void shouldListWithFiltersAndPagination() {
        userMapper.insert(buildUser("13800005551", "ListA"));
        userMapper.insert(buildUser("13800005552", "ListB"));

        List<User> users = userMapper.listWithFilters(null, null, null, 0, 10);
        assertNotNull(users);
        assertTrue(users.size() >= 2);
    }

    @Test
    @Order(6)
    @Transactional
    void shouldCountWithFilters() {
        userMapper.insert(buildUser("13800006661", "CountTest"));

        long count = userMapper.countWithFilters(null, null, null);
        assertTrue(count >= 1);
    }

    @Test
    @Order(7)
    @Transactional
    void shouldReturnNullForNonExistentUser() {
        User found = userMapper.findById(99999999L);
        assertNull(found);
    }

    // ---- helper ----

    private User buildUser(String phone, String nickname) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname(nickname);
        user.setAvatar("");
        user.setClassName("CS2024");
        user.setDormBuilding("北区");
        user.setRole("user");
        user.setCreditScore(100);
        user.setStatus("active");
        user.setTokenVersion(0);
        return user;
    }
}
