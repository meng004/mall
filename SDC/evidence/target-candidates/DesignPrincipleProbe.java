import com.macro.mall.service.impl.PmsProductServiceImpl;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
public class DesignPrincipleProbe {
 public static class FailingDao { public int insertList(List<?> rows) { throw new IllegalStateException("synthetic storage failure"); } }
 public static void main(String[] args) {
  RuntimeException invalidDao = null;
  try { ReflectionTestUtils.invokeMethod(new PmsProductServiceImpl(), "relateAndInsertList", new Object(), List.of(new PmsProductAttributeValue()), 1L); }
  catch (RuntimeException error) { invalidDao=error; }
  if(invalidDao==null || invalidDao.getMessage()==null || !invalidDao.getMessage().contains("insertList")) throw new AssertionError("Unexpected dynamic contract behavior");
  System.out.println("E2-02 OBSERVED: Object parameter accepted, missing insertList contract detected only at runtime");
  RuntimeException observed = null;
  try { ReflectionTestUtils.invokeMethod(new PmsProductServiceImpl(), "relateAndInsertList", new FailingDao(), List.of(new PmsProductAttributeValue()), 1L); }
  catch (RuntimeException error) { observed=error; }
  if(observed==null) throw new AssertionError("Expected storage exception");
  if(observed.getCause()!=null || observed.getMessage()!=null) throw new AssertionError("Observed error context differs; re-evaluate evidence");
  System.out.println("E2-05 OBSERVED: original=IllegalStateException(synthetic storage failure), propagated="+observed.getClass().getSimpleName()+", message=null, cause=null");
  var service=new OmsPortalOrderServiceImpl();
  var members=mock(UmsMemberService.class,withSettings().mockMaker("mock-maker-subclass"));
  var orders=mock(OmsOrderMapper.class,withSettings().mockMaker("mock-maker-subclass"));
  var items=mock(OmsOrderItemMapper.class,withSettings().mockMaker("mock-maker-subclass"));
  ReflectionTestUtils.setField(service,"memberService",members);
  ReflectionTestUtils.setField(service,"orderMapper",orders);
  ReflectionTestUtils.setField(service,"orderItemMapper",items);
  var current=new UmsMember();current.setId(101L);
  when(members.getCurrentMember()).thenReturn(current);
  var other=new OmsOrder();other.setId(2L);other.setMemberId(202L);
  when(orders.selectByPrimaryKey(2L)).thenReturn(other);
  when(items.selectByExample(any())).thenReturn(List.of());
  var result=service.detail(2L);
  if(result.getMemberId()!=202L) throw new AssertionError("Observed ownership differs; re-evaluate evidence");
  verifyNoInteractions(members);
  System.out.println("E2-06 OBSERVED: currentMember=101, detailOwner=202, current-member dependency not consulted");
  System.out.println("Observation checks=3; business principles NOT satisfied; no live database or HTTP authorization tested");
 }
}
