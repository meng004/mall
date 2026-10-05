import com.macro.mall.dto.PmsProductAttributeParam;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Teacher reproduction: expected to fail on the documented unmodified source. */
class CandidateAttributeValidationTest {
    @Test
    void aPresentCategoryIdCanBeValidated() {
        var param = new PmsProductAttributeParam();
        param.setProductAttributeCategoryId(101L);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertDoesNotThrow(() -> factory.getValidator()
                    .validateProperty(param, "productAttributeCategoryId"));
        }
    }
}
