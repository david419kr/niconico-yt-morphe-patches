package io.github.david419kr.niconicoyt.patches.youtube.niconicocomments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21ih
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PlaybackRateFingerprintTest {
    @Test
    fun matchesOriginalLimits() = checkMatch(0.25f, 4.0f)

    @Test
    fun matchesMorpheCustomSpeedLimits() = checkMatch(0.0f, 8.0f)

    @Test
    fun rejectsUnrelatedLimits() = checkMatch(1.0f, 16.0f, expected = false)

    @Test
    fun requiresPlaybackRateLogAnchor() = checkMatch(0.0f, 8.0f, log = "Other rate: %f", expected = false)

    @Test
    fun reproducesOldFingerprintFailureAfterMorpheRewritesLimits() {
        val context = context(fixture(0.0f, 8.0f, "Playback rate: %f"))
        with(context) {
            assertNull(Fingerprint(
                filters = listOf(string("Playback rate: %f"), literal(0.25f), literal(4.0f)),
            ).matchOrNull())
        }
    }

    private fun checkMatch(min: Float, max: Float, log: String = "Playback rate: %f", expected: Boolean = true) {
        val fingerprint = PlaybackRateApplierFingerprint
        fingerprint.clearMatch()
        fingerprint.classFingerprint!!.clearMatch()
        try {
            with(context(fixture(min, max, log))) {
                // A parent miss throws in matchOrNull(), so check the parent first.
                val parent = fingerprint.classFingerprint!!.matchOrNull()
                if (expected) {
                    assertNotNull(parent)
                    assertEquals("applyRate", fingerprint.match().originalMethod.name)
                } else {
                    assertNull(parent)
                }
            }
        } finally {
            fingerprint.clearMatch()
            fingerprint.classFingerprint!!.clearMatch()
        }
    }

    private fun fixture(min: Float, max: Float, log: String): ClassDef {
        val type = "Ltest/Player;"
        val limits = listOf(
            ImmutableInstruction21ih(Opcode.CONST_HIGH16, 0, min.toRawBits()),
            ImmutableInstruction21ih(Opcode.CONST_HIGH16, 1, max.toRawBits()),
        )
        val flags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value
        val parent = ImmutableMethod(type, "logRate", listOf(ImmutableMethodParameter("Ljava/lang/Object;", null, null)),
            "V", flags, null, null, ImmutableMethodImplementation(4, listOf(
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(log)),
            ) + limits + ImmutableInstruction10x(Opcode.RETURN_VOID), null, null))
        val applier = ImmutableMethod(type, "applyRate", listOf(ImmutableMethodParameter("F", null, null)),
            "V", flags, null, null, ImmutableMethodImplementation(4, limits + listOf(
                ImmutableInstruction35c(Opcode.INVOKE_INTERFACE, 2, 2, 3, 0, 0, 0,
                    ImmutableMethodReference("Ltest/Output;", "setRate", listOf("F"), "V")),
                ImmutableInstruction10x(Opcode.RETURN_VOID),
            ), null, null))
        return ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null,
            emptyList(), listOf(parent, applier))
    }

    private fun context(classDef: ClassDef): BytecodePatchContext {
        // Patcher constructors and the class index are internal; inject a DEX fixture without an APK.
        val metadata = PackageMetadata::class.java.declaredConstructors.single { it.parameterCount == 4 }
            .apply { isAccessible = true }.newInstance("com.google.android.youtube", "fixture", "1", null)
        val context = BytecodePatchContext::class.java.declaredConstructors.single { it.parameterCount == 2 }
            .apply { isAccessible = true }.newInstance(PatcherConfig(File("unused.apk")), metadata) as BytecodePatchContext
        val indexType = Class.forName("app.morphe.patcher.util.PatchClasses")
        val index = indexType.getDeclaredConstructor(Set::class.java).apply { isAccessible = true }
            .newInstance(setOf(classDef))
        BytecodePatchContext::class.java.getDeclaredField("patchClasses").apply { isAccessible = true }
            .set(context, index)
        return context
    }
}
