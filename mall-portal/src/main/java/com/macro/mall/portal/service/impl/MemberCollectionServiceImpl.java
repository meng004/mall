package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.service.MemberCollectionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 会员收藏Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class MemberCollectionServiceImpl implements MemberCollectionService {
    @Value("${mongo.insert.sqlEnable}")
    private Boolean sqlEnable;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private MemberProductCollectionRepository productCollectionRepository;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int add(MemberProductCollection productCollection) {
        int count = 0;
        if (productCollection.getProductId() == null) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        productCollection.setMemberId(member.getId());
        productCollection.setMemberNickname(member.getNickname());
        productCollection.setMemberIcon(member.getIcon());
        MemberProductCollection findCollection = productCollectionRepository.findByMemberIdAndProductId(productCollection.getMemberId(), productCollection.getProductId());
        if (findCollection == null) {
            ProductSnapshotLookup.Read read = ProductSnapshotLookup.read(sqlEnable, productCollection.getProductId(), productMapper);
            if (read.source() == ProductSnapshotLookup.Source.MISSING) {
                return 0;
            }
            if (read.source() == ProductSnapshotLookup.Source.PRODUCT) {
                productCollection.setProductName(read.snapshot().name());
                productCollection.setProductSubTitle(read.snapshot().subTitle());
                productCollection.setProductPrice(read.snapshot().price());
                productCollection.setProductPic(read.snapshot().pic());
            }
            productCollectionRepository.save(productCollection);
            count = 1;
        }
        return count;
    }

    @Override
    public int delete(Long productId) {
        UmsMember member = memberService.getCurrentMember();
        return productCollectionRepository.deleteByMemberIdAndProductId(member.getId(), productId);
    }

    @Override
    public Page<MemberProductCollection> list(Integer pageNum, Integer pageSize) {
        UmsMember member = memberService.getCurrentMember();
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return productCollectionRepository.findByMemberId(member.getId(), pageable);
    }

    @Override
    public MemberProductCollection detail(Long productId) {
        UmsMember member = memberService.getCurrentMember();
        return productCollectionRepository.findByMemberIdAndProductId(member.getId(), productId);
    }

    @Override
    public void clear() {
        UmsMember member = memberService.getCurrentMember();
        productCollectionRepository.deleteAllByMemberId(member.getId());
    }
}

final class ProductSnapshotLookup {
    private ProductSnapshotLookup() {
    }

    enum Source { REQUEST, MISSING, PRODUCT }

    record Snapshot(String name, String subTitle, String price, String pic) {
    }

    record Read(Source source, Snapshot snapshot) {
    }

    static Read read(Boolean sqlEnable, Long productId, PmsProductMapper productMapper) {
        if (!sqlEnable) {
            return new Read(Source.REQUEST, null);
        }
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null || product.getDeleteStatus() == 1) {
            return new Read(Source.MISSING, null);
        }
        return new Read(Source.PRODUCT, new Snapshot(
                product.getName(),
                product.getSubTitle(),
                priceText(product),
                product.getPic()));
    }

    static String priceText(PmsProduct product) {
        return product.getPrice() + "";
    }
}
