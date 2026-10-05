import java.io.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import com.macro.mall.model.PmsProductAttributeExample;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import com.macro.mall.service.impl.PmsProductAttributeServiceImpl;
import com.macro.mall.dto.PmsProductAttributeParam;
public class SpecProbe { public static void main(String[] args) throws Exception {
Configuration c=new Configuration(); String path=args[0]+"/mall-mbg/src/main/resources/com/macro/mall/mapper/PmsProductAttributeMapper.xml";
try(InputStream in=new FileInputStream(path)){new XMLMapperBuilder(in,c,path,c.getSqlFragments()).parse();}
PmsProductAttributeExample e=new PmsProductAttributeExample();e.createCriteria().andIdIn(List.of());
System.out.println("ALL_MISSING_SQL="+c.getMappedStatement("com.macro.mall.mapper.PmsProductAttributeMapper.deleteByExample").getBoundSql(e).getSql().replaceAll("\\s+"," "));
var method=PmsProductAttributeServiceImpl.class.getMethod("update",Long.class,PmsProductAttributeParam.class);
System.out.println("UPDATE_TRANSACTION_ATTRIBUTE="+new AnnotationTransactionAttributeSource().getTransactionAttribute(method,PmsProductAttributeServiceImpl.class));
}}
