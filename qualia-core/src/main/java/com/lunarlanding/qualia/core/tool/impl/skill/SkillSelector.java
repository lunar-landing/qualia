package com.lunarlanding.qualia.core.tool.impl.skill;

import com.alibaba.fastjson.JSON;
import com.lunarlanding.qualia.core.agent.ReActAgent;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能选择器工具
 * 返回可用技能的名称和简要描述，引导模型按需加载具体技能
 */
public class SkillSelector extends FunctionTool {

    private final ReActAgent agent;

    public SkillSelector(ReActAgent agent) {
        this.agent = agent;
        this.setName("skill-selector");
        this.setDescription("查询可用技能列表。当用户问题可能适合用技能解决时，先调用此工具获取技能列表，再根据列表决定是否使用skill-loader加载具体技能。");
        this.setParameters(new Parameter[]{});
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        List<Skill> skills = agent.getSkills();

        if (skills.isEmpty()) {
            return "当前没有可用的技能。";
        }

        List<Map<String, String>> skillList = skills.stream()
                .map(s -> Map.of(
                        "name", s.getName(),
                        "description", s.getDescription() != null ? s.getDescription() : ""
                ))
                .collect(Collectors.toList());

        return JSON.toJSONString(skillList);
    }
}
