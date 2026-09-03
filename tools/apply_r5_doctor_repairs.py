#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"R5 DOCTOR FAIL {label}: expected 1 anchor, got {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

# 1) Repair the accidental literal newlines introduced by the integration generator.
old = '''        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R5 学习版本。
硬门通过后筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。
本轮档位：" + enrollmentProfileAtStart);
'''
new = '''        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R5 学习版本。\\n" +
                "硬门通过后筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。\\n" +
                "本轮档位：" + enrollmentProfileAtStart);
'''
main = once(main, old, new, "enrollment text escaping")

# 2) If ML Kit temporarily gives no tracking id, keep one stable sentinel across contiguous frames.
# The previous synthetic id changed every frame and made 3/5-frame guard evidence impossible.
main = once(main,
    "        int trackingId = tracked != null ? tracked : -1 - (int)(++noTrackingSequence & 0x3fffffff);\n",
    "        int trackingId = tracked != null ? tracked : -1;\n",
    "tracking fallback")
main = main.replace("    private long noTrackingSequence = 0;\n", "")

# 3) A no-face gap invalidates temporal Guard evidence, but must not close an already opened history context.
anchor = '''        if (faces.isEmpty()) {
            runOnUiThread(() -> {
'''
replacement = '''        if (faces.isEmpty()) {
            if (currentPage == Page.ENROLLMENT && enrollmentRemaining.get() == 0 && existingIdentityContext.isEmpty()) {
                identityGuard.reset();
                guardGeneration = identityGuard.captureGeneration();
                guardTrackingId = Integer.MIN_VALUE;
                lastGuardProbeMs = 0L;
                lastGuardCandidates = new ArrayList<>();
            }
            runOnUiThread(() -> {
                if (currentPage == Page.ENROLLMENT && enrollmentRemaining.get() == 0 && existingIdentityContext.isEmpty()) {
                    renderIdentityGuardPanel();
                    updateActionState();
                }
'''
main = once(main, anchor, replacement, "no-face guard reset")

MAIN.write_text(main, encoding="utf-8")
print("R5 doctor repairs applied")
