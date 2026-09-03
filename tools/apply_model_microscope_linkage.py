#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")
original = text


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"PATCH FAIL [{label}]: source anchor not found")
    text = text.replace(old, new, 1)


def regex_once(pattern: str, replacement: str, label: str) -> None:
    global text
    if re.search(pattern, text, flags=re.S) is None:
        raise SystemExit(f"PATCH FAIL [{label}]: regex anchor not found")
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"PATCH FAIL [{label}]: replaced {count} blocks")


if "private final MicroscopeSelectionState microscopeSelection" not in text:
    replace_once(
        "    private final RecognitionTrend recognitionTrend = new RecognitionTrend(30);\n",
        "    private final RecognitionTrend recognitionTrend = new RecognitionTrend(30);\n"
        "    private final MicroscopeSelectionState microscopeSelection = new MicroscopeSelectionState();\n",
        "selection state field",
    )

text = text.replace("    private ModelVariant lastDisplayVariant = ModelVariant.S;\n", "")

old_model_listener = '''        spinnerModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            modelMode = modes[position];
            lastDisplayVariant = displayVariantForMode();
            clearFusion();
            recognitionTrend.clear();
            lastDisplayTop = new ArrayList<>();
            if (topKChart != null) topKChart.setResults(lastDisplayTop, threshold);
            if (trendChart != null) trendChart.setSeries(new float[0], new float[0], threshold);
            clearProbeProjection();
            refreshCalibration(lastDisplayVariant);
        }));'''
new_model_listener = '''        spinnerModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                applyRecognitionModelSelection(modes[position])));'''
replace_once(old_model_listener, new_model_listener, "model spinner listener")

old_inspect_listener = '''        spinnerEnrollmentInspectModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            inspectVariant = variants[position];
            renderEnrollmentMicroscope(inspectVariant);
        }));'''
new_inspect_listener = '''        spinnerEnrollmentInspectModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            inspectVariant = variants[position];
            renderEnrollmentMicroscope(inspectVariant);
            if (modelMode == ModelMode.COMPARE) {
                MicroscopeSelectionState.Snapshot focused = microscopeSelection.selectFocus(inspectVariant);
                clearFusion();
                recognitionTrend.clear();
                clearProbeProjection();
                refreshCalibration(focused.focus);
                renderRecognitionModelPendingState(focused);
            }
        }));'''
replace_once(old_inspect_listener, new_inspect_listener, "enrollment inspect listener")

methods = '''
    private void applyRecognitionModelSelection(ModelMode selectedMode) {
        modelMode = selectedMode == null ? ModelMode.S : selectedMode;
        MicroscopeSelectionState.Snapshot selection = microscopeSelection.selectMode(modelMode);
        ModelVariant focus = selection.focus;

        if (modelMode != ModelMode.COMPARE) {
            inspectVariant = focus;
            int index = variantIndex(focus);
            if (spinnerEnrollmentInspectModel != null && spinnerEnrollmentInspectModel.getSelectedItemPosition() != index) {
                spinnerEnrollmentInspectModel.setSelection(index);
            }
            renderEnrollmentMicroscope(focus);
        }

        clearFusion();
        recognitionTrend.clear();
        lastDisplayTop = new ArrayList<>();
        clearProbeProjection();
        refreshCalibration(focus);
        renderRecognitionModelPendingState(selection);
        updateActionState();
    }

    private void renderRecognitionModelPendingState(MicroscopeSelectionState.Snapshot selection) {
        if (selection == null) return;
        ModelVariant focus = selection.focus;
        if (topKChart != null) topKChart.setResults(new ArrayList<>(), threshold);
        if (trendChart != null) trendChart.setSeries(new float[0], new float[0], threshold);

        String modeText = selection.mode == ModelMode.COMPARE
                ? "COMPARE · 显微镜焦点 " + focus.storageKey
                : focus.storageKey;
        if (txtResult != null) txtResult.setText("模型已切换 · " + modeText + " · 等待该模型新帧");
        if (txtRecognitionQuality != null) {
            txtRecognitionQuality.setText("Probe 质量（模型无关）· 等待 " + focus.storageKey + " 新帧");
        }
        if (txtPipeline != null) {
            txtPipeline.setText("模型切换 → " + modeText + " → 清空旧融合/趋势 → 等待新帧；旧模型在途帧不会再回写显微镜");
        }
        if (txtRecognitionFormula != null) {
            txtRecognitionFormula.setText("公式链 · " + focus.storageKey + "\\n等待该模型新帧后刷新 Top-K / margin / gate / 512D cosine");
        }
        PerformanceWindow window = performance.get(focus);
        if (txtPerf != null && window != null) {
            txtPerf.setText(String.format(Locale.US,
                    "显微镜焦点 %s · 本帧等待中\\n该模型30帧均值 detect %.1f / align %.1f / infer %.1f / match %.1f / total %.1f ms",
                    focus.storageKey, window.avgDetectMs(), window.avgAlignMs(), window.avgInferMs(),
                    window.avgMatchMs(), window.avgTotalMs()));
        }
        if (txtProbeEmbeddingInfo != null) {
            txtProbeEmbeddingInfo.setText("显微镜焦点 " + focus.storageKey + " · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。");
        }
    }

    private static int variantIndex(ModelVariant variant) {
        ModelVariant[] variants = ModelVariant.values();
        for (int i = 0; i < variants.length; i++) if (variants[i] == variant) return i;
        return 0;
    }

'''
if "private void applyRecognitionModelSelection(" not in text:
    replace_once(
        "    private void installR31Panels() {\n",
        methods + "    private void installR31Panels() {\n",
        "selection refresh methods",
    )

old_recognition_start = '''        StringBuilder results = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        ModelVariant displayVariant = displayVariantForMode();
        List<FaceStore.Match> displayTop = new ArrayList<>();'''
new_recognition_start = '''        MicroscopeSelectionState.Snapshot frameSelection = microscopeSelection.snapshot();
        StringBuilder results = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        ModelVariant displayVariant = frameSelection.focus;
        List<FaceStore.Match> displayTop = new ArrayList<>();'''
replace_once(old_recognition_start, new_recognition_start, "capture frame selection")

text = text.replace(
    "        for (ModelVariant variant : modelMode.variants()) {\n",
    "        for (ModelVariant variant : frameSelection.mode.variants()) {\n",
    1,
)

old_ui_post = '''        int finalFusedFrames = displayFusedFrames;
        runOnUiThread(() -> {
            lastDisplayTop = finalTop;
            lastDisplayVariant = displayVariant;'''
new_ui_post = '''        int finalFusedFrames = displayFusedFrames;
        runOnUiThread(() -> {
            if (!microscopeSelection.isCurrent(frameSelection)) return;
            lastDisplayTop = finalTop;'''
replace_once(old_ui_post, new_ui_post, "stale frame guard")

old_display_method = '''    private ModelVariant displayVariantForMode() {
        for (ModelVariant variant : modelMode.variants()) if (variant == ModelVariant.S) return variant;
        ModelVariant[] variants = modelMode.variants();
        return variants.length == 0 ? ModelVariant.S : variants[0];
    }'''
new_display_method = '''    private ModelVariant displayVariantForMode() {
        return microscopeSelection.snapshot().focus;
    }'''
replace_once(old_display_method, new_display_method, "display focus method")

if text == original:
    print("R3.2 linkage patch already applied")
else:
    MAIN.write_text(text, encoding="utf-8")
    print("R3.2 linkage patch applied")
