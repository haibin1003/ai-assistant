package com.ai.assistant.application.skill;

import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.skill.dto.CreateSkillRequest;
import com.ai.assistant.application.skill.dto.SkillDTO;
import com.ai.assistant.application.skill.dto.UpdateSkillRequest;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.Skill;
import com.ai.assistant.domain.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SkillService skillService;

    private UserContextDTO adminContext;
    private UserContextDTO userContext;
    private Skill testSkill;

    @BeforeEach
    void setUp() {
        adminContext = UserContextDTO.builder()
            .sessionId("admin-session")
            .userId("admin-001")
            .username("admin")
            .roles(List.of("ADMIN"))
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        userContext = UserContextDTO.builder()
            .sessionId("user-session")
            .userId("user-001")
            .username("testuser")
            .roles(List.of("USER"))
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        testSkill = Skill.builder()
            .skillId("test-skill-001")
            .name("测试技能")
            .description("测试技能描述")
            .promptTemplate("帮助用户完成{task}")
            .isGlobal(false)
            .isActive(true)
            .createdBy("user-001")
            .build();
    }

    @Test
    @DisplayName("创建私有技能 - 成功")
    void createSkill_private_success() {
        CreateSkillRequest request = CreateSkillRequest.builder()
            .name("我的技能")
            .description("私有技能")
            .promptTemplate("执行任务")
            .isGlobal(false)
            .build();

        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            skill.setCreatedAt(LocalDateTime.now());
            return skill;
        });

        SkillDTO result = skillService.createSkill(request, userContext);

        assertNotNull(result);
        assertEquals("我的技能", result.getName());
        assertFalse(result.getIsGlobal());
    }

    @Test
    @DisplayName("创建全局技能 - 非管理员")
    void createSkill_global_notAdmin() {
        CreateSkillRequest request = CreateSkillRequest.builder()
            .name("全局技能")
            .description("全局技能")
            .promptTemplate("执行任务")
            .isGlobal(true)
            .build();

        assertThrows(BizException.class, () -> {
            skillService.createSkill(request, userContext);
        });
    }

    @Test
    @DisplayName("创建全局技能 - 管理员")
    void createSkill_global_admin() {
        CreateSkillRequest request = CreateSkillRequest.builder()
            .name("全局技能")
            .description("全局技能")
            .promptTemplate("执行任务")
            .isGlobal(true)
            .build();

        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            skill.setCreatedAt(LocalDateTime.now());
            return skill;
        });

        SkillDTO result = skillService.createSkill(request, adminContext);

        assertNotNull(result);
        assertTrue(result.getIsGlobal());
    }

    @Test
    @DisplayName("更新技能 - 成功")
    void updateSkill_success() {
        UpdateSkillRequest request = UpdateSkillRequest.builder()
            .name("更新后的名称")
            .build();

        when(skillRepository.findBySkillId("test-skill-001")).thenReturn(Optional.of(testSkill));
        when(skillRepository.save(any(Skill.class))).thenReturn(testSkill);

        SkillDTO result = skillService.updateSkill("test-skill-001", request, userContext);

        assertNotNull(result);
    }

    @Test
    @DisplayName("更新技能 - 无权限")
    void updateSkill_noPermission() {
        UpdateSkillRequest request = UpdateSkillRequest.builder()
            .name("更新后的名称")
            .build();

        // 创建一个属于其他用户的技能
        Skill otherUserSkill = Skill.builder()
            .skillId("other-skill")
            .name("其他用户技能")
            .createdBy("other-user")
            .isGlobal(false)
            .build();

        when(skillRepository.findBySkillId("other-skill")).thenReturn(Optional.of(otherUserSkill));

        assertThrows(BizException.class, () -> {
            skillService.updateSkill("other-skill", request, userContext);
        });
    }

    @Test
    @DisplayName("删除技能 - 成功")
    void deleteSkill_success() {
        when(skillRepository.findBySkillId("test-skill-001")).thenReturn(Optional.of(testSkill));

        skillService.deleteSkill("test-skill-001", userContext);

        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    @DisplayName("获取可用技能列表")
    void getAvailableSkills() {
        Skill globalSkill = Skill.builder()
            .skillId("global-skill")
            .name("全局技能")
            .isGlobal(true)
            .isActive(true)
            .build();

        when(skillRepository.findByIsGlobalTrueAndIsActiveTrue())
            .thenReturn(List.of(globalSkill));
        when(skillRepository.findByCreatedByAndIsActiveTrue("user-001"))
            .thenReturn(List.of(testSkill));

        List<SkillDTO> result = skillService.getAvailableSkills(userContext);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("获取用户技能列表")
    void getUserSkills() {
        when(skillRepository.findByCreatedByAndIsActiveTrue("user-001"))
            .thenReturn(List.of(testSkill));

        List<SkillDTO> result = skillService.getUserSkills("user-001");

        assertEquals(1, result.size());
        assertEquals("测试技能", result.get(0).getName());
    }

    @Test
    @DisplayName("获取全局技能列表")
    void getGlobalSkills() {
        Skill globalSkill = Skill.builder()
            .skillId("global-skill")
            .name("全局技能")
            .isGlobal(true)
            .isActive(true)
            .build();

        when(skillRepository.findByIsGlobalTrueAndIsActiveTrue())
            .thenReturn(List.of(globalSkill));

        List<SkillDTO> result = skillService.getGlobalSkills();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsGlobal());
    }
}
