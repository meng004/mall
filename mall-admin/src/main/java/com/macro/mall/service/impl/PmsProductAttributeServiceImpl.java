package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.PmsProductAttributeDao;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.dto.ProductAttrInfo;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
import com.macro.mall.service.PmsProductAttributeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 商品属性管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class PmsProductAttributeServiceImpl implements PmsProductAttributeService {
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Autowired
    private PmsProductAttributeDao productAttributeDao;

    @Override
    public List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.setOrderByClause("sort desc");
        example.createCriteria().andProductAttributeCategoryIdEqualTo(cid).andTypeEqualTo(type);
        return productAttributeMapper.selectByExample(example);
    }

    @Override
    public int create(PmsProductAttributeParam pmsProductAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        BeanUtils.copyProperties(pmsProductAttributeParam, pmsProductAttribute);
        int count = productAttributeMapper.insertSelective(pmsProductAttribute);
        //新增商品属性以后需要更新商品属性分类数量
        PmsProductAttributeCategory pmsProductAttributeCategory = productAttributeCategoryMapper.selectByPrimaryKey(pmsProductAttribute.getProductAttributeCategoryId());
        if(pmsProductAttribute.getType()==0){
            pmsProductAttributeCategory.setAttributeCount(pmsProductAttributeCategory.getAttributeCount()+1);
        }else if(pmsProductAttribute.getType()==1){
            pmsProductAttributeCategory.setParamCount(pmsProductAttributeCategory.getParamCount()+1);
        }
        productAttributeCategoryMapper.updateByPrimaryKey(pmsProductAttributeCategory);
        return count;
    }

    @Override
    public int update(Long id, PmsProductAttributeParam productAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        pmsProductAttribute.setId(id);
        BeanUtils.copyProperties(productAttributeParam, pmsProductAttribute);
        return productAttributeMapper.updateByPrimaryKeySelective(pmsProductAttribute);
    }

    @Override
    public PmsProductAttribute getItem(Long id) {
        return productAttributeMapper.selectByPrimaryKey(id);
    }

    @Override
    public int delete(List<Long> ids) {
        LinkedHashMap<Long, PmsProductAttribute> existingById = new LinkedHashMap<>();
        for (Long id : ids) {
            if (existingById.containsKey(id)) {
                continue;
            }
            PmsProductAttribute attribute = productAttributeMapper.selectByPrimaryKey(id);
            if (attribute != null) {
                existingById.put(id, attribute);
            }
        }
        if (existingById.isEmpty()) {
            return 0;
        }
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.createCriteria().andIdIn(new ArrayList<>(existingById.keySet()));
        int count = productAttributeMapper.deleteByExample(example);
        LinkedHashMap<Long, PmsProductAttributeCategory> affectedCategories = new LinkedHashMap<>();
        for (PmsProductAttribute attribute : existingById.values()) {
            Long categoryId = attribute.getProductAttributeCategoryId();
            PmsProductAttributeCategory category = affectedCategories.get(categoryId);
            if (category == null) {
                category = productAttributeCategoryMapper.selectByPrimaryKey(categoryId);
                affectedCategories.put(categoryId, category);
            }
            Integer type = attribute.getType();
            if (type == 0) {
                int current = category.getAttributeCount();
                category.setAttributeCount(current >= 1 ? current - 1 : 0);
            } else if (type == 1) {
                int current = category.getParamCount();
                category.setParamCount(current >= 1 ? current - 1 : 0);
            }
        }
        for (PmsProductAttributeCategory category : affectedCategories.values()) {
            productAttributeCategoryMapper.updateByPrimaryKey(category);
        }
        return count;
    }

    @Override
    public List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId) {
        return productAttributeDao.getProductAttrInfo(productCategoryId);
    }
}
