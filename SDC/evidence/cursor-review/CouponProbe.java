import java.util.*; import java.lang.reflect.*; import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl; public class CouponProbe {public static void main(String[] args)throws Exception{
Class<?> t=Class.forName("com.macro.mall.sdc.e3.SDCE301Test");var ctor=t.getDeclaredConstructor();ctor.setAccessible(true);Object o=ctor.newInstance();var setup=t.getDeclaredMethod("setUp");setup.setAccessible(true);setup.invoke(o);
var coupon=t.getDeclaredMethod("coupon",long.class,int.class,String.class,Date.class,List.class,List.class);coupon.setAccessible(true);
var cart=t.getDeclaredMethod("cart",String.class,long.class,long.class);cart.setAccessible(true);
var detail=Class.forName("com.macro.mall.portal.domain.SmsCouponHistoryDetail");var histories=t.getDeclaredMethod("historyDaoReturns",Array.newInstance(detail,0).getClass());histories.setAccessible(true);
var field=t.getDeclaredField("service");field.setAccessible(true);var service=(UmsMemberCouponServiceImpl)field.get(o);
for(int type:new int[]{1,2}){Object arr=Array.newInstance(detail,1);Array.set(arr,0,coupon.invoke(null,1L,type,"0.50",new Date(4102444800000L),List.of(100L),List.of(10L)));histories.invoke(o,arr);System.out.println("useType="+type+", matched=0.90,minPoint=0.50,available="+service.listCart((List)cart.invoke(null,"0.90",10L,100L),1).size()+",expected=1");}
}}
