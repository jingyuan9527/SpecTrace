package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.service.OperationLogService;
import com.spectrace.service.ParamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-参数元数据管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminParamController {

    private final ParamService paramService;
    private final OperationLogService logService;

    // ---------- 参数分组 ----------

    @GetMapping("/param-groups")
    public R<List<ParamGroup>> groups() {
        return R.ok(paramService.listGroups());
    }

    @PostMapping("/param-groups")
    public R<ParamGroup> createGroup(@RequestBody ParamGroup group,
                                     @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        ParamGroup saved = paramService.saveGroup(group);
        logService.record(adminId, adminName, "CREATE_PARAM_GROUP", "param_group", saved.getId(), saved.getName());
        return R.ok(saved);
    }

    @PutMapping("/param-groups/{id}")
    public R<ParamGroup> updateGroup(@PathVariable Long id, @RequestBody ParamGroup group,
                                     @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        group.setId(id);
        ParamGroup saved = paramService.saveGroup(group);
        logService.record(adminId, adminName, "UPDATE_PARAM_GROUP", "param_group", id, saved.getName());
        return R.ok(saved);
    }

    @DeleteMapping("/param-groups/{id}")
    public R<Void> deleteGroup(@PathVariable Long id,
                               @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        paramService.deleteGroup(id);
        logService.record(adminId, adminName, "DELETE_PARAM_GROUP", "param_group", id, null);
        return R.ok();
    }

    // ---------- 参数定义 ----------

    @GetMapping("/param-defs")
    public R<List<ParamDef>> defs(@RequestParam(required = false) Long groupId) {
        return R.ok(paramService.listDefs(groupId, null));
    }

    @PostMapping("/param-defs")
    public R<ParamDef> createDef(@RequestBody ParamDef def,
                                 @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        ParamDef saved = paramService.saveDef(def);
        logService.record(adminId, adminName, "CREATE_PARAM_DEF", "param_def", saved.getId(), saved.getParamKey());
        return R.ok(saved);
    }

    @PutMapping("/param-defs/{id}")
    public R<ParamDef> updateDef(@PathVariable Long id, @RequestBody ParamDef def,
                                 @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        def.setId(id);
        ParamDef saved = paramService.saveDef(def);
        logService.record(adminId, adminName, "UPDATE_PARAM_DEF", "param_def", id, saved.getParamKey());
        return R.ok(saved);
    }

    @DeleteMapping("/param-defs/{id}")
    public R<Void> deleteDef(@PathVariable Long id,
                             @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        paramService.deleteDef(id);
        logService.record(adminId, adminName, "DELETE_PARAM_DEF", "param_def", id, null);
        return R.ok();
    }
}
