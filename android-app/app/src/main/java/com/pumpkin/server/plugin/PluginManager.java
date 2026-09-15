package com.pumpkin.server.plugin;

import android.content.Context;

import com.pumpkin.plugin.PluginHost;
import com.pumpkin.plugin.PluginKeys;
import com.pumpkin.plugin.PluginSetting;
import com.pumpkin.plugin.PumpkinPlugin;

import org.json.JSONObject;

import com.pumpkin.server.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * 插件管理器：扫描私有目录里的插件、加载它们、并把它们声明的东西交给 App 去渲染。
 *
 * <h3>插件长什么样</h3>
 * <pre>
 * filesDir/plugins/&lt;插件id&gt;/
 *     plugin.json     {"id":"lan-address","entry":"com.pumpkin.plugin.lanaddress.LanAddressPlugin"}
 *     plugin.dex      插件代码（CI 用 D8 把插件 jar 转出来的）
 * </pre>
 *
 * <h3>类加载器：这里是最关键的一处设计</h3>
 * {@code new DexClassLoader(dex, optDir, null, ctx.getClassLoader())} —— 父加载器是
 * **App 自己的类加载器**。这样插件里的 {@code PumpkinPlugin} / {@code PluginHost}
 * 会解析到 App dex 里的那一份，于是：
 * <ul>
 *   <li>{@code instanceof PumpkinPlugin} 正常（两个类加载器各持一份同名类时，这个判断会失败）；</li>
 *   <li>App 传给插件的 {@link PluginHost} 实例与插件期望的类型是同一个类；</li>
 *   <li>插件因此必须用 {@code compileOnly} 依赖 plugin-api，绝不能把接口打进自己的 dex。</li>
 * </ul>
 * 对应地，App 侧必须对 {@code com.pumpkin.plugin.**} 加 R8 keep 规则（见 proguard-rules.pro），
 * 否则这些接口被混淆/删掉后，插件会全部加载失败，而主流程看不出任何异常。
 *
 * <h3>插件能改什么</h3>
 * 插件不直接碰 App 的内部状态，而是往「覆盖板」写约定的键（见 {@link PluginKeys}），
 * App 在渲染与行为处读这些键。好处是「插件能影响什么」是显式清单，
 * 插件写坏了最多让某个值不对，不会把 App 弄崩。
 */
public final class PluginManager {

    private static final String TAG = "PumpkinPlugin";

    /** 覆盖板在 Prefs 里的键前缀。 */
    private static final String OVERRIDE_PREFIX = "plugin_ov_";

    private final Context ctx;

    /** 已加载的插件，按加载顺序。 */
    private final List<LoadedPlugin> loaded = new ArrayList<>();

    /** 插件声明过的设置项，按加载顺序去重。 */
    private final Map<String, PluginSetting> settings = new LinkedHashMap<>();

    /** 插件日志（插件通过 PluginHost.log 写的），给设置页展示用。 */
    private final StringBuilder logBuf = new StringBuilder();

    public PluginManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** 插件目录。没有就建出来，并在里面放一份说明。 */
    public File pluginDir() {
        File dir = new File(ctx.getFilesDir(), "plugins");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            return dir;
        }
        File readme = new File(dir, "说明.txt");
        if (!readme.exists()) {
            try {
                java.io.FileWriter w = new java.io.FileWriter(readme, false);
                w.write("把插件放在这个目录下，每个插件一个子目录：\n"
                        + "  <插件id>/plugin.json   内容是 {\"id\":\"...\",\"entry\":\"类的全名\"}\n"
                        + "  <插件id>/plugin.dex    插件代码（CI 会把插件编译成 dex）\n"
                        + "\n"
                        + "改完插件后回 App 的「设置 → 插件」点「重新加载」。\n");
                w.close();
            } catch (Exception ignored) {
                // 写不了说明不影响功能
            }
        }
        return dir;
    }

    /**
     * 扫描并加载全部插件。
     *
     * **可以重复调用**（设置页有「重新加载」）：每次都清空状态重新扫。
     * 注意旧的类加载器无法卸载，所以同一个插件重新加载会多占一份 dex 内存 ——
     * 插件很小，这里不做回收。
     */
    public void loadAll() {
        loaded.clear();
        settings.clear();
        logBuf.setLength(0);

        File dir = pluginDir();
        File[] subs = dir.listFiles();
        if (subs == null) {
            log("插件目录读不到：" + dir.getAbsolutePath());
            return;
        }

        int ok = 0;
        for (File sub : subs) {
            if (!sub.isDirectory()) {
                continue;
            }
            try {
                loadOne(sub);
                ok++;
            } catch (Throwable t) {
                // 单个插件坏掉不能影响别的插件，更不能影响 App 启动
                log("[" + sub.getName() + "] 加载失败：" + describe(t));
            }
        }
        log("共加载 " + ok + " 个插件");
    }

    private void loadOne(File dir) throws Exception {
        File manifest = new File(dir, "plugin.json");
        File dex = new File(dir, "plugin.dex");
        if (!manifest.exists()) {
            throw new IllegalStateException("缺少 plugin.json");
        }
        if (!dex.exists()) {
            throw new IllegalStateException("缺少 plugin.dex");
        }

        String json = readText(manifest);
        JSONObject o = new JSONObject(json);
        String entry = o.optString("entry", "").trim();
        if (entry.isEmpty()) {
            throw new IllegalStateException("plugin.json 里没有 entry");
        }

        // 父加载器 = App 的类加载器，见类注释。
        File optDir = new File(ctx.getCodeCacheDir(), "plugins/" + dir.getName());
        if (!optDir.exists()) {
            optDir.mkdirs();
        }
        DexClassLoader loader = new DexClassLoader(
                dex.getAbsolutePath(), optDir.getAbsolutePath(), null, ctx.getClassLoader());

        Class<?> cls = loader.loadClass(entry);
        Object instance = cls.getDeclaredConstructor().newInstance();
        if (!(instance instanceof PumpkinPlugin)) {
            // 能到这里说明类加载器链是对的，只是这个类没实现接口
            throw new IllegalStateException(entry + " 没有实现 PumpkinPlugin");
        }

        PumpkinPlugin plugin = (PumpkinPlugin) instance;
        HostImpl host = new HostImpl(plugin.getId());
        plugin.onLoad(host);

        loaded.add(new LoadedPlugin(plugin.getId(), plugin.getName(), plugin.getVersion(),
                plugin.getDescription()));
    }

    private String describe(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private static String readText(File f) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            try {
                r.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- 给 UI 与业务层用

    /** 已加载插件的展示信息。 */
    public static final class LoadedPlugin {
        public final String id;
        public final String name;
        public final String version;
        public final String description;

        LoadedPlugin(String id, String name, String version, String description) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
        }
    }

    public List<LoadedPlugin> loaded() {
        return loaded;
    }

    /** 插件声明过的设置项（已按 key 去重）。 */
    public List<PluginSetting> settings() {
        return new ArrayList<>(settings.values());
    }

    /** 覆盖板：返回 null 表示插件没有覆盖这一项，App 应当走自己的默认逻辑。 */
    public String override(String key) {
        String v = Prefs.get(ctx, OVERRIDE_PREFIX + key, "");
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** 覆盖板：写值；传 null 或空串表示清除覆盖。 */
    public void setOverride(String key, String value) {
        Prefs.put(ctx, OVERRIDE_PREFIX + key, value == null ? "" : value.trim());
    }

    /** 插件日志（给设置页显示，方便看出插件有没有被加载起来）。 */
    public String logText() {
        return logBuf.toString();
    }

    private void log(String message) {
        logBuf.append(message).append('\n');
    }

    /** 交给插件的宿主实现。 */
    private final class HostImpl implements PluginHost {

        private final String pluginId;

        HostImpl(String pluginId) {
            this.pluginId = pluginId;
        }

        @Override
        public void log(String message) {
            PluginManager.this.log("[" + pluginId + "] " + message);
        }

        @Override
        public void set(String key, String value) {
            setOverride(key, value);
        }

        @Override
        public String get(String key) {
            return override(key);
        }

        @Override
        public void declareSetting(PluginSetting setting) {
            // 后声明的覆盖先声明的：两个插件抢同一个键时，最后一个说了算，
            // 但至少不会两个控件打架（只留一个）。
            settings.put(setting.getKey(), setting);
        }

        @Override
        public String detectedLanHost() {
            return detectLanHost();
        }

        @Override
        public int detectedLanPort() {
            return detectLanPort();
        }
    }

    // ---------------------------------------------------------------- 环境信息
    //
    // 这两个方法刻意放在这里（而不是让插件自己判断），因为「App 认为的地址是什么」
    // 只有 App 自己知道；插件要做的是在被给到的真实值基础上做覆盖。

    /** 由 App 注入：返回当前自动探测到的局域网地址。 */
    public interface LanProbe {
        String host();

        int port();
    }

    private LanProbe probe;

    public void setLanProbe(LanProbe p) {
        this.probe = p;
    }

    private String detectLanHost() {
        return probe == null ? "" : probe.host();
    }

    private int detectLanPort() {
        return probe == null ? 0 : probe.port();
    }
}
