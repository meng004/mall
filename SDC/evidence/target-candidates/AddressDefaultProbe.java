import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.*;

/** Read-only service observation with mocked persistence; not a production fix. */
public class AddressDefaultProbe {
    public static void main(String[] args) {
        var service = new UmsMemberReceiveAddressServiceImpl();
        var members = mock(UmsMemberService.class, withSettings().mockMaker("mock-maker-subclass"));
        var mapper = mock(UmsMemberReceiveAddressMapper.class, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, "memberService", members);
        ReflectionTestUtils.setField(service, "addressMapper", mapper);
        var current = new UmsMember();
        current.setId(101L);
        when(members.getCurrentMember()).thenReturn(current);
        when(mapper.selectByExample(any())).thenReturn(List.of());
        List<String> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            UmsMemberReceiveAddress value = invocation.getArgument(0);
            UmsMemberReceiveAddressExample query = invocation.getArgument(1);
            var criteria = query.getOredCriteria().get(0).getAllCriteria();
            String conditions = criteria.stream()
                    .map(c -> c.getCondition() + " " + c.getValue())
                    .reduce((a, b) -> a + " AND " + b).orElse("");
            writes.add("set default=" + value.getDefaultStatus() + " WHERE " + conditions);
            // Existing default row matches the reset; target ID 999 does not exist.
            return value.getDefaultStatus() == 0 ? 1 : 0;
        });
        var request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(1);
        int result = service.update(999L, request);
        if (result != 0 || writes.size() != 2
                || !writes.get(0).contains("member_id = 101")
                || !writes.get(0).contains("default_status = 1")
                || !writes.get(1).contains("id = 999")) {
            throw new AssertionError("Observed behavior changed; re-evaluate E3-07");
        }
        System.out.println("E3-07 OBSERVED: target ID 999 absent, update returns 0 after reset of current member defaults");
        writes.forEach(System.out::println);
        System.out.println("Observation matched; service invocation only, mocked database, no transaction/HTTP/concurrency claim");
    }
}
