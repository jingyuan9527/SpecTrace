package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.entity.Brand;
import com.spectrace.service.BrandService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-品牌管理")
@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminBrandController {

    private final BrandService brandService;
    private final OperationLogService logService;

    @GetMapping
    public R<List<Brand>> list() {
        return R.ok(brandService.list(null));
    }

    @PostMapping
    public R<Brand> create(@RequestBody Brand brand,
                           @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        Brand saved = brandService.save(brand);
        logService.record(adminId, adminName, "CREATE_BRAND", "brand", saved.getId(), saved.getName());
        return R.ok(saved);
    }

    @PutMapping("/{id}")
    public R<Brand> update(@PathVariable Long id, @RequestBody Brand brand,
                           @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        brand.setId(id);
        Brand saved = brandService.save(brand);
        logService.record(adminId, adminName, "UPDATE_BRAND", "brand", id, saved.getName());
        return R.ok(saved);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        brandService.delete(id);
        logService.record(adminId, adminName, "DELETE_BRAND", "brand", id, null);
        return R.ok();
    }
}
