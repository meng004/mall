package com.macro.mall.sdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.GlobalExceptionHandler;
import com.macro.mall.controller.PmsProductAttributeController;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.service.PmsProductAttributeService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SDCE306Test {
    private static final String VALID_JSON = """
            {"productAttributeCategoryId":7,"name":"颜色","selectType":0,"inputType":0,"filterType":0,"searchType":0,"relatedStatus":0,"handAddStatus":0,"type":0}
            """;
    private static final String MISSING_CATEGORY_JSON = """
            {"name":"颜色","selectType":0,"inputType":0,"filterType":0,"searchType":0,"relatedStatus":0,"handAddStatus":0,"type":0}
            """;
    private static final String EMPTY_NAME_JSON = """
            {"productAttributeCategoryId":7,"name":"","selectType":0,"inputType":0,"filterType":0,"searchType":0,"relatedStatus":0,"handAddStatus":0,"type":0}
            """;

    private ValidatorFactory factory;
    private Validator validator;
    private MockMvc mvc;
    private PmsProductAttributeService service;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        PmsProductAttributeController controller = new PmsProductAttributeController();
        service = mock(PmsProductAttributeService.class, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(controller, "productAttributeService", service);
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(validator))
                .build();
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void validCategoryIdDoesNotThrowUnexpectedType() {
        PmsProductAttributeParam validParam = validParam();
        assertDoesNotThrow(() -> validator.validate(validParam));
        assertTrue(validator.validate(validParam).isEmpty());
    }

    @Test
    void createRejectsMissingCategoryBeforeService() throws Exception {
        assertRejected("/productAttribute/create", MISSING_CATEGORY_JSON);
    }

    @Test
    void updateRejectsMissingCategoryBeforeService() throws Exception {
        assertRejected("/productAttribute/update/11", MISSING_CATEGORY_JSON);
    }

    @Test
    void createRejectsEmptyNameBeforeService() throws Exception {
        assertRejected("/productAttribute/create", EMPTY_NAME_JSON);
    }

    @Test
    void updateRejectsEmptyNameBeforeService() throws Exception {
        assertRejected("/productAttribute/update/11", EMPTY_NAME_JSON);
    }

    @Test
    void validCreateReachesService() throws Exception {
        when(service.create(any())).thenReturn(1);
        MvcResult result = postJson("/productAttribute/create", VALID_JSON);
        assertEquals(ResultCode.SUCCESS.getCode(), codeOf(result));
        verify(service).create(any());
    }

    @Test
    void validUpdateReachesService() throws Exception {
        when(service.update(anyLong(), any())).thenReturn(1);
        MvcResult result = postJson("/productAttribute/update/11", VALID_JSON);
        assertEquals(ResultCode.SUCCESS.getCode(), codeOf(result));
        verify(service).update(anyLong(), any());
    }

    @Test
    void createFailureStillComesFromServiceResult() throws Exception {
        when(service.create(any())).thenReturn(0);
        MvcResult result = postJson("/productAttribute/create", VALID_JSON);
        assertEquals(ResultCode.FAILED.getCode(), codeOf(result));
        verify(service).create(any());
    }

    @Test
    void updateFailureStillComesFromServiceResult() throws Exception {
        when(service.update(anyLong(), any())).thenReturn(0);
        MvcResult result = postJson("/productAttribute/update/11", VALID_JSON);
        assertEquals(ResultCode.FAILED.getCode(), codeOf(result));
        verify(service).update(anyLong(), any());
    }

    private void assertRejected(String path, String json) throws Exception {
        MvcResult result = postJson(path, json);
        assertAll(
                () -> verifyNoInteractions(service),
                () -> assertEquals(ResultCode.VALIDATE_FAILED.getCode(), codeOf(result), result.getResponse().getContentAsString())
        );
    }

    private MvcResult postJson(String path, String json) throws Exception {
        return mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    private static PmsProductAttributeParam validParam() {
        PmsProductAttributeParam validParam = new PmsProductAttributeParam();
        validParam.setProductAttributeCategoryId(7L);
        validParam.setName("颜色");
        validParam.setSelectType(0);
        validParam.setInputType(0);
        validParam.setFilterType(0);
        validParam.setSearchType(0);
        validParam.setRelatedStatus(0);
        validParam.setHandAddStatus(0);
        validParam.setType(0);
        return validParam;
    }

    private static long codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("code").asLong();
    }
}
