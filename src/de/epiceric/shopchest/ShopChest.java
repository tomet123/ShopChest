package de.epiceric.shopchest;

import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.LanguageConfiguration;
import de.epiceric.shopchest.config.Regex;
import de.epiceric.shopchest.event.*;
import de.epiceric.shopchest.interfaces.JsonBuilder;
import de.epiceric.shopchest.interfaces.jsonbuilder.*;
import de.epiceric.shopchest.language.LanguageUtils;
import de.epiceric.shopchest.language.LocalizedMessage;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.sql.Database;
import de.epiceric.shopchest.sql.MySQL;
import de.epiceric.shopchest.sql.SQLite;
import de.epiceric.shopchest.utils.Metrics;
import de.epiceric.shopchest.utils.Metrics.Graph;
import de.epiceric.shopchest.utils.Metrics.Plotter;
import de.epiceric.shopchest.utils.ShopUtils;
import de.epiceric.shopchest.utils.UpdateChecker;
import de.epiceric.shopchest.utils.UpdateChecker.UpdateCheckerResult;
import de.epiceric.shopchest.utils.Utils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

public class ShopChest extends JavaPlugin {

    private static ShopChest instance;
    private Economy econ = null;
    private Permission perm = null;
    private boolean lockette = false;
    private boolean lwc = false;
    private Database database;
    private boolean isUpdateNeeded = false;
    private String latestVersion = "";
    private String downloadLink = "";
    private String[] broadcast = null;
    private LanguageConfiguration langConfig;

    public static ShopChest getInstance() {
        return instance;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perm = rsp.getProvider();
        return perm != null;
    }

    private void initLanguageConfig() {
        langConfig = new LanguageConfiguration(this);
        File langFolder = new File(getDataFolder(), "lang");

        if (!(new File(langFolder, "en_US.lang")).exists())
            saveResource("lang/en_US.lang", false);

        if (!(new File(langFolder, "de_DE.lang")).exists())
            saveResource("lang/de_DE.lang", false);

        File langConfigFile = new File(langFolder, Config.language_file + ".lang");
        File langDefaultFile = new File(langFolder, "en_US.lang");

        if (!langConfigFile.exists()) {
            if (!langDefaultFile.exists()) {
                try {
                    Reader r = getTextResource("lang/" + langConfigFile.getName());

                    if (r == null) {
                        r = getTextResource("lang/en_US.lang");
                        getLogger().info("Using locale \"en_US\" (Streamed from jar file)");
                    } else {
                        getLogger().info("Using locale \"" + langConfigFile.getName().substring(0, langConfigFile.getName().length() - 5) + "\" (Streamed from jar file)");
                    }

                    BufferedReader br = new BufferedReader(r);

                    StringBuilder sb = new StringBuilder();
                    String line = br.readLine();

                    while (line != null) {
                        sb.append(line);
                        sb.append("\n");
                        line = br.readLine();
                    }

                    langConfig.loadFromString(sb.toString());
                } catch (IOException | InvalidConfigurationException ex) {
                    ex.printStackTrace();
                    getLogger().warning("Using default language values");
                }
            } else {
                try {
                    langConfig.load(langDefaultFile);
                    getLogger().info("Using locale \"en_US\"");
                } catch (IOException | InvalidConfigurationException e) {
                    e.printStackTrace();
                    getLogger().warning("Using default language values");
                }
            }
        } else {
            try {
                getLogger().info("Using locale \"" + langConfigFile.getName().substring(0, langConfigFile.getName().length() - 5) + "\"");
                langConfig.load(langConfigFile);
            } catch (IOException | InvalidConfigurationException ex) {
                ex.printStackTrace();
                getLogger().warning("Using default language values");
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Could not find plugin 'Vault'!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            getLogger().severe("Could not find any Vault economy dependency!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupPermissions()) {
            getLogger().severe("Could not find any Vault permission dependency!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        switch (Utils.getServerVersion()) {
            case "v1_8_R1":
            case "v1_8_R2":
            case "v1_8_R3":
            case "v1_9_R1":
            case "v1_9_R2":
            case "v1_10_R1":
                break;
            default:
                getLogger().severe("Incompatible Server Version: " + Utils.getServerVersion() + "!");
                getServer().getPluginManager().disablePlugin(this);
                return;
        }

        initLanguageConfig();
        LanguageUtils.load();
        saveResource("item_names.txt", true);
        reloadConfig();
        saveDefaultConfig();

        try {
            Metrics metrics = new Metrics(this);
            Graph shopType = metrics.createGraph("Shop Type");
            shopType.addPlotter(new Plotter("Normal") {

                @Override
                public int getValue() {
                    int value = 0;

                    for (Shop shop : ShopUtils.getShops()) {
                        if (shop.getShopType() == ShopType.NORMAL) value++;
                    }

                    return value;
                }

            });

            shopType.addPlotter(new Plotter("Admin") {

                @Override
                public int getValue() {
                    int value = 0;

                    for (Shop shop : ShopUtils.getShops()) {
                        if (shop.getShopType() == ShopType.ADMIN) value++;
                    }

                    return value;
                }

            });

            Graph databaseType = metrics.createGraph("Database Type");
            databaseType.addPlotter(new Plotter("SQLite") {

                @Override
                public int getValue() {
                    if (Config.database_type == Database.DatabaseType.SQLite)
                        return 1;

                    return 0;
                }

            });

            databaseType.addPlotter(new Plotter("MySQL") {

                @Override
                public int getValue() {
                    if (Config.database_type == Database.DatabaseType.MySQL)
                        return 1;

                    return 0;
                }

            });

            metrics.start();
        } catch (IOException e) {
            getLogger().severe("Could not submit stats.");
        }

        if (Config.database_type == Database.DatabaseType.SQLite) {
            getLogger().info("Using SQLite");
            database = new SQLite(this);
        } else {
            getLogger().info("Using MySQL");
            database = new MySQL(this);
        }

        lockette = getServer().getPluginManager().getPlugin("Lockette") != null;
        lwc = getServer().getPluginManager().getPlugin("LWC") != null;

        UpdateChecker uc = new UpdateChecker(this, getDescription().getWebsite());
        UpdateCheckerResult result = uc.updateNeeded();

        if (Config.enable_broadcast) broadcast = uc.getBroadcast();

        Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CHECKING));
        if (result == UpdateCheckerResult.TRUE) {
            latestVersion = uc.getVersion();
            downloadLink = uc.getLink();
            isUpdateNeeded = true;
            Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));

            for (Player p : getServer().getOnlinePlayers()) {
                if (p.isOp() || perm.has(p, "shopchest.notification.update")) {
                    JsonBuilder jb;
                    switch (Utils.getServerVersion()) {
                        case "v1_8_R1":
                            jb = new JsonBuilder_1_8_R1(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        case "v1_8_R2":
                            jb = new JsonBuilder_1_8_R2(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        case "v1_8_R3":
                            jb = new JsonBuilder_1_8_R3(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        case "v1_9_R1":
                            jb = new JsonBuilder_1_9_R1(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        case "v1_9_R2":
                            jb = new JsonBuilder_1_9_R2(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        case "v1_10_R1":
                            jb = new JsonBuilder_1_10_R1(this, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));
                            break;
                        default:
                            return;
                    }
                    jb.sendJson(p);
                }
            }

        } else if (result == UpdateCheckerResult.FALSE) {
            latestVersion = "";
            downloadLink = "";
            isUpdateNeeded = false;
            Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_NO_UPDATE));
        } else {
            latestVersion = "";
            downloadLink = "";
            isUpdateNeeded = false;
            Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_ERROR));
        }

        for (Player p : getServer().getOnlinePlayers()) {
            if (perm.has(p, "shopchest.broadcast")) {
                if (broadcast != null) {
                    for (String message : broadcast) {
                        p.sendMessage(message);
                    }
                }
            }
        }

        if (broadcast != null) {
            for (String message : broadcast) {
                Bukkit.getConsoleSender().sendMessage("[ShopChest] " + message);
            }
        }

        try {
            Commands.registerCommand(new Commands(this, Config.main_command_name, "Manage Shops.", "", new ArrayList<String>()), this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        initializeShops();

        getServer().getPluginManager().registerEvents(new UpdateHolograms(), this);
        getServer().getPluginManager().registerEvents(new RegenerateShopItem(), this);
        getServer().getPluginManager().registerEvents(new InteractShop(this), this);
        getServer().getPluginManager().registerEvents(new NotifyUpdate(this), this);
        getServer().getPluginManager().registerEvents(new ProtectChest(), this);
        getServer().getPluginManager().registerEvents(new ItemCustomNameListener(), this);

        if (getServer().getPluginManager().getPlugin("ClearLag") != null)
            getServer().getPluginManager().registerEvents(new RegenerateShopItemAfterRemove(), this);

        if (getServer().getPluginManager().getPlugin("LWC") != null)
            new LWCMagnetListener(this).initializeListener();
    }

    @Override
    public void onDisable() {
        for (Shop shop : ShopUtils.getShops()) {
            ShopUtils.removeShop(shop, false);
        }
    }

    private void initializeShops() {
        int count = ShopUtils.reloadShops();
        getLogger().info("Initialized " + String.valueOf(count) + " Shops");
    }

    public LanguageConfiguration getLanguageConfig() {
        return langConfig;
    }

    public Economy getEconomy() {
        return econ;
    }

    public Permission getPermission() {
        return perm;
    }

    public Database getShopDatabase() {
        return database;
    }

    public boolean hasLWC() {
        return lwc;
    }

    public boolean hasLockette() {
        return lockette;
    }

    public boolean isUpdateNeeded() {
        return isUpdateNeeded;
    }

    public void setUpdateNeeded(boolean isUpdateNeeded) {
        this.isUpdateNeeded = isUpdateNeeded;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    public String[] getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(String[] broadcast) {
        this.broadcast = broadcast;
    }
}
