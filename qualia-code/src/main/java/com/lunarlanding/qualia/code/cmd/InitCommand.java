package com.lunarlanding.qualia.code.cmd;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSON;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * 初始化配置命令
 */
@Command(name = "init", description = "初始化 Qualia Code 配置文件")
public class InitCommand implements Callable<Integer> {

    private static final Path GLOBAL_CONFIG_DIR = Path.of(System.getProperty("user.home"), ".qualia");
    private static final Path GLOBAL_CONFIG_FILE = GLOBAL_CONFIG_DIR.resolve("qualia-code.json");

    /** 预设的模型提供商 */
    private static final Map<String, ModelPreset> PRESETS = new LinkedHashMap<>();
    
    static {
        PRESETS.put("1", new ModelPreset("dashscope", "qwen-max", "https://dashscope.aliyuncs.com/api/v1", "通义千问"));
        PRESETS.put("2", new ModelPreset("openai", "gpt-4o", "https://api.openai.com/v1", "OpenAI"));
        PRESETS.put("3", new ModelPreset("deepseek", "deepseek-chat", "https://api.deepseek.com", "DeepSeek"));
        PRESETS.put("4", new ModelPreset("claude", "claude-3-5-sonnet-20241022", "https://api.anthropic.com", "Claude"));
    }

    @Override
    public Integer call() {
        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("=== Qualia Code 配置初始化 ===");
        System.out.println();

        // 检查是否已存在配置
        if (Files.exists(GLOBAL_CONFIG_FILE)) {
            System.out.print("配置文件已存在，是否覆盖？(y/N): ");
            String confirm = scanner.nextLine().trim();
            if (!"y".equalsIgnoreCase(confirm)) {
                System.out.println("已取消");
                return 0;
            }
        }

        // 选择模型提供商
        System.out.println("请选择模型提供商:");
        PRESETS.forEach((key, preset) -> 
            System.out.println("  " + key + ". " + preset.description)
        );
        System.out.println("  5. 自定义");
        System.out.println();
        System.out.print("请输入选项 [1-5] (默认 1): ");
        String choice = scanner.nextLine().trim();
        if (choice.isEmpty()) choice = "1";

        String provider, model, baseUrl, description;
        
        if ("5".equals(choice)) {
            // 自定义配置
            System.out.print("Provider: ");
            provider = scanner.nextLine().trim();
            System.out.print("Model 名称: ");
            model = scanner.nextLine().trim();
            System.out.print("Base URL: ");
            baseUrl = scanner.nextLine().trim();
            description = provider;
        } else if (PRESETS.containsKey(choice)) {
            ModelPreset preset = PRESETS.get(choice);
            provider = preset.provider;
            model = preset.model;
            baseUrl = preset.baseUrl;
            description = preset.description;
        } else {
            System.err.println("无效选项");
            return 1;
        }

        // 输入 API Key
        System.out.println();
        System.out.print("请输入 API Key: ");
        String apiKey = scanner.nextLine().trim();
        if (apiKey.isEmpty()) {
            System.err.println("API Key 不能为空");
            return 1;
        }

        // 确认配置
        System.out.println();
        System.out.println("=== 配置确认 ===");
        System.out.println("  Provider: " + provider);
        System.out.println("  Model:    " + model);
        System.out.println("  Base URL: " + baseUrl);
        System.out.println("  API Key:  " + maskApiKey(apiKey));
        System.out.println();
        System.out.print("确认保存？(Y/n): ");
        String confirm = scanner.nextLine().trim();
        if ("n".equalsIgnoreCase(confirm)) {
            System.out.println("已取消");
            return 0;
        }

        // 生成配置
        String name = provider + "-" + model;
        JSONObject config = buildConfig(name, provider, apiKey, model, baseUrl);

        // 保存配置
        try {
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            String json = JSON.toJSONString(config, true);
            Files.writeString(GLOBAL_CONFIG_FILE, json, StandardCharsets.UTF_8);
            System.out.println();
            System.out.println("配置已保存到: " + GLOBAL_CONFIG_FILE);
            System.out.println();
            return 0;
        } catch (IOException e) {
            System.err.println("保存配置失败: " + e.getMessage());
            return 1;
        }
    }

    private JSONObject buildConfig(String name, String provider, String apiKey, String model, String baseUrl) {
        JSONObject config = new JSONObject();
        config.put("version", "1.0.0");
        config.put("defaultModel", name);

        // models 数组
        JSONObject modelConfig = new JSONObject();
        modelConfig.put("name", name);
        modelConfig.put("provider", provider);
        modelConfig.put("apiKey", apiKey);
        modelConfig.put("model", model);
        modelConfig.put("baseUrl", baseUrl);

        config.put("models", new Object[]{modelConfig});

        // mcpServers 空数组
        config.put("mcpServers", new Object[]{});

        return config;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /** 模型预设 */
    private static class ModelPreset {
        final String provider;
        final String model;
        final String baseUrl;
        final String description;

        ModelPreset(String provider, String model, String baseUrl, String description) {
            this.provider = provider;
            this.model = model;
            this.baseUrl = baseUrl;
            this.description = description;
        }
    }
}
