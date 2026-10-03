package com.macro.mall.portal.query;

import com.macro.mall.portal.service.PmsPortalProductService;
import org.springframework.stereotype.Service;

@Service
public class ExistingProductQueryService implements ProductQueryService {
    private final PmsPortalProductService products;
    public ExistingProductQueryService(PmsPortalProductService products) { this.products = products; }
    @Override public ProductQueryResult query(ProductQueryRequest request) {
        var criteria = request.criteria() != null ? request.criteria() : new ProductQueryCriteria(request.text(),null,null);
        var result = products.search(criteria,request.pageNum(),request.pageSize()).stream()
            .map(p -> new ProductQueryResult.ProductSummary(p.getId(),p.getName(),p.getPrice(),p.getStock())).toList();
        return new ProductQueryResult(ProductQueryResult.Status.OK,"existing",criteria,result,"查询完成");
    }
}
