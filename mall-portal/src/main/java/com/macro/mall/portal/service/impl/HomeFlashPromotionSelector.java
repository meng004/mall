package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionExample;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.model.SmsFlashPromotionSessionExample;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.util.DateUtil;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * 首页秒杀的时间规则：按给定时刻选出当前活动、当前场次、下一场次及其商品。
 * 从 HomeServiceImpl 原样搬出，规则与查询条件不变。
 */
public class HomeFlashPromotionSelector {
    private final SmsFlashPromotionMapper flashPromotionMapper;
    private final SmsFlashPromotionSessionMapper promotionSessionMapper;
    private final HomeDao homeDao;

    public HomeFlashPromotionSelector(SmsFlashPromotionMapper flashPromotionMapper,
                                      SmsFlashPromotionSessionMapper promotionSessionMapper,
                                      HomeDao homeDao) {
        this.flashPromotionMapper = flashPromotionMapper;
        this.promotionSessionMapper = promotionSessionMapper;
        this.homeDao = homeDao;
    }

    public HomeFlashPromotion select(Date now) {
        HomeFlashPromotion homeFlashPromotion = new HomeFlashPromotion();
        //获取当前秒杀活动
        SmsFlashPromotion flashPromotion = getFlashPromotion(now);
        if (flashPromotion != null) {
            //获取当前秒杀场次
            SmsFlashPromotionSession flashPromotionSession = getFlashPromotionSession(now);
            if (flashPromotionSession != null) {
                homeFlashPromotion.setStartTime(flashPromotionSession.getStartTime());
                homeFlashPromotion.setEndTime(flashPromotionSession.getEndTime());
                //获取下一个秒杀场次
                SmsFlashPromotionSession nextSession = getNextFlashPromotionSession(homeFlashPromotion.getStartTime());
                if(nextSession!=null){
                    homeFlashPromotion.setNextStartTime(nextSession.getStartTime());
                    homeFlashPromotion.setNextEndTime(nextSession.getEndTime());
                }
                //获取秒杀商品
                List<FlashPromotionProduct> flashProductList = homeDao.getFlashProductList(flashPromotion.getId(), flashPromotionSession.getId());
                homeFlashPromotion.setProductList(flashProductList);
            }
        }
        return homeFlashPromotion;
    }

    //获取下一个场次信息
    private SmsFlashPromotionSession getNextFlashPromotionSession(Date date) {
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(nextSessionExample(date));
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }

    //根据时间获取秒杀活动
    private SmsFlashPromotion getFlashPromotion(Date date) {
        List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectByExample(flashPromotionExample(date));
        if (!CollectionUtils.isEmpty(flashPromotionList)) {
            return flashPromotionList.get(0);
        }
        return null;
    }

    //根据时间获取秒杀场次
    private SmsFlashPromotionSession getFlashPromotionSession(Date date) {
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(currentSessionExample(date));
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }

    /** 活动日闭区间：起点 <=，终点 >=。 */
    private static SmsFlashPromotionExample flashPromotionExample(Date date) {
        Date currDate = DateUtil.getDate(date);
        SmsFlashPromotionExample example = new SmsFlashPromotionExample();
        example.createCriteria()
                .andStatusEqualTo(1)
                .andStartDateLessThanOrEqualTo(currDate)
                .andEndDateGreaterThanOrEqualTo(currDate);
        return example;
    }

    /** 当前场次闭区间：开始 <=，结束 >=。 */
    private static SmsFlashPromotionSessionExample currentSessionExample(Date date) {
        Date currTime = DateUtil.getTime(date);
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeLessThanOrEqualTo(currTime)
                .andEndTimeGreaterThanOrEqualTo(currTime);
        return sessionExample;
    }

    /** 下一场严格晚于当前场开始，不含相等。 */
    private static SmsFlashPromotionSessionExample nextSessionExample(Date date) {
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeGreaterThan(date);
        sessionExample.setOrderByClause("start_time asc");
        return sessionExample;
    }
}
