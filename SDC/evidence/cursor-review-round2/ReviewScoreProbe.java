package com.macro.mall.sdc.e7;
import java.util.*;import java.math.*;import com.macro.mall.portal.ai.reasons.*;import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.*;import com.macro.mall.portal.ai.comments.*;
public class ReviewScoreProbe {public static void main(String[] args){
var counts=new EnumMap<Label,Integer>(Label.class);var ratios=new EnumMap<Label,BigDecimal>(Label.class);for(var l:Label.values()){counts.put(l,l==Label.LOGISTICS?1:0);ratios.put(l,l==Label.LOGISTICS?BigDecimal.ONE:BigDecimal.ZERO);}
var corrupt=new Result(State.OK,List.of(new Classified("a",Label.QUALITY,"质量问题",false)),counts,ratios,1,0);
System.out.println("WRONG_COUNTS "+CandidateE706Evaluation.score(List.of(new Item("a","鞋","质量问题","")),"OK",Map.of("a","QUALITY"),1,0,corrupt));
var c=new CommentTopicService.Result(CommentTopicService.State.OK,Map.of(CommentTopicService.Topic.QUALITY,"客服确认缝线断开"));
System.out.println("VALID_SEMANTIC "+CandidateE704Evaluation.score("客服确认缝线断开","OK",Set.of(CommentTopicService.Topic.QUALITY),c));
var batch=List.of(new Item("a","鞋","不是质量问题，是物流问题",""));var actual=ExistingReturnReasonBatchService.aggregate(null,List.of(new Classified("a",Label.LOGISTICS,"不是质量问题，是物流问题",false)),1);
System.out.println("VALID_PRIMARY "+CandidateE706Evaluation.score(batch,"OK",Map.of("a","LOGISTICS"),1,0,actual));
System.out.println("CODE "+CandidateE704Evaluation.verdict(20,20,12,11,true,false)+" DECISION "+EvaluationSupport.decision(false,false,false,20,20,true));
}}
