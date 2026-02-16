package com.enmodify;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.util.Properties;

public class ModConfig {
    private static final String CONFIG_FILE = "epf_fix.properties";
    
    // 默认分母
    public static float defenseDenominator = 50.0F;
    // 默认上限 (-1.0F 表示自动跟随分母-1)
    public static float maxEpfLimit = -1.0F;

    public static void load() {
        File file = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile();
        Properties props = new Properties();

        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                defenseDenominator = Float.parseFloat(props.getProperty("defenseDenominator", "50.0"));
                maxEpfLimit = Float.parseFloat(props.getProperty("maxEpfLimit", "-1.0"));
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        } else {
            save(); // 不存在则创建默认
        }
    }

    public static void save() {
        File file = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile();
        Properties props = new Properties();
        props.setProperty("defenseDenominator", String.valueOf(defenseDenominator));
        props.setProperty("maxEpfLimit", String.valueOf(maxEpfLimit));
        
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "EPF Fix Config\nmaxEpfLimit: Set to -1 to use (denominator - 1)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}