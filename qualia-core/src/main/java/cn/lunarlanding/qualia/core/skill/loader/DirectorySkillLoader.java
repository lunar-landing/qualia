package cn.lunarlanding.qualia.core.skill.loader;

import cn.lunarlanding.qualia.core.skill.engine.PythonScriptEngine;
import cn.lunarlanding.qualia.core.skill.engine.ScriptEngine;
import cn.lunarlanding.qualia.core.skill.Skill;
import cn.lunarlanding.qualia.core.skill.SkillScript;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 目录技能加载器
 * 从文件系统目录加载技能及其脚本
 */
public class DirectorySkillLoader {

    private static final Logger logger = LoggerFactory.getLogger(DirectorySkillLoader.class);
    private static final String SKILL_FILE = "skill.md";
    private static final String SCRIPT_DIR = "script";

    /** PEP 263 编码声明匹配（# -*- coding: utf-8 -*- / # coding=utf-8 等） */
    private static final Pattern CODING_PATTERN = Pattern.compile("coding[:=]\\s*[-\\w.]+");

    private final Path skillsDir;
    private final ScriptEngine scriptExecutor;

    /**
     * 构造目录加载器
     *
     * @param skillsDir 技能根目录
     */
    public DirectorySkillLoader(Path skillsDir) {
        this.skillsDir = skillsDir;
        this.scriptExecutor = new PythonScriptEngine();
    }

    /**
     * 构造目录加载器
     *
     * @param skillsDir      技能根目录
     * @param scriptExecutor 脚本执行器
     */
    public DirectorySkillLoader(Path skillsDir, ScriptEngine scriptExecutor) {
        this.skillsDir = skillsDir;
        this.scriptExecutor = scriptExecutor;
    }

    /**
     * 加载所有技能
     *
     * @return 技能列表
     */
    public List<Skill> loadAll() {
        if (!Files.exists(skillsDir)) {
            logger.warn("技能目录不存在: {}", skillsDir);
            return Collections.emptyList();
        }

        List<Skill> skills = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory)
                .forEach(dir -> {
                    Skill skill = loadFromDirectory(dir);
                    if (skill != null) {
                        skills.add(skill);
                    }
                });
        } catch (IOException e) {
            logger.error("加载技能目录失败: {}", skillsDir, e);
        }
        return skills;
    }

    /**
     * 按名称加载技能
     *
     * @param name 技能名称
     * @return 技能对象，未找到返回 null
     */
    public Skill loadByName(String name) {
        Path skillDir = skillsDir.resolve(name);
        if (Files.exists(skillDir)) {
            return loadFromDirectory(skillDir);
        }
        return null;
    }

    /**
     * 从目录加载单个技能
     */
    private Skill loadFromDirectory(Path skillDir) {
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.exists(skillFile)) {
            logger.debug("跳过目录（无 skill.md）: {}", skillDir);
            return null;
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            SkillMetadata metadata = parseSkillMetadata(content, skillDir);

            Skill skill = new Skill(metadata.name, metadata.description)
                .withContent(content);

            // 加载 references/ 目录
            Path refsDir = skillDir.resolve("references");
            if (Files.exists(refsDir)) {
                try (Stream<Path> refs = Files.list(refsDir)) {
                    refs.filter(Files::isRegularFile)
                        .forEach(ref -> {
                            try {
                                skill.withReference(
                                    ref.getFileName().toString(),
                                    Files.readString(ref, StandardCharsets.UTF_8)
                                );
                            } catch (IOException e) {
                                logger.error("加载附属文档失败: {}", ref, e);
                            }
                        });
                }
            }

            // 加载 script/ 目录
            Path scriptDir = skillDir.resolve(SCRIPT_DIR);
            if (Files.exists(scriptDir)) {
                loadScripts(scriptDir, skill);
            }

            logger.info("加载技能: {} ({} 个脚本, {} 个文档)",
                metadata.name, skill.getScripts().size(), skill.getReferenceNames().size());
            return skill;

        } catch (IOException e) {
            logger.error("加载技能失败: {}", skillDir, e);
            return null;
        }
    }

    /**
     * 加载脚本目录中的所有脚本
     */
    private void loadScripts(Path scriptDir, Skill skill) {
        try (Stream<Path> scripts = Files.list(scriptDir)) {
            scripts.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".py") || p.toString().endsWith(".js"))
                .forEach(script -> {
                    String baseName = FilenameUtils.getBaseName(script.toString());
                    String scriptName = "script_" + skill.getName().toLowerCase().replaceAll("[\\s\\-]+", "_") + "_" + baseName;
                    String description = extractScriptDescription(script);
                    SkillScript skillScript = new SkillScript(scriptName, description, script);
                    skill.addScript(skillScript);
                    logger.debug("  加载脚本: {} -> {}", baseName, scriptName);
                });
        } catch (IOException e) {
            logger.error("加载脚本目录失败: {}", scriptDir, e);
        }
    }

    /**
     * 解析 skill.md 的元数据
     */
    private SkillMetadata parseSkillMetadata(String content, Path skillDir) {
        String name = skillDir.getFileName().toString(); // 默认用目录名
        String description = "";

        // 解析 # 标题作为 name
        Pattern titlePattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = titlePattern.matcher(content);
        if (matcher.find()) {
            name = matcher.group(1).trim();
        }

        // 解析 YAML front matter
        Pattern yamlPattern = Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---");
        Matcher yamlMatcher = yamlPattern.matcher(content);
        if (yamlMatcher.find()) {
            String yaml = yamlMatcher.group(1);
            Pattern descPattern = Pattern.compile("description:\\s*['\"]?(.+?)['\"]?\\s*$", Pattern.MULTILINE);
            Matcher descMatcher = descPattern.matcher(yaml);
            if (descMatcher.find()) {
                description = descMatcher.group(1).trim();
            }
        }

        // 如果没有 YAML，取第二行非空内容作为描述
        if (description.isEmpty()) {
            String[] lines = content.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    description = line.length() > 100 ? line.substring(0, 100) + "..." : line;
                    break;
                }
            }
        }

        return new SkillMetadata(name, description);
    }

    /**
     * 从脚本文件提取描述
     */
    private String extractScriptDescription(Path script) {
        try {
            List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                // 跳过 shebang 和严格模式声明
                if (line.startsWith("#!") || line.startsWith("'use strict'") || line.startsWith("\"use strict\"")) continue;
                // 跳过 PEP 263 编码声明（如 # -*- coding: utf-8 -*-），避免误当描述
                if (line.startsWith("#") && CODING_PATTERN.matcher(line).find()) continue;
                // 取第一个注释作为描述（Python # / JS //）
                if (line.startsWith("#") && line.length() > 1) {
                    return line.substring(1).trim();
                }
                if (line.startsWith("//") && line.length() > 2) {
                    return line.substring(2).trim();
                }
                break;
            }
        } catch (IOException e) {
            // ignore
        }
        return FilenameUtils.getBaseName(script.toString());
    }

    /**
     * 获取技能目录路径
     */
    public Path getSkillsDir() {
        return skillsDir;
    }

    /**
     * 技能元数据
     */
    private record SkillMetadata(String name, String description) {}
}
