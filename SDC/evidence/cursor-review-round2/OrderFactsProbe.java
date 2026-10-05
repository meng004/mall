import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.sdc.e7.CandidateE701Evaluation;
import java.lang.reflect.Proxy;
import java.util.List;

/** Synthetic fixture only: exercise current readiness logic without a DB or model. */
public class OrderFactsProbe {
    public static void main(String[] args) throws Exception {
        var facts = new ObjectMapper().readTree("""
            {"memberScope":{"currentMemberId":101},"orders":[
              {"id":1,"memberId":101,"statusName":"待发货","deleted":false},
              {"id":2,"memberId":101,"statusName":"待发货","deleted":false}]}
            """);
        OmsOrderDetail wrong = new OmsOrderDetail();
        wrong.setId(1L);
        wrong.setMemberId(101L);
        wrong.setStatus(4);
        CommonPage<OmsOrderDetail> incomplete = new CommonPage<>();
        incomplete.setList(List.of(wrong));
        incomplete.setTotal(1L);
        var orders = (OmsPortalOrderService) Proxy.newProxyInstance(
            OmsPortalOrderService.class.getClassLoader(), new Class<?>[]{OmsPortalOrderService.class},
            (p, method, values) -> {
                if (method.getName().equals("list")) return incomplete;
                throw new AssertionError("Unexpected call " + method.getName());
            });
        var ready = CandidateE701Evaluation.class.getDeclaredMethod(
            "teachingOrdersReady", OmsPortalOrderService.class, com.fasterxml.jackson.databind.JsonNode.class);
        ready.setAccessible(true);
        System.out.println("facts_orders=2, actual_orders=1, actual_status=4, ready=" + ready.invoke(null, orders, facts));
    }
}
