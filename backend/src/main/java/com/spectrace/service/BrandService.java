package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.config.CacheConfig;
import com.spectrace.entity.Brand;
import com.spectrace.mapper.BrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandMapper brandMapper;

    @Cacheable(cacheNames = CacheConfig.BRANDS, key = "#status")
    public List<Brand> list(Integer status) {
        var query = Wrappers.<Brand>lambdaQuery().orderByAsc(Brand::getSortOrder);
        if (status != null) {
            query.eq(Brand::getStatus, status);
        }
        return brandMapper.selectList(query);
    }

    @CacheEvict(cacheNames = CacheConfig.BRANDS, allEntries = true)
    public Brand save(Brand brand) {
        if (brand.getId() == null) {
            if (brand.getStatus() == null) {
                brand.setStatus(1);
            }
            if (brand.getSortOrder() == null) {
                brand.setSortOrder(0);
            }
            brandMapper.insert(brand);
        } else {
            brandMapper.updateById(brand);
        }
        return brand;
    }

    @CacheEvict(cacheNames = CacheConfig.BRANDS, allEntries = true)
    public void delete(Long id) {
        Long used = brandMapper.selectCount(Wrappers.<Brand>lambdaQuery().eq(Brand::getId, id));
        if (used == null || used == 0) {
            throw new BizException("品牌不存在");
        }
        brandMapper.deleteById(id);
    }
}
