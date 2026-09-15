package top.elysianherd.magicaland.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;

import java.io.File;
import java.io.Reader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ModelManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File BASE_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "magicaland");
    private static final File MODELS_DIR = new File(BASE_DIR, "ponies");
    private static final long SAVE_DEBOUNCE_NANOS = 200_000_000L;
    private static final Set<String> RESERVED_MODEL_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
            "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private static ModelConfig activeModel;
    private static List<String> availableModels = new ArrayList<>();
    private static boolean savePending;
    private static long saveRequestedAt;
    private static boolean saveTickRegistered;
    private static EditingSession editing;

    public static void init() {
        registerSaveTick();
        if (!MODELS_DIR.exists()) {
            MODELS_DIR.mkdirs();
        }
        refreshModelList();
        
        if (availableModels.isEmpty()) {
            createModel("anon");
        }
        
        String lastActive = Config.getInstance().activeModelName;
        if (lastActive != null && !lastActive.isEmpty() && availableModels.contains(lastActive)
                && loadModel(lastActive)) {
            return;
        }

        for (String modelName : new ArrayList<>(availableModels)) {
            if (loadModel(modelName)) {
                return;
            }
        }

        activeModel = null;
        Config.getInstance().activeModelName = "";
        Config.save();
    }

    public static List<String> getAvailableModels() {
        return List.copyOf(availableModels);
    }

    public static ModelConfig getActiveModel() {
        return activeModel;
    }

    public static ModelConfig getModelPreview(String name) {
        if (editing != null) {
            ModelConfig model = editing.drafts.get(name);
            return model == null ? null : model.copyForDisplay();
        }
        if (activeModel != null && Objects.equals(activeModel.name, name)) return activeModel.copyForDisplay();
        return readModel(name);
    }

    public static String exportActiveModel() {
        return activeModel == null ? "" : GSON.toJson(activeModel);
    }

    public static boolean importActiveModel(String json) {
        if (editing == null || activeModel == null || json == null || json.length() > 1_000_000) return false;
        try {
            ModelConfig imported = GSON.fromJson(json, ModelConfig.class);
            if (imported == null) return false;
            ModelConfig.sanitize(imported);
            imported.name = activeModel.name;
            setActiveModel(imported);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean importModel(String name, String json) {
        if (editing != null || name == null || json == null || json.length() > 1_000_000) return false;
        String safeName = name.trim();
        if (!isValidModelName(safeName)) return false;
        File file = resolveModelFile(safeName);
        if (file == null || file.exists()) return false;
        try {
            ModelConfig imported = GSON.fromJson(json, ModelConfig.class);
            if (imported == null) return false;
            ModelConfig.sanitize(imported);
            imported.name = safeName;
            writeAtomically(file, writer -> GSON.toJson(imported, writer));
            refreshModelList();
            setActiveModel(imported);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    public static ModelConfig getAppliedModel() {
        return editing == null ? activeModel : editing.applied;
    }

    public static boolean isEditing() { return editing != null; }

    public static Object editingSessionIdentity() { return editing; }

    public static boolean isPresetDirty(String name) {
        if (editing == null || name == null) return false;
        ModelConfig draft = editing.drafts.get(name);
        return draft != null && (!editing.originalFiles.containsKey(name)
                || !Objects.equals(editing.baseline.get(name), GSON.toJson(draft)));
    }

    public static boolean beginEditing() {
        if (editing != null) return true;
        EditingSession session = new EditingSession(copy(activeModel), List.copyOf(availableModels));
        try {
            File[] files = MODELS_DIR.exists() ? MODELS_DIR.listFiles((dir, name) -> name.endsWith(".json")) : new File[0];
            if (files == null) throw new IOException("Preset directory is not readable");
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 5);
                if (!isValidModelName(name)) continue;
                File safe = resolveModelFile(name);
                if (safe == null) throw new IOException("Unsafe preset path: " + name);
                byte[] bytes = Files.readAllBytes(safe.toPath());
                session.originalFiles.put(name, bytes);
                try {
                    ModelConfig model = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), ModelConfig.class);
                    if (model == null) continue;
                    ModelConfig.sanitize(model);
                    model.name = name;
                    session.drafts.put(name, model);
                    session.stored.put(name, GSON.toJson(model));
                } catch (RuntimeException ignored) {
                    // 损坏文件只保留原字节和占用名，不加入可编辑列表。
                }
            }
            if (activeModel != null && isValidModelName(activeModel.name)) {
                if (session.originalFiles.containsKey(activeModel.name) && !session.drafts.containsKey(activeModel.name))
                    throw new IOException("Applied preset was changed into an unreadable file");
                session.drafts.put(activeModel.name, copy(activeModel));
            }
            session.drafts.forEach((name, model) -> session.baseline.put(name, GSON.toJson(model)));
            session.baselineActive = activeModel == null ? null : activeModel.name;
            if (session.drafts.isEmpty()) {
                ModelConfig initial = new ModelConfig();
                initial.name = unusedName(session, "anon", "");
                session.drafts.put(initial.name, initial);
            }
            editing = session;
            activeModel = session.baselineActive == null ? session.drafts.values().iterator().next()
                    : session.drafts.get(session.baselineActive);
            savePending = false;
            refreshModelList();
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isDirty() {
        if (editing == null) return false;
        if (!Objects.equals(editing.baselineActive, activeModel == null ? null : activeModel.name)
                || !editing.baseline.keySet().equals(editing.drafts.keySet())) return true;
        for (var entry : editing.drafts.entrySet()) {
            if (!editing.originalFiles.containsKey(entry.getKey())
                    || !Objects.equals(editing.baseline.get(entry.getKey()), GSON.toJson(entry.getValue()))) return true;
        }
        return false;
    }

    public static void discardEditing() {
        if (editing == null) return;
        activeModel = copy(editing.applied);
        availableModels = new ArrayList<>(editing.availableBefore);
        editing = null;
        savePending = false;
    }

    public static boolean commitEditing() {
        if (editing == null || activeModel == null) return false;
        EditingSession session = editing;
        List<FileChange> changes = new ArrayList<>();
        Config currentConfig = Config.getInstance();
        try {
            if (session.drafts.get(activeModel.name) != activeModel) return false;
            File appliedTarget = resolveModelFile(activeModel.name);
            if (appliedTarget == null) return false;
            verifyUnchanged(new FileChange(appliedTarget.toPath(), session.originalFiles.get(activeModel.name), null));
            for (var entry : session.drafts.entrySet()) {
                String name = entry.getKey();
                ModelConfig model = entry.getValue();
                if (!name.equals(model.name) || !isValidModelName(name)) return false;
                if (session.originalFiles.containsKey(name) && !session.baseline.containsKey(name)) return false;
                ModelConfig.sanitize(model);
                byte[] after = GSON.toJson(model).getBytes(StandardCharsets.UTF_8);
                if (session.originalFiles.containsKey(name) && session.stored.containsKey(name)
                        && session.stored.get(name).equals(new String(after, StandardCharsets.UTF_8))) continue;
                File target = resolveModelFile(name);
                if (target == null) return false;
                changes.add(new FileChange(target.toPath(), session.originalFiles.get(name), after));
            }
            for (String name : session.baseline.keySet()) if (!session.drafts.containsKey(name)) {
                byte[] before = session.originalFiles.get(name);
                if (before != null) changes.add(new FileChange(resolveModelFile(name).toPath(), before, null));
            }
            Path configPath = BASE_DIR.toPath().resolve("config.json");
            if (!configPath.toFile().getCanonicalFile().getParentFile().equals(BASE_DIR.getCanonicalFile()))
                throw new IOException("Unsafe configuration path");
            byte[] configBefore = readOptional(configPath);
            com.google.gson.JsonObject updated = configBefore == null ? GSON.toJsonTree(currentConfig).getAsJsonObject()
                    : com.google.gson.JsonParser.parseString(new String(configBefore, StandardCharsets.UTF_8)).getAsJsonObject();
            if (configBefore == null || !updated.has("activeModelName")
                    || !GSON.toJsonTree(activeModel.name).equals(updated.get("activeModelName"))) {
                updated.addProperty("activeModelName", activeModel.name);
                changes.add(new FileChange(configPath, configBefore,
                        GSON.toJson(updated).getBytes(StandardCharsets.UTF_8)));
            }
            ModelConfig committedModel = copy(activeModel);
            applyTransaction(changes);
            activeModel = committedModel;
            currentConfig.activeModelName = activeModel.name;
            editing = null;
            savePending = false;
            availableModels = new ArrayList<>(session.originalFiles.keySet());
            availableModels.removeIf(name -> session.baseline.containsKey(name) && !session.drafts.containsKey(name));
            for (String name : session.drafts.keySet()) if (!availableModels.contains(name)) availableModels.add(name);
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void setActiveModel(ModelConfig model) {
        if (editing != null) {
            if (model != null && isValidModelName(model.name)) {
                if (editing.originalFiles.containsKey(model.name) && !editing.baseline.containsKey(model.name)) return;
                ModelConfig draft = copy(model);
                editing.drafts.put(draft.name, draft);
                activeModel = draft;
                refreshModelList();
            }
            return;
        }
        if (activeModel != model) {
            flushPendingSaveImmediately();
        }
        savePending = false;
        activeModel = model;
        if (model != null) {
            Config.getInstance().activeModelName = model.name;
            Config.save();
        }
        syncToServer();
    }

    public static void refreshModelList() {
        if (editing != null) {
            availableModels = new ArrayList<>(editing.drafts.keySet());
            return;
        }
        availableModels.clear();
        if (MODELS_DIR.exists() && MODELS_DIR.isDirectory()) {
            File[] files = MODELS_DIR.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    name = name.substring(0, name.length() - 5);
                    if (isValidModelName(name)) {
                        availableModels.add(name);
                    }
                }
            }
        }
        if (availableModels.isEmpty()) {
            createModel("anon");
        }
    }

    public static boolean createModel(String name) {
        if (editing != null) {
            name = name == null ? "" : name.trim();
            if (!isValidModelName(name) || occupied(editing, name)) return false;
            ModelConfig model = new ModelConfig();
            model.name = name;
            editing.drafts.put(name, model);
            activeModel = model;
            refreshModelList();
            return true;
        }
        if (savePending && !saveActiveModelChecked()) return false;
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        name = name.trim();
        if (!isValidModelName(name)) {
            return false;
        }
        File file = resolveModelFile(name);
        if (file == null) {
            return false;
        }
        if (file.exists()) {
            return false;
        }

        ModelConfig newModel = new ModelConfig();
        newModel.name = name;
        
        newModel.frontManeStyle = PonyStyleRegistry.DEFAULT_ID;
        newModel.backManeStyle = PonyStyleRegistry.DEFAULT_ID;
        newModel.eyeStyle = PonyStyleRegistry.DEFAULT_ID;
        newModel.hornColor = "#FFFFFFFF";
        newModel.bodyColor = "#FFFFFFFF";
        newModel.neckColor = "#FFFFFFFF";
        newModel.headColor = "#FFFFFFFF";
        newModel.leftEarColor = "#FFFFFFFF";
        newModel.rightEarColor = "#FFFFFFFF";
        newModel.limbColor = "#FFFFFFFF";
        newModel.leftFrontLimbColor = "#FFFFFFFF";
        newModel.rightFrontLimbColor = "#FFFFFFFF";
        newModel.leftHindLimbColor = "#FFFFFFFF";
        newModel.rightHindLimbColor = "#FFFFFFFF";
        newModel.noseColor = "#FFFFFFFF";
        newModel.showHorn = true;
        newModel.hornColorLocked = true;
        newModel.bodyColorLocked = true;
        newModel.noseColorLocked = true;
        newModel.neckColorLocked = true;
        newModel.headColorLocked = true;
        newModel.leftEarColorLocked = true;
        newModel.rightEarColorLocked = true;
        newModel.leftFrontLimbColorLocked = true;
        newModel.rightFrontLimbColorLocked = true;
        newModel.leftHindLimbColorLocked = true;
        newModel.rightHindLimbColorLocked = true;

        try {
            writeAtomically(file, writer -> GSON.toJson(newModel, writer));
            refreshModelList();
            setActiveModel(newModel);
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loadModel(String name) {
        if (editing != null) {
            ModelConfig model = editing.drafts.get(name);
            if (model == null) return false;
            activeModel = model;
            return true;
        }
        if (savePending && !saveActiveModelChecked()) return false;
        ModelConfig model = readModel(name);
        if (model == null) return false;
        setActiveModel(model);
        return true;
    }

    private static ModelConfig readModel(String name) {
        File file = resolveModelFile(name);
        if (file == null || !file.exists()) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            ModelConfig model = GSON.fromJson(reader, ModelConfig.class);
            if (model != null) {
                ModelConfig.sanitize(model);
                model.name = name;
                return model;
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void saveActiveModel() {
        saveActiveModelChecked();
    }

    private static boolean saveActiveModelChecked() {
        if (editing != null) {
            if (activeModel != null) ModelConfig.sanitize(activeModel);
            return true;
        }
        if (activeModel == null) {
            return true;
        }
        ModelConfig.sanitize(activeModel);
        File file = resolveModelFile(activeModel.name);
        if (file == null) {
            savePending = true;
            return false;
        }
        try {
            writeAtomically(file, writer -> GSON.toJson(activeModel, writer));
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            savePending = true;
            return false;
        }
        savePending = false;
        syncToServer();
        return true;
    }

    public static boolean renameActiveModel(String requestedName) {
        if (activeModel == null || requestedName == null) return false;
        String name = requestedName.trim();
        if (!isValidModelName(name)) return false;
        if (name.equals(activeModel.name)) return true;
        if (editing != null) {
            if (occupied(editing, name)) return false;
            editing.drafts.remove(activeModel.name);
            activeModel.name = name;
            editing.drafts.put(name, activeModel);
            refreshModelList();
            return true;
        }
        File source = resolveModelFile(activeModel.name);
        File target = resolveModelFile(name);
        if (source == null || target == null || target.exists()) return false;
        if (!saveActiveModelChecked()) return false;
        try {
            // 不覆盖同名预设；加载时始终以文件名为准。
            Files.move(source.toPath(), target.toPath());
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        activeModel.name = name;
        Config.getInstance().activeModelName = name;
        Config.save();
        refreshModelList();
        saveActiveModel();
        return true;
    }

    public static void requestSaveActiveModel() {
        if (editing != null) return;
        if (activeModel == null) {
            return;
        }
        savePending = true;
        saveRequestedAt = System.nanoTime();
    }

    public static boolean duplicateActiveModel() { return duplicateActiveModel(" 副本"); }

    public static boolean duplicateActiveModel(String suffix) {
        if (activeModel == null || !isValidModelName(activeModel.name)) return false;
        if (editing == null && savePending && !saveActiveModelChecked()) return false;
        if (suffix == null || suffix.isBlank() || suffix.length() > 16 || !isValidModelName("a" + suffix.trim())) suffix = " 副本";
        String name = unusedName(editing, activeModel.name, suffix);
        ModelConfig duplicate = copy(activeModel);
        duplicate.name = name;
        if (editing != null) {
            editing.drafts.put(name, duplicate);
            activeModel = duplicate;
            refreshModelList();
            return true;
        }
        File target = resolveModelFile(name);
        if (target == null || target.exists()) return false;
        try {
            writeAtomically(target, writer -> GSON.toJson(duplicate, writer));
            refreshModelList();
            setActiveModel(duplicate);
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void syncToServer() {
        if (editing != null) return;
        try {
            ClientNetworkHandler.sendModelToServer();
        } catch (Exception ignored) {
        }
    }

    public static boolean deleteModel(String name) {
        if (editing != null) {
            if (editing.drafts.size() <= 1 || !editing.drafts.containsKey(name)) return false;
            ModelConfig removed = editing.drafts.remove(name);
            if (removed == activeModel) activeModel = editing.drafts.values().iterator().next();
            refreshModelList();
            return true;
        }
        if (savePending && !saveActiveModelChecked()) return false;
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (availableModels.size() <= 1) {
            return false;
        }
        File file = resolveModelFile(name);
        if (file == null) {
            return false;
        }
        boolean deletingActive = activeModel != null && activeModel.name.equals(name);
        ModelConfig replacement = null;
        if (deletingActive) {
            for (String candidate : List.copyOf(availableModels)) {
                if (candidate.equals(name)) continue;
                File candidateFile = resolveModelFile(candidate);
                if (candidateFile == null) continue;
                try (Reader reader = Files.newBufferedReader(candidateFile.toPath(), StandardCharsets.UTF_8)) {
                    ModelConfig model = GSON.fromJson(reader, ModelConfig.class);
                    if (model == null) continue;
                    ModelConfig.sanitize(model);
                    model.name = candidate;
                    replacement = model;
                    break;
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                }
            }
            if (replacement == null) return false;
        }
        if (file.exists() && file.isFile()) {
            if (file.delete()) {
                refreshModelList();
                if (deletingActive) setActiveModel(replacement);
                return true;
            }
        }
        return false;
    }

    private static File resolveModelFile(String name) {
        if (!isValidModelName(name)) {
            return null;
        }
        Path base = MODELS_DIR.toPath().toAbsolutePath().normalize();
        Path target = base.resolve(name + ".json").normalize();
        if (!base.equals(target.getParent())) {
            return null;
        }
        try {
            File canonicalBase = MODELS_DIR.getCanonicalFile();
            File canonicalTarget = target.toFile().getCanonicalFile();
            return canonicalBase.equals(canonicalTarget.getParentFile()) ? target.toFile() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static ModelConfig copy(ModelConfig model) {
        return model == null ? null : GSON.fromJson(GSON.toJson(model), ModelConfig.class);
    }

    private static boolean occupied(EditingSession session, String name) {
        return session.drafts.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))
                || session.originalFiles.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name)
                        && (!session.baseline.containsKey(existing) || !existing.equals(name)));
    }

    private static String unusedName(EditingSession session, String base, String suffix) {
        for (int number = 1; number < 100000; number++) {
            String ending = suffix + (number == 1 ? "" : " " + number);
            int length = Math.min(base.length(), Math.max(1, 32 - ending.length()));
            if (length < base.length() && Character.isHighSurrogate(base.charAt(length - 1))) length--;
            String name = base.substring(0, length) + ending;
            File file = resolveModelFile(name);
            if (isValidModelName(name) && (session == null ? file != null && !file.exists() : !occupied(session, name))) return name;
        }
        throw new IllegalStateException("No available preset name");
    }

    private static byte[] readOptional(Path path) throws IOException {
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    private static void verifyUnchanged(FileChange change) throws IOException {
        if (!Arrays.equals(change.before, readOptional(change.target)))
            throw new IOException("Preset or configuration changed outside the editor: " + change.target.getFileName());
    }

    private static void replaceFile(Path source, Path target, boolean replace) throws IOException {
        if (!replace) {
            Files.move(source, target);
            return;
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void applyTransaction(List<FileChange> changes) throws IOException {
        if (changes.isEmpty()) return;
        for (FileChange change : changes) verifyUnchanged(change);
        Files.createDirectories(MODELS_DIR.toPath());
        Path temporary = Files.createTempDirectory(BASE_DIR.toPath(), ".appearance-edit-");
        List<Path> temporaryFiles = new ArrayList<>();
        List<FileChange> applied = new ArrayList<>();
        boolean keepRecovery = false;
        try {
            Path journal = temporary.resolve("recovery.json");
            temporaryFiles.add(journal);
            List<Map<String, String>> recovery = new ArrayList<>();
            for (int i = 0; i < changes.size(); i++) recovery.add(Map.of("target", changes.get(i).target.toString(),
                    "backup", changes.get(i).before == null ? "" : i + ".before"));
            Files.writeString(journal, GSON.toJson(recovery), StandardCharsets.UTF_8);
            for (int i = 0; i < changes.size(); i++) {
                FileChange change = changes.get(i);
                if (change.before != null) {
                    change.backup = temporary.resolve(i + ".before");
                    temporaryFiles.add(change.backup);
                    Files.write(change.backup, change.before);
                }
                if (change.after != null) {
                    change.prepared = temporary.resolve(i + ".after");
                    temporaryFiles.add(change.prepared);
                    Files.write(change.prepared, change.after);
                }
            }
            for (FileChange change : changes) verifyUnchanged(change);
            for (FileChange change : changes) {
                verifyUnchanged(change);
                applied.add(change);
                if (change.after == null) Files.delete(change.target);
                else replaceFile(change.prepared, change.target, change.before != null);
            }
        } catch (IOException | RuntimeException failure) {
            for (int i = applied.size() - 1; i >= 0; i--) {
                FileChange change = applied.get(i);
                try {
                    byte[] current = readOptional(change.target);
                    if (Arrays.equals(current, change.before)) continue;
                    if (!Arrays.equals(current, change.after)) throw new IOException("Concurrent change during rollback: " + change.target);
                    if (change.before == null) Files.deleteIfExists(change.target);
                    else {
                        Path restore = temporary.resolve(i + ".restore");
                        temporaryFiles.add(restore);
                        Files.copy(change.backup, restore, StandardCopyOption.REPLACE_EXISTING);
                        replaceFile(restore, change.target, true);
                    }
                } catch (IOException | RuntimeException rollbackFailure) {
                    keepRecovery = true;
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (keepRecovery) System.err.println("Appearance recovery files retained at: " + temporary);
            throw failure;
        } finally {
            if (!keepRecovery) {
                for (Path file : temporaryFiles) {
                    try { Files.deleteIfExists(file); } catch (IOException cleanup) { cleanup.printStackTrace(); }
                }
                try { Files.deleteIfExists(temporary); } catch (IOException cleanup) { cleanup.printStackTrace(); }
            }
        }
    }

    private static final class EditingSession {
        final ModelConfig applied;
        final List<String> availableBefore;
        final Map<String, byte[]> originalFiles = new LinkedHashMap<>();
        final Map<String, ModelConfig> drafts = new LinkedHashMap<>();
        final Map<String, String> baseline = new LinkedHashMap<>();
        final Map<String, String> stored = new LinkedHashMap<>();
        String baselineActive;
        EditingSession(ModelConfig applied, List<String> availableBefore) {
            this.applied = applied;
            this.availableBefore = availableBefore;
        }
    }

    private static final class FileChange {
        final Path target;
        final byte[] before;
        final byte[] after;
        Path backup;
        Path prepared;
        FileChange(Path target, byte[] before, byte[] after) {
            this.target = target;
            this.before = before;
            this.after = after;
        }
    }

    private static boolean isValidModelName(String name) {
        if (name == null || name.isEmpty() || name.length() > 32
                || !name.equals(name.trim()) || name.equals(".") || name.equals("..")
                || name.endsWith(".") || name.endsWith(" ")) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isISOControl(character) || "/\\:*?\"<>|".indexOf(character) >= 0) {
                return false;
            }
        }

        String deviceName = name;
        int extensionSeparator = name.indexOf('.');
        if (extensionSeparator >= 0) {
            deviceName = name.substring(0, extensionSeparator);
        }
        return !RESERVED_MODEL_NAMES.contains(deviceName.toUpperCase(Locale.ROOT));
    }

    private static void registerSaveTick() {
        if (saveTickRegistered) {
            return;
        }
        saveTickRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> flushPendingSave());
    }

    private static void flushPendingSave() {
        if (savePending && activeModel != null
                && System.nanoTime() - saveRequestedAt >= SAVE_DEBOUNCE_NANOS) {
            saveActiveModel();
        }
    }

    private static void flushPendingSaveImmediately() {
        if (savePending) {
            saveActiveModel();
        }
    }

    private static void writeAtomically(File file, WriterConsumer writerConsumer) throws IOException {
        Path target = file.toPath();
        Path temporary = target.resolveSibling(file.getName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writerConsumer.write(writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    private interface WriterConsumer {
        void write(Writer writer);
    }
}
