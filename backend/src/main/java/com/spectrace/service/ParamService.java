package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.mapper.ParamDefMapper;
import com.spectrace.mapper.ParamGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParamService {

    private final ParamGroupMapper paramGroupMapper;
    private final ParamDefMapper paramDefMapper;

    public List<ParamGroup> listGroups() {
        return paramGroupMapper.selectList(Wrappers.<ParamGroup>lambdaQuery().orderByAsc(ParamGroup::getSortOrder));
    }

    public ParamGroup saveGroup(ParamGroup group) {
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
        if (group.getId() == null) {
            paramGroupMapper.insert(group);
        } else {
            paramGroupMapper.updateById(group);
        }
        return group;
    }

    public void deleteGroup(Long id) {
        paramGroupMapper.deleteById(id);
    }

    public List<ParamDef> listDefs(Long groupId, Integer enabled) {
        var query = Wrappers.<ParamDef>lambdaQuery().orderByAsc(ParamDef::getSortOrder);
        if (groupId != null) {
            query.eq(ParamDef::getGroupId, groupId);
        }
        if (enabled != null) {
            query.eq(ParamDef::getEnabled, enabled);
        }
        return paramDefMapper.selectList(query);
    }

    public ParamDef saveDef(ParamDef def) {
        if (def.getEnabled() == null) {
            def.setEnabled(1);
        }
        if (def.getSortOrder() == null) {
            def.setSortOrder(0);
        }
        if (def.getId() == null) {
            paramDefMapper.insert(def);
        } else {
            paramDefMapper.updateById(def);
        }
        return def;
    }

    public void deleteDef(Long id) {
        paramDefMapper.deleteById(id);
    }

    /** paramKey -> 参数名，供待审核数据展示等场景使用。 */
    public Map<String, String> keyToNameMap() {
        return paramDefMapper.selectList(null).stream()
                .collect(Collectors.toMap(ParamDef::getParamKey, ParamDef::getParamName, (a, b) -> a));
    }

    /** paramKey -> ParamDef，供 Agent 抽取 Schema 与入库映射使用。 */
    public Map<String, ParamDef> keyToDefMap() {
        return paramDefMapper.selectList(null).stream()
                .collect(Collectors.toMap(ParamDef::getParamKey, d -> d, (a, b) -> a));
    }
}
