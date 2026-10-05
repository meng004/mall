import java.math.BigDecimal;
import java.util.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
public class CouponTargetProbe {
 public static void main(String[] args) {
  var service = new UmsMemberCouponServiceImpl();
  var members=mock(UmsMemberService.class,withSettings().mockMaker("mock-maker-subclass"));
  var dao=mock(SmsCouponHistoryDao.class,withSettings().mockMaker("mock-maker-subclass"));
  var member=new UmsMember(); member.setId(1L);
  when(members.getCurrentMember()).thenReturn(member);
  ReflectionTestUtils.setField(service,"memberService",members);
  ReflectionTestUtils.setField(service,"couponHistoryDao",dao);
  var coupon=new SmsCoupon(); coupon.setUseType(0); coupon.setMinPoint(new BigDecimal("100.00")); coupon.setEndTime(new Date(System.currentTimeMillis()+86400000L));
  var history=new SmsCouponHistoryDetail(); history.setCoupon(coupon);
  when(dao.getDetailList(1L)).thenReturn(List.of(history));
  for(String amount:List.of("98.90","99.90","100.00")) {
   var item=new CartPromotionItem();item.setPrice(new BigDecimal(amount));item.setReduceAmount(BigDecimal.ZERO);item.setQuantity(1);
   int available=service.listCart(List.of(item),1).size();
   System.out.println("amount="+amount+" minimum=100.00 available="+available);
   if (available != (amount.equals("98.90") ? 0 : 1)) throw new AssertionError("Unexpected source behavior");
  }
 }
}
