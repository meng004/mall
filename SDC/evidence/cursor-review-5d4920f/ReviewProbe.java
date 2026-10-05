package com.macro.mall.sdc.e7;
import java.nio.file.*;
import java.util.*;
import com.macro.mall.portal.llm.CursorCliLlmClient;
public class ReviewProbe {
 public static void main(String[] args) throws Exception {
  String online=EvaluationSupport.codeOf(EvaluationSupport.decision(false,false,true,19,19,true));
  String helper=EvaluationSupport.verdict(19,19,12,12,true,false);
  System.out.println("pending-normal online="+online+" helper="+helper);
  var score=EvaluationSupport.Score.fail("transport","传输","timeout");
  var row=new LinkedHashMap<String,Object>();row.put("implementation","llm");row.put("passed",score.passed());row.put("scored",score.scored());row.put("errorCategory",score.category());
  System.out.println("online-timeout tally="+EvaluationSupport.tallyLlm(List.of(row)));
  Path log=Files.createTempFile("sdc-synthetic-stderr-",".txt");
  try {
   String sentinel="DUMMY_REVIEW_ONLY_83";Files.writeString(log,"password="+sentinel+" token="+sentinel);
   var m=CursorCliLlmClient.class.getDeclaredMethod("stderrSummary",Path.class);m.setAccessible(true);
   String out=(String)m.invoke(null,log);System.out.println("synthetic-secret-value-retained="+out.contains(sentinel));
  } finally {Files.delete(log);}
 }
}
