package de.epiceric.shopchest.config;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;

public class LanguageConfiguration extends FileConfiguration {

    private ArrayList<String> lines = new ArrayList<>();
    private HashMap<String, String> values = new HashMap<>();

    private ShopChest plugin;

    public LanguageConfiguration(ShopChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public String saveToString() {
        StringBuilder sb = new StringBuilder("");

        for (String line : lines) {
            sb.append(line);
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public String getString(String path, String def) {
        for (String key : values.keySet()) {
            if (key.equals(path)) {
                return values.get(key);
            }
        }

        plugin.getLogger().warning("Using default value for: " + path + " (" + def + ")");
        return def;
    }

    @Override
    public void load(File file) throws IOException, InvalidConfigurationException {
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader br = new BufferedReader(isr);

        StringBuilder sb = new StringBuilder();

        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        fis.close();
        isr.close();
        br.close();

        loadFromString(sb.toString());
    }

    @Override
    public void loadFromString(String s) throws InvalidConfigurationException {
        String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.isEmpty()) {
                this.lines.add(line);

                if (!line.startsWith("#")) {
                    if (line.contains("=")) {
                        if (line.split("=").length >= 2) {
                            String key = line.split("=")[0];
                            StringBuilder sbValue = new StringBuilder();

                            for (int i = 1; i < line.split("=").length; i++) {
                                sbValue.append(line.split("=")[i]);
                            }

                            String value = Matcher.quoteReplacement(sbValue.toString());

                            values.put(key, value);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected String buildHeader() {
        return null;
    }
}