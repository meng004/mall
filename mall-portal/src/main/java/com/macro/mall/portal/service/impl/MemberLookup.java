package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.portal.service.UmsMemberCacheService;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 会员按用户名读取：缓存命中直接返回，未命中查库并回填。
 * 不负责密码、令牌或认证。
 */
public class MemberLookup {
    private final UmsMemberCacheService cache;
    private final UmsMemberMapper mapper;

    public MemberLookup(UmsMemberCacheService cache, UmsMemberMapper mapper) {
        this.cache = cache;
        this.mapper = mapper;
    }

    public UmsMember findByUsername(String username) {
        UmsMember cached = cache.getMember(username);
        if (cached != null) return cached;
        UmsMemberExample query = new UmsMemberExample();
        query.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> rows = mapper.selectByExample(query);
        if (CollectionUtils.isEmpty(rows)) return null;
        UmsMember member = rows.get(0);
        cache.setMember(member);
        return member;
    }
}
