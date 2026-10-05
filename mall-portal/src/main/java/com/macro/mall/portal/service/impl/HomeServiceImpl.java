package com.macro.mall.portal.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.service.HomeService;
import com.macro.mall.portal.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * 首页内容管理Service实现类
 * Created by macro on 2019/1/28.
 */
@Service
public class HomeServiceImpl implements HomeService {
    @Autowired
    private SmsHomeAdvertiseMapper advertiseMapper;
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private CmsSubjectMapper subjectMapper;

    @Override
    public HomeContentResult content() {
        HomeContentResult result = new HomeContentResult();
        //获取首页广告
        result.setAdvertiseList(getHomeAdvertiseList());
        //获取推荐品牌
        result.setBrandList(homeDao.getRecommendBrandList(0,6));
        //获取秒杀信息
        result.setHomeFlashPromotion(getHomeFlashPromotion());
        //获取新品推荐
        result.setNewProductList(homeDao.getNewProductList(0,4));
        //获取人气推荐
        result.setHotProductList(homeDao.getHotProductList(0,4));
        //获取推荐专题
        result.setSubjectList(homeDao.getRecommendSubjectList(0,4));
        return result;
    }

    @Override
    public List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum) {
        // TODO: 2019/1/29 暂时默认推荐所有商品
        PageHelper.startPage(pageNum,pageSize);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        return productMapper.selectByExample(example);
    }

    @Override
    public List<PmsProductCategory> getProductCateList(Long parentId) {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria()
                .andShowStatusEqualTo(1)
                .andParentIdEqualTo(parentId);
        example.setOrderByClause("sort desc");
        return productCategoryMapper.selectByExample(example);
    }

    @Override
    public List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        CmsSubjectExample example = new CmsSubjectExample();
        CmsSubjectExample.Criteria criteria = example.createCriteria();
        criteria.andShowStatusEqualTo(1);
        if(cateId!=null){
            criteria.andCategoryIdEqualTo(cateId);
        }
        return subjectMapper.selectByExample(example);
    }

    @Override
    public List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getHotProductList(offset, pageSize);
    }

    @Override
    public List<PmsProduct> newProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getNewProductList(offset, pageSize);
    }

    private HomeFlashPromotion getHomeFlashPromotion() {
        return new FlashPromotionQuery(flashPromotionMapper, promotionSessionMapper, homeDao).load(new Date());
    }

    private List<SmsHomeAdvertise> getHomeAdvertiseList() {
        SmsHomeAdvertiseExample example = new SmsHomeAdvertiseExample();
        example.createCriteria().andTypeEqualTo(1).andStatusEqualTo(1);
        example.setOrderByClause("sort desc");
        return advertiseMapper.selectByExample(example);
    }

    /**
     * 秒杀查询协作者。时间从 load 传入，字段填充只在 assemble。
     * 比较运算符与原来的活动、当前场、下一场查询一致。
     */
    private static final class FlashPromotionQuery {
        private final SmsFlashPromotionMapper flashPromotionMapper;
        private final SmsFlashPromotionSessionMapper promotionSessionMapper;
        private final HomeDao homeDao;

        private FlashPromotionQuery(SmsFlashPromotionMapper flashPromotionMapper,
                                    SmsFlashPromotionSessionMapper promotionSessionMapper,
                                    HomeDao homeDao) {
            this.flashPromotionMapper = flashPromotionMapper;
            this.promotionSessionMapper = promotionSessionMapper;
            this.homeDao = homeDao;
        }

        private HomeFlashPromotion load(Date now) {
            HomeFlashPromotion homeFlashPromotion = new HomeFlashPromotion();
            SmsFlashPromotion flashPromotion = findPromotion(now);
            if (flashPromotion != null) {
                SmsFlashPromotionSession flashPromotionSession = findCurrentSession(now);
                if (flashPromotionSession != null) {
                    SmsFlashPromotionSession nextSession = findNextSession(flashPromotionSession.getStartTime());
                    List<FlashPromotionProduct> products = homeDao.getFlashProductList(
                            flashPromotion.getId(), flashPromotionSession.getId());
                    assemble(homeFlashPromotion, flashPromotionSession, nextSession, products);
                }
            }
            return homeFlashPromotion;
        }

        private static void assemble(HomeFlashPromotion target,
                                     SmsFlashPromotionSession current,
                                     SmsFlashPromotionSession next,
                                     List<FlashPromotionProduct> products) {
            target.setStartTime(current.getStartTime());
            target.setEndTime(current.getEndTime());
            if (next != null) {
                target.setNextStartTime(next.getStartTime());
                target.setNextEndTime(next.getEndTime());
            }
            target.setProductList(products);
        }

        private SmsFlashPromotion findPromotion(Date date) {
            Date currDate = DateUtil.getDate(date);
            SmsFlashPromotionExample example = new SmsFlashPromotionExample();
            example.createCriteria()
                    .andStatusEqualTo(1)
                    .andStartDateLessThanOrEqualTo(currDate)
                    .andEndDateGreaterThanOrEqualTo(currDate);
            List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectByExample(example);
            if (!CollectionUtils.isEmpty(flashPromotionList)) {
                return flashPromotionList.get(0);
            }
            return null;
        }

        private SmsFlashPromotionSession findCurrentSession(Date date) {
            Date currTime = DateUtil.getTime(date);
            SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
            sessionExample.createCriteria()
                    .andStartTimeLessThanOrEqualTo(currTime)
                    .andEndTimeGreaterThanOrEqualTo(currTime);
            List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
            if (!CollectionUtils.isEmpty(promotionSessionList)) {
                return promotionSessionList.get(0);
            }
            return null;
        }

        private SmsFlashPromotionSession findNextSession(Date date) {
            SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
            sessionExample.createCriteria()
                    .andStartTimeGreaterThan(date);
            sessionExample.setOrderByClause("start_time asc");
            List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
            if (!CollectionUtils.isEmpty(promotionSessionList)) {
                return promotionSessionList.get(0);
            }
            return null;
        }
    }
}
