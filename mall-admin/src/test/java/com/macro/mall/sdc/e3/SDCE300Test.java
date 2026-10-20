package com.macro.mall.sdc.e3;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.GlobalExceptionHandler;
import com.macro.mall.controller.UmsAdminController;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.security.util.JwtTokenUtil;
import com.macro.mall.security.util.SpringUtil;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.impl.UmsAdminServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * E3-00 规格：管理员登录失败时，用户名不存在与密码错误必须同一响应码、同一文案（文案取自服务第 272 行），
 * 调用方不能据此区分账号是否存在；账号禁用单列；改密三种失败的对外响应保持不变。
 * 经真实 Controller、Service 与全局异常处理器出站，只替换 Mapper、缓存与令牌工具。
 */
class SDCE300Test {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private final UmsAdminServiceImpl service = new UmsAdminServiceImpl();
    private final UmsAdminMapper adminMapper = mock(UmsAdminMapper.class);
    private final UmsAdminCacheService cache = mock(UmsAdminCacheService.class);
    private final UmsAdminLoginLogMapper loginLogMapper = mock(UmsAdminLoginLogMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "adminMapper", adminMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", ENCODER);
        ReflectionTestUtils.setField(service, "loginLogMapper", loginLogMapper);
        UmsAdminRoleRelationDao relationDao = mock(UmsAdminRoleRelationDao.class);
        when(relationDao.getResourceList(anyLong())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "adminRoleRelationDao", relationDao);
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        when(jwt.generateToken(any())).thenReturn("t0k3n");
        ReflectionTestUtils.setField(service, "jwtTokenUtil", jwt);
        // 服务经静态 SpringUtil 取缓存服务；它的上下文只允许设一次，测试只能直接改静态字段
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(UmsAdminCacheService.class)).thenReturn(cache);
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", context);

        UmsAdminController controller = new UmsAdminController();
        ReflectionTestUtils.setField(controller, "adminService", service);
        ReflectionTestUtils.setField(controller, "tokenHead", "Bearer ");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
        SecurityContextHolder.clearContext();
    }

    private void existing(String username, String password, int status) {
        UmsAdmin admin = new UmsAdmin();
        admin.setId(7L);
        admin.setUsername(username);
        admin.setPassword(ENCODER.encode(password));
        admin.setStatus(status);
        when(adminMapper.selectByExample(any())).thenReturn(List.of(admin));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions updatePassword(String username, String oldPassword) throws Exception {
        return mvc.perform(post("/admin/updatePassword").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"n3w\"}"));
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() throws Exception {
        when(adminMapper.selectByExample(any())).thenReturn(List.of());
        login("ghost", "any").andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.message").value("用户名或密码错误"));

        existing("admin", "right", 1);
        login("admin", "wrong").andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.message").value("用户名或密码错误"));
        verifyNoInteractions(loginLogMapper);
    }

    @Test
    void disabledAccountIsReportedSeparately() throws Exception {
        existing("admin", "right", 0);
        login("admin", "right").andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.message").value("帐号已被禁用"));
        verifyNoInteractions(loginLogMapper);
    }

    @Test
    void successfulLoginReturnsTokenAndWritesLog() throws Exception {
        existing("admin", "right", 1);
        login("admin", "right").andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.token").value("t0k3n"));
        verify(loginLogMapper).insert(any());
    }

    @Test
    void passwordUpdateFailuresKeepTheirResponses() throws Exception {
        when(adminMapper.selectByExample(any())).thenReturn(List.of());
        updatePassword("ghost", "old").andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.message").value("找不到该用户"));

        existing("admin", "old", 1);
        updatePassword("admin", "bad").andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.message").value("旧密码错误"));
        verify(adminMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void passwordUpdateSucceeds() throws Exception {
        existing("admin", "old", 1);
        updatePassword("admin", "old").andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(1));
        verify(adminMapper).updateByPrimaryKey(argThat(a -> ENCODER.matches("n3w", a.getPassword())));
        verify(cache).delAdmin(7L);
    }

    @Test
    void blankPasswordUpdateIsRejectedByTheService() {
        // 控制器上的 @NotEmpty 会先拦住空值；服务被其他调用方直接调用时仍须拒绝
        UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
        param.setUsername("admin");
        try {
            int status = service.updatePassword(param);
            assertEquals(-1, status, "改前以返回码表示");
        } catch (ApiException e) {
            assertEquals("提交参数不合法", e.getMessage());
        }
        verify(adminMapper, never()).updateByPrimaryKey(any());
    }
}
