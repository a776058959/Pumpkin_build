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
 *     plugin.json     {"id":"console-font","entry":"com.pumpkin.plugin.consolefont.ConsoleFontPlugin"}
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
            if (!isEnabled(sub.getName())) {
                // 停用的插件不进 dex、不声明设置项，但它写的覆盖值仍然生效 ——
                // 「停用」是停代码，不是把用户已经调好的值还原。
                log("[" + sub.getName() + "] 已停用，跳过");
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
            // 记下来，卸载这个插件时才能把它写过的覆盖键一并清掉
            if (value != null && !value.trim().isEmpty()) {
                rememberKey(pluginId, key);
            }
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
            // 也记进「这个插件拥有哪些键」。
            //
            // 这一步是必须的，别删：设置项的值是**用户**在界面上写的
            //（走 MainActivity.onPluginSettingChangedFromUi → setOverride），
            // 不经过 HostImpl.set()，所以只在 set() 里记的话，
            // 卸载插件时这些键就没人认领 —— 实测卸载后覆盖值会留在 Prefs 里，
            // 插件再也装不回来也清不掉的那个值会一直生效。
            //
            // 记在 Prefs 里而不是内存里，是因为卸载时插件可能处于**停用**状态：
            // 停用的插件不会被加载、也就不会调到这里，只能靠上次记下的清单。
            rememberKey(pluginId, setting.getKey());
        }
    }

    // ================================================================ 插件商店
    //
    // 插件索引发布在一个固定 tag（STORE_TAG）的 Release 里：
    //     plugins.json      {"plugins":[{id,name,version,description,entry,dex}, ...]}
    //     plugin-<id>.dex   各插件的代码
    //
    // 用固定 tag 而不是「最新 Release」：后者通常是服务端构建，没有插件附件。
    // 下载走 UpdateClient.downloadTo（与 App 更新同一套多源回退），
    // 所以**不需要 root、也不需要任何存储权限**：App 只是往自己的私有目录写文件。

    /** 插件索引所在的 Release tag。 */
    public static final String STORE_TAG = "plugins";

    private static final String STORE_INDEX = "plugins.json";

    private com.pumpkin.server.UpdateClient updates;

    public void setUpdates(com.pumpkin.server.UpdateClient u) {
        this.updates = u;
    }

    /** 商店里的一条插件信息。 */
    public static final class StoreEntry {
        public final String id;
        public final String name;
        public final String version;
        public final String description;
        public final String entry;
        public final String dex;

        StoreEntry(String id, String name, String version, String description,
                String entry, String dex) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.entry = entry;
            this.dex = dex;
        }
    }

    /** 拉取插件索引。失败直接抛，由调用方把原因显示出来。 */
    public List<StoreEntry> fetchStore() throws Exception {
        if (updates == null) {
            throw new IllegalStateException("没有绑定 UpdateClient");
        }
        String indexUrl = updates.releaseAssetUrl(STORE_TAG, STORE_INDEX);
        if (indexUrl == null) {
            throw new java.io.IOException("发布里没有 " + STORE_INDEX + "（tag=" + STORE_TAG + "）");
        }
        org.json.JSONObject root = new org.json.JSONObject(updates.fetchText(indexUrl));
        org.json.JSONArray arr = root.optJSONArray("plugins");
        List<StoreEntry> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = o.optString("id", "").trim();
            String entry = o.optString("entry", "").trim();
            String dex = o.optString("dex", "").trim();
            if (id.isEmpty() || entry.isEmpty() || dex.isEmpty()) {
                continue;
            }
            out.add(new StoreEntry(id, o.optString("name", id), o.optString("version", ""),
                    o.optString("description", ""), entry, dex));
        }
        return out;
    }

    /** 已安装插件的版本（安装时记在 Prefs 里）；没装过返回空串。 */
    public String installedVersion(String id) {
        String v = Prefs.get(ctx, "plugin_ver_" + id, "");
        return v == null ? "" : v;
    }

    /** 插件是否启用。默认启用。 */
    public boolean isEnabled(String id) {
        return Prefs.getBool(ctx, "plugin_enabled_" + id, true);
    }

    public void setEnabled(String id, boolean enabled) {
        Prefs.putBool(ctx, "plugin_enabled_" + id, enabled);
    }

    /**
     * 下载并安装（或更新）一个插件。
     *
     * 全程只写 App 自己的私有目录，不需要 root，也不需要存储权限。
     */
    public void install(StoreEntry e, com.pumpkin.server.UpdateClient.Progress progress)
            throws Exception {
        if (updates == null) {
            throw new IllegalStateException("没有绑定 UpdateClient");
        }
        String url = updates.releaseAssetUrl(STORE_TAG, e.dex);
        if (url == null) {
            throw new java.io.IOException("发布里没有 " + e.dex);
        }
        File dir = new File(pluginDir(), e.id);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            // 这条错误实际出现过，值得写清楚：
            // 早先验证插件时是用 root/adb 把文件推进 filesDir/plugins 的，
            // 于是 plugins 目录的属主变成 root:root 755 —— App 能读能加载，
            // 但再也建不了新的子目录，表现成「装插件失败」，看着像功能坏了。
            // 清掉那个目录（App 会自己重建，属主就是自己）即可。
            throw new java.io.IOException("建不了插件目录 " + dir.getAbsolutePath()
                    + "（插件目录不可写；如果以前用 root/adb 往里推过文件，"
                    + "把 files/plugins 整个删掉让 App 重建）");
        }
        updates.downloadTo(url, new File(dir, "plugin.dex"), progress);
        // plugin.json 由索引里的 entry 生成 —— 使用者不需要知道这个文件的存在
        java.io.FileWriter w = new java.io.FileWriter(new File(dir, "plugin.json"), false);
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("id", e.id);
            o.put("entry", e.entry);
            w.write(o.toString());
        } finally {
            try {
                w.close();
            } catch (Exception ignored) {
            }
        }
        Prefs.put(ctx, "plugin_ver_" + e.id, e.version);
        Prefs.putBool(ctx, "plugin_enabled_" + e.id, true);
        loadAll();
    }

    /** 卸载插件：删目录 + 清掉它写过的覆盖键。 */
    public void remove(String id) {
        deleteRecursively(new File(pluginDir(), id));
        String keys = Prefs.get(ctx, "plugin_keys_" + id, "");
        if (keys != null) {
            for (String k : keys.split(",")) {
                String key = k.trim();
                if (!key.isEmpty()) {
                    setOverride(key, null);
                }
            }
        }
        Prefs.put(ctx, "plugin_keys_" + id, "");
        Prefs.put(ctx, "plugin_ver_" + id, "");
        loadAll();
    }

    /** 记住这个插件写过哪些覆盖键，卸载时好清干净。 */
    private void rememberKey(String pluginId, String key) {
        String cur = Prefs.get(ctx, "plugin_keys_" + pluginId, "");
        if (cur == null) {
            cur = "";
        }
        for (String k : cur.split(",")) {
            if (k.trim().equals(key)) {
                return;
            }
        }
        Prefs.put(ctx, "plugin_keys_" + pluginId, cur.isEmpty() ? key : cur + "," + key);
    }

    /**
     * 已安装的插件 id（扫目录，不看是否加载成功）。
     *
     * 和 {@link #loaded()} 的区别：目录里存在但被停用、或者加载失败的插件，
     * 这里仍然列得出来 —— 否则用户没法在界面上把坏插件卸掉。
     */
    public List<String> installedIds() {
        List<String> out = new ArrayList<>();
        File[] subs = pluginDir().listFiles();
        if (subs == null) {
            return out;
        }
        for (File sub : subs) {
            if (sub.isDirectory() && new File(sub, "plugin.json").exists()) {
                out.add(sub.getName());
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        f.delete();
    }

}